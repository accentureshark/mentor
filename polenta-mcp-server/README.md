# Polenta MCP Server

A Model Context Protocol (MCP) compliant server for Data Lake access with PrestoDB.

This server implements the MCP specification from:
- https://modelcontextprotocol.io/specification/2025-06-18/server
- https://modelcontextprotocol.io/specification/2025-06-18/server/tools
- https://modelcontextprotocol.io/specification/2025-06-18/server/prompts
- https://modelcontextprotocol.io/specification/2025-06-18/server/resources
- https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/completion
- https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/logging
- https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/pagination

## MCP Compliance

This server is fully compliant with the MCP 2025-06-18 specification and includes:

### Core Server Protocol
- ✅ JSON-RPC 2.0 protocol compliance
- ✅ `initialize` method with protocol version validation
- ✅ Proper error handling with standard error codes
- ✅ Health endpoint for monitoring

### Tools Support
- ✅ `tools/list` method to enumerate available tools
- ✅ `tools/call` method to execute tools
- ✅ Three data lake tools:
  - `query_presto` - Execute SQL queries against PrestoDB
  - `list_tables` - List available tables in the data lake
  - `describe_table` - Get table schema and metadata

### Prompts Support
- ✅ `prompts/list` method to enumerate available prompts
- ✅ `prompts/get` method to retrieve prompt content
- ✅ Two specialized prompts:
  - `sql_optimization` - Generate optimized SQL queries
  - `data_exploration` - Generate data exploration queries

### Resources Support
- ✅ `resources/list` method to enumerate available resources
- ✅ `resources/read` method to read resource content
- ✅ Three data lake resources:
  - `presto://catalogs` - Available PrestoDB catalogs
  - `presto://schemas` - Available schemas
  - `presto://connection_info` - Connection information

### Utilities
- ✅ Logging support with structured logging
- ✅ Pagination parameter handling
- ✅ Proper CORS support for web clients

## Quick Start

### Prerequisites
- Python 3.8+
- pip

### Installation
```bash
cd polenta-mcp-server
pip install -r requirements.txt
```

### Running the Server
```bash
python polenta_server.py
```

The server will start on `http://localhost:25001` as expected by the Mentor client.

### Testing MCP Compliance
```bash
python test_mcp_compliance.py
```

This will run a comprehensive test suite to verify MCP specification compliance.

## API Endpoints

### Health Check
```
GET /mcp/health
```

Returns server health and version information.

### MCP JSON-RPC Endpoint
```
POST /mcp
Content-Type: application/json
```

Main endpoint for all MCP JSON-RPC requests.

### Server Information
```
GET /
```

Returns server metadata and capabilities.

## Example Usage

### Initialize Connection
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {
      "tools": {},
      "resources": {},
      "prompts": {},
      "logging": {}
    }
  }
}
```

### List Available Tools
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/list",
  "params": {}
}
```

### Execute a Tool
```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/call",
  "params": {
    "name": "query_presto",
    "arguments": {
      "query": "SELECT * FROM customer_data LIMIT 10",
      "catalog": "hive",
      "schema": "default"
    }
  }
}
```

### List Resources
```json
{
  "jsonrpc": "2.0",
  "id": "4",
  "method": "resources/list",
  "params": {}
}
```

### Read a Resource
```json
{
  "jsonrpc": "2.0",
  "id": "5",
  "method": "resources/read",
  "params": {
    "uri": "presto://catalogs"
  }
}
```

## Architecture

The server is implemented in Python using Flask and includes:

1. **McpServer Class**: Core MCP protocol implementation
2. **Request Routing**: JSON-RPC method routing and validation
3. **Tool Implementations**: Data lake specific tools for PrestoDB
4. **Resource Providers**: Information about data lake resources
5. **Prompt Generators**: SQL and data exploration prompt templates
6. **Error Handling**: Comprehensive error handling with proper codes
7. **Logging**: Structured logging for debugging and monitoring

## Configuration

The server can be configured by modifying the `McpServer` class initialization:

- **Server Name**: Change `self.server_name`
- **Version**: Change `self.server_version`
- **Port**: Modify the `app.run()` call
- **Tools**: Add/modify tools in `self.tools`
- **Prompts**: Add/modify prompts in `self.prompts`
- **Resources**: Add/modify resources in `self.resources`

## Real Implementation Notes

This is a reference implementation with simulated data lake operations. For a production deployment:

1. **Database Connection**: Implement real PrestoDB connection using `presto-python-client`
2. **Authentication**: Add proper authentication and authorization
3. **Configuration**: Externalize configuration to environment variables or config files
4. **Error Handling**: Enhance error handling for production scenarios
5. **Performance**: Add connection pooling and query optimization
6. **Security**: Implement input validation and SQL injection protection
7. **Monitoring**: Add metrics and health monitoring

## Integration with Mentor Client

This server is designed to work with the Mentor MCP client. The client expects:

- Server running on `localhost:25001`
- MCP protocol version `2025-06-18`
- JSON-RPC 2.0 compliance
- Specific endpoint patterns (`/mcp`, `/mcp/health`)

The server configuration in the Mentor client (`backend/src/main/resources/mcp.json`) should include:

```json
{
  "id": "polenta-local",
  "name": "Polenta MCP Server (Local)",
  "description": "Polenta MCP Server for Data Lake access with PrestoDB",
  "url": "http://localhost:25001",
  "implemented": true,
  "prewarm": true
}
```

## Compliance Verification

Run the compliance test suite to verify MCP specification adherence:

```bash
python test_mcp_compliance.py
```

The test suite verifies:
- Server connectivity and health
- JSON-RPC 2.0 protocol compliance
- Initialize method implementation
- Tools list and call functionality
- Prompts list and get functionality
- Resources list and read functionality
- Error handling for unknown methods
- Pagination parameter handling