package com.tunifyx.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tunifyx.app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    // ── Ganti dengan URL TunifyX lo di Vercel ─────────────────
    private static final String TUNIFYX_URL = "https://tunify-x.vercel.app";

    private ActivityMainBinding binding;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    // File chooser untuk input file di web (jika ada)
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_CODE = 1;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen — sembunyikan status bar & nav bar
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().setStatusBarColor(Color.parseColor("#121212"));
        getWindow().setNavigationBarColor(Color.parseColor("#121212"));

        // Keep screen on saat musik jalan
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        setupWebView();
        setupAudioFocus();

        // Load TunifyX
        if (isConnected()) {
            loadTunifyX();
        } else {
            showOffline();
        }

        // Retry button
        binding.btnRetry.setOnClickListener(v -> {
            if (isConnected()) {
                loadTunifyX();
            } else {
                Toast.makeText(this, "Masih tidak ada koneksi", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebView webView = binding.webView;
        WebSettings settings = webView.getSettings();

        // JavaScript wajib aktif
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);       // localStorage (untuk store.ts)
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); // autoplay audio
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Tampilan
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);

        // User agent — biar web tahu ini mobile Chrome
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 12; Mobile) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36 TunifyXApp/1.0"
        );

        // Cookie
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // WebViewClient — handle navigasi
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                binding.progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                binding.progressBar.setVisibility(View.GONE);
                binding.layoutOffline.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);

                // Inject CSS untuk sembunyikan scrollbar & fix height
                view.evaluateJavascript(
                    "(function(){" +
                    "var s=document.createElement('style');" +
                    "s.innerHTML='::-webkit-scrollbar{display:none!important}" +
                    "html,body{height:100dvh!important;overflow:hidden!important}';" +
                    "document.head.appendChild(s);" +
                    "})();",
                    null
                );
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
                binding.progressBar.setVisibility(View.GONE);
                showOffline();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                // Buka link eksternal di browser, bukan di WebView
                if (!url.contains("tunify") && !url.contains("vercel.app")
                        && !url.contains("youtube.com") && !url.contains("youtu.be")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });

        // WebChromeClient — handle permission & file
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Allow audio/video permission untuk YouTube IFrame
                request.grant(request.getResources());
            }

            @Override
            public boolean onShowFileChooser(WebView wv,
                ValueCallback<Uri[]> callback, FileChooserParams params) {
                filePathCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_CODE);
                } catch (Exception e) {
                    filePathCallback = null;
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                binding.progressBar.setProgress(progress);
                if (progress == 100) {
                    binding.progressBar.setVisibility(View.GONE);
                }
            }
        });

        // Background color sama dengan TunifyX
        webView.setBackgroundColor(Color.parseColor("#121212"));
    }

    private void setupAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChange -> {
                    // Handle audio focus changes (pause saat ada telepon dll)
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        binding.webView.evaluateJavascript(
                            "if(window._ytPlayer) window._ytPlayer.pauseVideo();", null
                        );
                    }
                })
                .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        }
    }

    private void loadTunifyX() {
        binding.layoutOffline.setVisibility(View.GONE);
        binding.webView.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.webView.loadUrl(TUNIFYX_URL);
    }

    private void showOffline() {
        binding.webView.setVisibility(View.GONE);
        binding.layoutOffline.setVisibility(View.VISIBLE);
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    // ── Back button: navigasi history WebView ─────────────────
    @Override
    public void onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ── Pause/resume WebView saat app masuk background ────────
    @Override
    protected void onPause() {
        super.onPause();
        // JANGAN pause WebView — biar audio tetap jalan di background
        // binding.webView.onPause(); // ← sengaja tidak dipanggil
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.webView.onResume();
        binding.webView.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
        binding.webView.destroy();
    }

    // ── File chooser result ───────────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_CODE) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) results = new Uri[]{Uri.parse(dataString)};
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }
}
