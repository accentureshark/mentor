#!/usr/bin/env python3
"""
Polenta MCP Server - A Model Context Protocol compliant server for Data Lake access with PrestoDB

This server implements the MCP specification from https://modelcontextprotocol.io/specification/2025-06-18/server
and related specification documents for tools, prompts, resources, logging, and pagination.
"""

import json
import uuid
import logging
from typing import Dict, List, Any, Optional
from flask import Flask, request, jsonify
from flask_cors import CORS
import traceback

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# MCP Protocol Constants
MCP_PROTOCOL_VERSION = "2025-06-18"

class McpServer:
    """
    MCP Server implementation for Polenta Data Lake server
    Compliant with MCP specification 2025-06-18
    """
    
    def __init__(self):
        self.server_name = "Polenta MCP Server"
        self.server_version = "1.0.0"
        self.protocol_version = MCP_PROTOCOL_VERSION
        
        # Server capabilities - as per MCP spec
        self.capabilities = {
            "tools": {
                "listChanged": True
            },
            "resources": {
                "subscribe": True,
                "listChanged": True
            },
            "prompts": {
                "listChanged": True
            },
            "logging": {},
            "experimental": {}
        }
        
        # Define available tools for Data Lake / PrestoDB operations
        self.tools = [
            {
                "name": "query_presto",
                "description": "Execute a query against the PrestoDB data lake",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "SQL query to execute against PrestoDB"
                        },
                        "catalog": {
                            "type": "string", 
                            "description": "Catalog to query against",
                            "default": "hive"
                        },
                        "schema": {
                            "type": "string",
                            "description": "Schema to query against", 
                            "default": "default"
                        }
                    },
                    "required": ["query"]
                }
            },
            {
                "name": "list_tables",
                "description": "List available tables in the data lake",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "catalog": {
                            "type": "string",
                            "description": "Catalog to list tables from",
                            "default": "hive"
                        },
                        "schema": {
                            "type": "string", 
                            "description": "Schema to list tables from",
                            "default": "default"
                        }
                    }
                }
            },
            {
                "name": "describe_table",
                "description": "Get the schema and metadata for a specific table",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "table_name": {
                            "type": "string",
                            "description": "Name of the table to describe"
                        },
                        "catalog": {
                            "type": "string",
                            "description": "Catalog containing the table",
                            "default": "hive"
                        },
                        "schema": {
                            "type": "string",
                            "description": "Schema containing the table",
                            "default": "default"
                        }
                    },
                    "required": ["table_name"]
                }
            },
            {
                "name": "list_schemas",
                "description": "List available schemas in the data lake",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "catalog": {
                            "type": "string",
                            "description": "Catalog to list schemas from",
                            "default": "hive"
                        }
                    }
                }
            }
        ]
        
        # Define available prompts
        self.prompts = [
            {
                "name": "sql_optimization",
                "description": "Generate optimized SQL queries for PrestoDB",
                "arguments": [
                    {
                        "name": "original_query",
                        "description": "The original SQL query to optimize",
                        "required": True
                    },
                    {
                        "name": "performance_goal", 
                        "description": "Performance optimization goal (speed, memory, etc.)",
                        "required": False
                    }
                ]
            },
            {
                "name": "data_exploration",
                "description": "Generate queries for data exploration and profiling",
                "arguments": [
                    {
                        "name": "table_name",
                        "description": "Table to explore",
                        "required": True
                    },
                    {
                        "name": "exploration_type",
                        "description": "Type of exploration (summary, nulls, duplicates, etc.)",
                        "required": False
                    }
                ]
            }
        ]
        
        # Define available resources  
        self.resources = [
            {
                "uri": "presto://catalogs",
                "name": "Available Catalogs",
                "description": "List of available PrestoDB catalogs",
                "mimeType": "application/json"
            },
            {
                "uri": "presto://schemas",
                "name": "Available Schemas", 
                "description": "List of available schemas across all catalogs",
                "mimeType": "application/json"
            },
            {
                "uri": "presto://connection_info",
                "name": "Connection Information",
                "description": "Information about the PrestoDB connection and cluster",
                "mimeType": "application/json"
            }
        ]

    def create_success_response(self, request_id: str, result: Any) -> Dict:
        """Create a successful JSON-RPC response"""
        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "result": result
        }
    
    def create_error_response(self, request_id: Optional[str], code: int, message: str, data: Any = None) -> Dict:
        """Create an error JSON-RPC response"""
        error = {
            "code": code,
            "message": message
        }
        if data is not None:
            error["data"] = data
            
        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "error": error
        }

    def handle_initialize(self, params: Dict, request_id: str) -> Dict:
        """Handle MCP initialize method"""
        logger.info(f"Initialize request received with params: {params}")
        
        # Validate protocol version
        client_version = params.get("protocolVersion")
        if client_version != MCP_PROTOCOL_VERSION:
            logger.warning(f"Protocol version mismatch. Client: {client_version}, Server: {MCP_PROTOCOL_VERSION}")
        
        result = {
            "protocolVersion": self.protocol_version,
            "capabilities": self.capabilities,
            "serverInfo": {
                "name": self.server_name,
                "version": self.server_version
            }
        }
        
        return self.create_success_response(request_id, result)

    def handle_tools_list(self, params: Dict, request_id: str) -> Dict:
        """Handle tools/list method"""
        logger.info("Tools list request received")
        
        # Support pagination as per MCP spec
        cursor = params.get("cursor")
        
        # For simplicity, return all tools (in real implementation, would support pagination)
        result = {
            "tools": self.tools
        }
        
        # Add pagination info if needed
        if cursor is not None:
            result["nextCursor"] = None  # No more pages
            
        return self.create_success_response(request_id, result)

    def handle_tools_call(self, params: Dict, request_id: str) -> Dict:
        """Handle tools/call method"""
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        
        logger.info(f"Tool call request: {tool_name} with arguments: {arguments}")
        
        # Find the tool
        tool = next((t for t in self.tools if t["name"] == tool_name), None)
        if not tool:
            return self.create_error_response(request_id, -32602, f"Tool '{tool_name}' not found")
        
        try:
            # Simulate tool execution based on tool type
            if tool_name == "query_presto":
                result = self._execute_presto_query(arguments)
            elif tool_name == "list_tables":
                result = self._list_tables(arguments) 
            elif tool_name == "describe_table":
                result = self._describe_table(arguments)
            elif tool_name == "list_schemas":
                result = self._list_schemas(arguments)
            else:
                return self.create_error_response(request_id, -32603, f"Tool '{tool_name}' execution not implemented")
            
            return self.create_success_response(request_id, {
                "content": [
                    {
                        "type": "text",
                        "text": json.dumps(result, indent=2)
                    }
                ]
            })
            
        except Exception as e:
            logger.error(f"Error executing tool {tool_name}: {str(e)}")
            return self.create_error_response(request_id, -32603, f"Tool execution failed: {str(e)}")

    def handle_prompts_list(self, params: Dict, request_id: str) -> Dict:
        """Handle prompts/list method"""
        logger.info("Prompts list request received")
        
        cursor = params.get("cursor")
        
        result = {
            "prompts": self.prompts
        }
        
        if cursor is not None:
            result["nextCursor"] = None
            
        return self.create_success_response(request_id, result)

    def handle_prompts_get(self, params: Dict, request_id: str) -> Dict:
        """Handle prompts/get method"""
        prompt_name = params.get("name")
        arguments = params.get("arguments", {})
        
        logger.info(f"Prompt get request: {prompt_name} with arguments: {arguments}")
        
        # Find the prompt
        prompt = next((p for p in self.prompts if p["name"] == prompt_name), None)
        if not prompt:
            return self.create_error_response(request_id, -32602, f"Prompt '{prompt_name}' not found")
        
        try:
            # Generate prompt content based on prompt type
            if prompt_name == "sql_optimization":
                content = self._generate_sql_optimization_prompt(arguments)
            elif prompt_name == "data_exploration":
                content = self._generate_data_exploration_prompt(arguments)
            else:
                content = f"Prompt '{prompt_name}' content generation not implemented"
            
            return self.create_success_response(request_id, {
                "description": prompt["description"],
                "messages": [
                    {
                        "role": "user",
                        "content": {
                            "type": "text",
                            "text": content
                        }
                    }
                ]
            })
            
        except Exception as e:
            logger.error(f"Error generating prompt {prompt_name}: {str(e)}")
            return self.create_error_response(request_id, -32603, f"Prompt generation failed: {str(e)}")

    def handle_resources_list(self, params: Dict, request_id: str) -> Dict:
        """Handle resources/list method"""
        logger.info("Resources list request received")
        
        cursor = params.get("cursor")
        
        result = {
            "resources": self.resources
        }
        
        if cursor is not None:
            result["nextCursor"] = None
            
        return self.create_success_response(request_id, result)

    def handle_resources_read(self, params: Dict, request_id: str) -> Dict:
        """Handle resources/read method"""
        uri = params.get("uri")
        
        logger.info(f"Resource read request: {uri}")
        
        # Find the resource
        resource = next((r for r in self.resources if r["uri"] == uri), None)
        if not resource:
            return self.create_error_response(request_id, -32602, f"Resource '{uri}' not found")
        
        try:
            # Generate resource content based on URI
            if uri == "presto://catalogs":
                content = self._get_catalogs_info()
            elif uri == "presto://schemas":
                content = self._get_schemas_info()
            elif uri == "presto://connection_info":
                content = self._get_connection_info()
            else:
                content = {"error": f"Resource content for '{uri}' not implemented"}
            
            return self.create_success_response(request_id, {
                "contents": [
                    {
                        "uri": uri,
                        "mimeType": resource["mimeType"],
                        "text": json.dumps(content, indent=2) if isinstance(content, dict) else str(content)
                    }
                ]
            })
            
        except Exception as e:
            logger.error(f"Error reading resource {uri}: {str(e)}")
            return self.create_error_response(request_id, -32603, f"Resource read failed: {str(e)}")

    # Tool implementation methods (simulated for demonstration)
    def _execute_presto_query(self, arguments: Dict) -> Dict:
        """Simulate PrestoDB query execution"""
        query = arguments.get("query", "")
        catalog = arguments.get("catalog", "hive")
        schema = arguments.get("schema", "default")
        
        # This would normally execute against real PrestoDB
        return {
            "query": query,
            "catalog": catalog,
            "schema": schema,
            "status": "success",
            "rows_affected": 42,
            "execution_time_ms": 156,
            "results": [
                {"column1": "value1", "column2": "value2"},
                {"column1": "value3", "column2": "value4"}
            ],
            "note": "This is a simulated result. In a real implementation, this would execute against PrestoDB."
        }

    def _list_tables(self, arguments: Dict) -> Dict:
        """Simulate listing tables from data lake"""
        catalog = arguments.get("catalog", "hive")
        schema = arguments.get("schema", "default")
        
        return {
            "catalog": catalog,
            "schema": schema,
            "tables": [
                {"name": "customer_data", "type": "table", "rows": 1000000},
                {"name": "order_history", "type": "table", "rows": 5000000},
                {"name": "product_catalog", "type": "table", "rows": 50000},
                {"name": "analytics_daily", "type": "view", "rows": None}
            ],
            "note": "This is a simulated result. In a real implementation, this would query PrestoDB system tables."
        }

    def _describe_table(self, arguments: Dict) -> Dict:
        """Simulate describing a table schema"""
        table_name = arguments.get("table_name")
        catalog = arguments.get("catalog", "hive")
        schema = arguments.get("schema", "default")
        
        # Simulate different table schemas based on name
        if table_name == "customer_data":
            columns = [
                {"name": "customer_id", "type": "bigint", "nullable": False},
                {"name": "first_name", "type": "varchar(50)", "nullable": True},
                {"name": "last_name", "type": "varchar(50)", "nullable": True},
                {"name": "email", "type": "varchar(100)", "nullable": True},
                {"name": "created_at", "type": "timestamp", "nullable": False}
            ]
        else:
            columns = [
                {"name": "id", "type": "bigint", "nullable": False},
                {"name": "data", "type": "varchar(255)", "nullable": True}
            ]
        
        return {
            "table_name": table_name,
            "catalog": catalog,
            "schema": schema,
            "columns": columns,
            "table_type": "table",
            "note": "This is a simulated result. In a real implementation, this would query PrestoDB system tables."
        }

    def _list_schemas(self, arguments: Dict) -> Dict:
        """Simulate listing schemas from data lake"""
        catalog = arguments.get("catalog", "hive")
        
        return {
            "catalog": catalog,
            "schemas": [
                {"name": "default", "table_count": 15, "description": "Default schema"},
                {"name": "analytics", "table_count": 8, "description": "Analytics and reporting tables"},
                {"name": "staging", "table_count": 23, "description": "Staging area for data processing"},
                {"name": "production", "table_count": 45, "description": "Production data tables"},
                {"name": "warehouse", "table_count": 12, "description": "Data warehouse tables"}
            ],
            "note": "This is a simulated result. In a real implementation, this would query PrestoDB system tables."
        }

    def _generate_sql_optimization_prompt(self, arguments: Dict) -> str:
        """Generate SQL optimization prompt"""
        original_query = arguments.get("original_query", "")
        performance_goal = arguments.get("performance_goal", "general optimization")
        
        return f"""Please optimize the following SQL query for {performance_goal}:

Original Query:
{original_query}

Consider the following optimization techniques:
1. Index usage and query planning
2. Join order optimization
3. Predicate pushdown
4. Partition pruning
5. Columnar storage optimizations
6. Memory usage optimization

Please provide the optimized query with explanations for the changes made."""

    def _generate_data_exploration_prompt(self, arguments: Dict) -> str:
        """Generate data exploration prompt"""
        table_name = arguments.get("table_name", "")
        exploration_type = arguments.get("exploration_type", "summary")
        
        return f"""Please generate queries to explore the '{table_name}' table for {exploration_type} analysis:

Table: {table_name}

Generate queries for:
1. Basic row count and column information
2. Data quality checks (null values, duplicates)
3. Statistical summaries for numeric columns
4. Distribution analysis for categorical columns
5. Data freshness and temporal patterns

Please provide executable SQL queries with explanations."""

    def _get_catalogs_info(self) -> Dict:
        """Get information about available PrestoDB catalogs"""
        return {
            "catalogs": [
                {
                    "name": "hive",
                    "connector": "hive-hadoop2",
                    "description": "Hive connector for data lake access"
                },
                {
                    "name": "mysql",
                    "connector": "mysql",
                    "description": "MySQL connector for operational data"
                },
                {
                    "name": "memory",
                    "connector": "memory",
                    "description": "Memory connector for temporary tables"
                }
            ],
            "note": "This is a simulated result. In a real implementation, this would query PrestoDB system catalogs."
        }

    def _get_schemas_info(self) -> Dict:
        """Get information about available schemas"""
        return {
            "schemas": [
                {"catalog": "hive", "schema": "default", "table_count": 15},
                {"catalog": "hive", "schema": "analytics", "table_count": 8},
                {"catalog": "hive", "schema": "staging", "table_count": 23},
                {"catalog": "mysql", "schema": "production", "table_count": 45}
            ],
            "note": "This is a simulated result. In a real implementation, this would query PrestoDB system tables."
        }

    def _get_connection_info(self) -> Dict:
        """Get PrestoDB connection information"""
        return {
            "cluster_info": {
                "coordinator": "presto-coordinator:8080",
                "workers": 4,
                "version": "0.284",
                "environment": "development"
            },
            "connection_status": "connected",
            "note": "This is a simulated result. In a real implementation, this would query PrestoDB cluster status."
        }

# Create server instance
mcp_server = McpServer()

@app.route('/mcp/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "server": mcp_server.server_name,
        "version": mcp_server.server_version,
        "protocol_version": mcp_server.protocol_version
    })

@app.route('/mcp', methods=['POST'])
def handle_mcp_request():
    """Main MCP endpoint for JSON-RPC requests"""
    try:
        if not request.is_json:
            return jsonify(mcp_server.create_error_response(None, -32700, "Parse error: Request must be JSON")), 400
        
        data = request.get_json()
        if not data:
            return jsonify(mcp_server.create_error_response(None, -32700, "Parse error: Invalid JSON")), 400
        
        # Validate JSON-RPC format
        if data.get("jsonrpc") != "2.0":
            return jsonify(mcp_server.create_error_response(data.get("id"), -32600, "Invalid Request: Missing or invalid jsonrpc version")), 400
        
        method = data.get("method")
        params = data.get("params", {})
        request_id = data.get("id")
        
        if not method:
            return jsonify(mcp_server.create_error_response(request_id, -32600, "Invalid Request: Missing method")), 400
        
        logger.info(f"Received MCP request: method={method}, id={request_id}")
        
        # Route to appropriate handler
        if method == "initialize":
            response = mcp_server.handle_initialize(params, request_id)
        elif method == "tools/list":
            response = mcp_server.handle_tools_list(params, request_id)
        elif method == "tools/call":
            response = mcp_server.handle_tools_call(params, request_id)
        elif method == "prompts/list":
            response = mcp_server.handle_prompts_list(params, request_id)
        elif method == "prompts/get":
            response = mcp_server.handle_prompts_get(params, request_id)
        elif method == "resources/list":
            response = mcp_server.handle_resources_list(params, request_id)
        elif method == "resources/read":
            response = mcp_server.handle_resources_read(params, request_id)
        else:
            response = mcp_server.create_error_response(request_id, -32601, f"Method not found: {method}")
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"Error handling MCP request: {str(e)}\n{traceback.format_exc()}")
        return jsonify(mcp_server.create_error_response(None, -32603, f"Internal error: {str(e)}")), 500

@app.route('/', methods=['GET'])
def root():
    """Root endpoint with server information"""
    return jsonify({
        "name": mcp_server.server_name,
        "version": mcp_server.server_version,
        "protocol_version": mcp_server.protocol_version,
        "description": "Polenta MCP Server for Data Lake access with PrestoDB",
        "endpoints": {
            "mcp": "/mcp",
            "health": "/mcp/health"
        },
        "capabilities": list(mcp_server.capabilities.keys()),
        "mcp_specification": "https://modelcontextprotocol.io/specification/2025-06-18/server"
    })

if __name__ == '__main__':
    logger.info(f"Starting {mcp_server.server_name} v{mcp_server.server_version}")
    logger.info(f"MCP Protocol Version: {mcp_server.protocol_version}")
    logger.info("Server will be available at http://localhost:25001")
    
    # Run on port 25001 as expected by the Mentor client
    app.run(host='0.0.0.0', port=25001, debug=False)