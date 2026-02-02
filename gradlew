#!/bin/sh

##############################################################################
#
# Gradle start up script for POSIX (Gradle 8.5)
# Project uses wrapper: run ./gradlew instead of installing Gradle.
#
##############################################################################

# Resolve links
app_path=$0
while [ -h "$app_path" ]; do
  ls=$(ls -ld "$app_path")
  link=${ls#*' -> '}
  case $link in
    /*) app_path=$link ;;
    *) app_path=$(dirname "$app_path")/$link ;;
  esac
done
APP_HOME=$(cd "${app_path%/*}" 2>/dev/null && pwd -P) || exit 1
APP_BASE_NAME=${0##*/}
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Java command
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
  [ ! -x "$JAVACMD" ] && JAVACMD="$JAVA_HOME/jre/bin/java"
else
  JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
  echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
  echo "Please install JDK 17+ (or 21+ for virtual threads) and set JAVA_HOME if needed." >&2
  exit 1
fi

exec "$JAVACMD" \
  -Dorg.gradle.appname="$APP_BASE_NAME" \
  -Dfile.encoding=UTF-8 \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
