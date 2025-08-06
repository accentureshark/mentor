# MCP Client Backend - Alternative Execution Methods

This document describes how to run the MCP Client Backend without using `java -jar`.

## Overview

The application has been configured to run using alternative execution methods while maintaining full Spring Boot functionality and MCP compliance:

1. **Custom Startup Script**: Uses `org.springframework.boot.loader.launch.JarLauncher` instead of `java -jar`
2. **Docker Support**: Docker containers use the custom startup script 
3. **Development Mode**: Local development script for easy testing

## Execution Methods

### 1. Docker Execution (Production)

The Docker container now uses a custom startup script instead of `java -jar`:

```bash
# Build and run with Docker Compose
docker-compose up --build mentor-backend

# Or build Docker image manually
docker build -f Dockerfile.backend -t mentor-backend .
docker run -p 8083:8083 mentor-backend
```

### 2. Local Development

Use the development startup script:

```bash
cd backend
./start-dev.sh
```

This script will:
- Build the project with Maven
- Copy the JAR to the expected location
- Set up environment variables
- Start the application using the custom startup script

### 3. Manual Execution

If you want to run manually after building:

```bash
cd backend
mvn clean package -DskipTests
cp target/*.jar target/app.jar
chmod +x target/scripts/start-app.sh
cd target
APP_HOME=$(pwd) ./scripts/start-app.sh
```

## Key Changes Made

1. **Spring Boot Configuration**: Modified `pom.xml` to explicitly specify the main class and layout
2. **Custom Startup Script**: Created `start-app.sh` that uses Spring Boot's JarLauncher
3. **Docker Configuration**: Updated `Dockerfile.backend` to use the startup script
4. **Docker Compose**: Enabled the backend service with proper configuration

## Environment Variables

The startup script supports the following environment variables:

- `APP_HOME`: Application home directory (default: `/app`)
- `JAVA_OPTS`: JVM options (default: empty)
- `SPRING_PROFILES_ACTIVE`: Spring profile (default: `docker`)

## Benefits

- **No `java -jar`**: Uses classpath execution or Spring Boot JarLauncher
- **MCP Compliance**: Maintains all MCP protocol functionality
- **Spring Boot Features**: All Spring Boot features remain available
- **Flexibility**: Supports both fat JAR and extracted dependency execution
- **Development Friendly**: Easy local development setup

## Verification

The application maintains the same REST endpoints:

- Health: `GET /actuator/health` (if actuator is enabled)
- API Documentation: `GET /swagger-ui.html`
- MCP Servers: `GET /api/mcp/servers`
- Chat: `POST /api/mcp/chat/send`

All existing functionality remains unchanged - only the execution method has been modified.