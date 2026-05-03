#!/bin/sh
SAVED="$(pwd)"
cd "$(dirname "$0")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null
APP_BASE_NAME="$(basename "$0")"
DEFAULT_JVM_OPTS="-Xmx512m -Xms128m"'
die() { echo; echo "$*"; echo; exit 1; }
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    [ ! -x "$JAVACMD" ] && die "JAVA_HOME invalid: $JAVA_HOME"
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "No java found in PATH."
fi
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
