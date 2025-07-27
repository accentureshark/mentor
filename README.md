# Mentor - Universal Model Context Protocol Interface

This is a universal MCP (Model Context Protocol) client that can connect to multiple MCP servers, including the local Melian server and various public MCP servers.

## Architecture

- **Backend** (`backend/`): Spring Boot application providing REST APIs for MCP server management
- **UI** (`ui/`): React application with PrimeReact components for the user interface

## Quick Start

### Using Make (Recommended)

```bash
# From the root directory, build and run both client components
make run-client
```

This will:
- Build the backend (Spring Boot)
- Build the UI (React + Vite)
- Start backend on http://localhost:8083
- Start UI on http://localhost:5174

### Manual Setup

```bash
# Build and run backend
cd mcp-client/backend
mvn spring-boot:run

# In another terminal, build and run UI
cd mcp-client/ui
npm install
npm run dev
```

## Available MCP Servers

The client comes pre-configured with several MCP servers:

1. **Melian MCP Server (Local)** - The main Melian server for movie data
2. **GitHub MCP Server** - Access to GitHub repositories and issues
3. **File System MCP Server** - Local file system operations
4. **Brave Search MCP Server** - Web search capabilities
5. **SQLite MCP Server** - Database query operations

### IDE configuration

The backend now loads its MCP server list from
`backend/src/main/resources/mcp-servers.json`. This same file can be used
directly with the VSCode or IntelliJ MCP client plugins so the IDE stays in sync
with the running application.

## Usage

1. Open http://localhost:5174 in your browser
2. Select an MCP server from the left sidebar
3. Start chatting with the selected server
4. The assistant responds strictly using the context returned by the MCP server
   and avoids inferring or adding new information

## Make Targets

From the root directory:

- `make build-client` - Build both backend and UI
- `make run-client` - Run both components
- `make run-client-backend` - Run only the backend
- `make run-client-ui` - Run only the UI
- `make clean-client` - Clean build artifacts

## Requirements

- Java 17+
- Node.js 18+
- Maven 3.8+
