#!/bin/bash

# MCP Client Backend Startup Script
# This script starts the Spring Boot application without using java -jar

set -e

# Default values
APP_HOME="${APP_HOME:-/app}"
JAVA_OPTS="${JAVA_OPTS:-}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-docker}"
MAIN_CLASS="org.shark.mentor.mcp.McpClientApplication"

echo "Starting MCP Client Backend..."
echo "APP_HOME: ${APP_HOME}"
echo "JAVA_OPTS: ${JAVA_OPTS}"
echo "SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}"
echo "MAIN_CLASS: ${MAIN_CLASS}"

# Java system properties
JAVA_SYSTEM_PROPS="-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}"

# Check if we have extracted libs or use fat jar
if [ -d "${APP_HOME}/lib" ] && [ "$(ls -A ${APP_HOME}/lib)" ]; then
    # Use extracted libraries for classpath execution
    CLASSPATH="${APP_HOME}/lib/*:${APP_HOME}/app.jar"
    echo "Using classpath execution with extracted dependencies"
    echo "CLASSPATH: ${CLASSPATH}"
    exec java ${JAVA_OPTS} ${JAVA_SYSTEM_PROPS} -cp "${CLASSPATH}" "${MAIN_CLASS}" "$@"
else
    # Use org.springframework.boot.loader.launch.JarLauncher for proper Spring Boot execution
    echo "Using Spring Boot JarLauncher execution (no java -jar)"
    exec java ${JAVA_OPTS} ${JAVA_SYSTEM_PROPS} -cp "${APP_HOME}/app.jar" org.springframework.boot.loader.launch.JarLauncher "$@"
fi