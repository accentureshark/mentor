#!/bin/bash

# Local development startup script for MCP Client Backend
# This script is for running the application in development mode without java -jar

set -e

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "Error: pom.xml not found. Please run this script from the backend directory."
    exit 1
fi

# Build the project if needed
echo "Building the project..."
mvn clean package -DskipTests

# Set development environment variables
export APP_HOME="$(pwd)/target"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-default}"
export JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

# Copy the JAR to expected location
cp target/*.jar target/app.jar

# Make the startup script executable
chmod +x target/scripts/start-app.sh

echo "Starting MCP Client Backend in development mode..."
echo "Profile: ${SPRING_PROFILES_ACTIVE}"
echo "Java opts: ${JAVA_OPTS}"

# Run the application using our custom startup script
exec target/scripts/start-app.sh "$@"