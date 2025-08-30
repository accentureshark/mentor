#!/usr/bin/env python3
"""
MCP Compliance Test Suite for Polenta Server

This script tests compliance with the MCP specification:
- https://modelcontextprotocol.io/specification/2025-06-18/server
- https://modelcontextprotocol.io/specification/2025-06-18/server/tools
- https://modelcontextprotocol.io/specification/2025-06-18/server/prompts
- https://modelcontextprotocol.io/specification/2025-06-18/server/resources
- https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/completion
- https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/logging
- https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/pagination
"""

import json
import requests
import uuid
import time
import sys
from typing import Dict, Any, List, Optional

class McpComplianceTest:
    def __init__(self, server_url: str = "http://localhost:25001"):
        self.server_url = server_url
        self.base_mcp_url = f"{server_url}/mcp"
        self.health_url = f"{server_url}/mcp/health"
        self.session = requests.Session()
        self.session.headers.update({
            "Content-Type": "application/json",
            "Accept": "application/json"
        })
        
        self.test_results = []
        self.passed_tests = 0
        self.failed_tests = 0

    def log_test(self, test_name: str, passed: bool, message: str = "", details: Any = None):
        """Log test result"""
        status = "PASS" if passed else "FAIL"
        print(f"[{status}] {test_name}: {message}")
        
        if details:
            print(f"    Details: {json.dumps(details, indent=2)}")
        
        self.test_results.append({
            "test": test_name,
            "status": status,
            "message": message,
            "details": details
        })
        
        if passed:
            self.passed_tests += 1
        else:
            self.failed_tests += 1

    def make_mcp_request(self, method: str, params: Dict = None) -> tuple[bool, Dict]:
        """Make a JSON-RPC request to the MCP server"""
        if params is None:
            params = {}
            
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": method,
            "params": params
        }
        
        try:
            response = self.session.post(self.base_mcp_url, json=request_data, timeout=10)
            response_data = response.json()
            
            if response.status_code == 200 and "result" in response_data:
                return True, response_data
            elif "error" in response_data:
                return False, response_data
            else:
                return False, {"error": f"Unexpected response: {response.status_code}"}
                
        except Exception as e:
            return False, {"error": str(e)}

    def test_server_connectivity(self):
        """Test basic server connectivity"""
        try:
            response = self.session.get(self.server_url, timeout=5)
            if response.status_code == 200:
                self.log_test("Server Connectivity", True, "Server is accessible")
                return True
            else:
                self.log_test("Server Connectivity", False, f"Server returned {response.status_code}")
                return False
        except Exception as e:
            self.log_test("Server Connectivity", False, f"Cannot connect to server: {str(e)}")
            return False

    def test_health_endpoint(self):
        """Test MCP health endpoint"""
        try:
            response = self.session.get(self.health_url, timeout=5)
            if response.status_code == 200:
                data = response.json()
                required_fields = ["status", "protocol_version"]
                missing_fields = [field for field in required_fields if field not in data]
                
                if not missing_fields:
                    self.log_test("Health Endpoint", True, "Health endpoint working correctly", data)
                    return True
                else:
                    self.log_test("Health Endpoint", False, f"Missing fields: {missing_fields}", data)
                    return False
            else:
                self.log_test("Health Endpoint", False, f"Health endpoint returned {response.status_code}")
                return False
        except Exception as e:
            self.log_test("Health Endpoint", False, f"Health endpoint error: {str(e)}")
            return False

    def test_initialize_method(self):
        """Test MCP initialize method - Core Server Protocol compliance"""
        params = {
            "protocolVersion": "2025-06-18",
            "capabilities": {
                "tools": {},
                "resources": {},
                "prompts": {},
                "logging": {}
            }
        }
        
        success, response = self.make_mcp_request("initialize", params)
        
        if success:
            result = response.get("result", {})
            required_fields = ["protocolVersion", "capabilities", "serverInfo"]
            missing_fields = [field for field in required_fields if field not in result]
            
            if not missing_fields:
                protocol_version = result.get("protocolVersion")
                if protocol_version == "2025-06-18":
                    self.log_test("Initialize Method", True, "Initialize method compliant", result)
                    return True
                else:
                    self.log_test("Initialize Method", False, f"Wrong protocol version: {protocol_version}")
                    return False
            else:
                self.log_test("Initialize Method", False, f"Missing required fields: {missing_fields}", result)
                return False
        else:
            self.log_test("Initialize Method", False, f"Initialize failed: {response.get('error', 'Unknown error')}")
            return False

    def test_tools_list_method(self):
        """Test tools/list method - Tools specification compliance"""
        success, response = self.make_mcp_request("tools/list")
        
        if success:
            result = response.get("result", {})
            if "tools" in result and isinstance(result["tools"], list):
                tools = result["tools"]
                
                # Validate tool schema
                valid_tools = True
                for tool in tools:
                    required_tool_fields = ["name", "description"]
                    missing_tool_fields = [field for field in required_tool_fields if field not in tool]
                    if missing_tool_fields:
                        self.log_test("Tools List Method", False, f"Tool missing fields: {missing_tool_fields}", tool)
                        valid_tools = False
                        break
                
                if valid_tools:
                    self.log_test("Tools List Method", True, f"Found {len(tools)} valid tools", {"tools": [t["name"] for t in tools]})
                    return True
                else:
                    return False
            else:
                self.log_test("Tools List Method", False, "Response missing 'tools' array", result)
                return False
        else:
            self.log_test("Tools List Method", False, f"Tools list failed: {response.get('error', 'Unknown error')}")
            return False

    def test_tools_call_method(self):
        """Test tools/call method - Tools specification compliance"""
        # First get available tools
        success, response = self.make_mcp_request("tools/list")
        if not success:
            self.log_test("Tools Call Method", False, "Cannot get tools list for call test")
            return False
        
        tools = response.get("result", {}).get("tools", [])
        if not tools:
            self.log_test("Tools Call Method", False, "No tools available to test")
            return False
        
        # Test calling the first tool
        test_tool = tools[0]
        tool_name = test_tool["name"]
        
        # Prepare test arguments based on tool
        if tool_name == "query_presto":
            test_args = {"query": "SELECT 1 AS test"}
        elif tool_name == "list_tables":
            test_args = {}
        elif tool_name == "describe_table":
            test_args = {"table_name": "test_table"}
        else:
            test_args = {}
        
        params = {
            "name": tool_name,
            "arguments": test_args
        }
        
        success, response = self.make_mcp_request("tools/call", params)
        
        if success:
            result = response.get("result", {})
            if "content" in result:
                self.log_test("Tools Call Method", True, f"Tool '{tool_name}' executed successfully")
                return True
            else:
                self.log_test("Tools Call Method", False, "Tool call response missing 'content'", result)
                return False
        else:
            self.log_test("Tools Call Method", False, f"Tool call failed: {response.get('error', 'Unknown error')}")
            return False

    def test_prompts_list_method(self):
        """Test prompts/list method - Prompts specification compliance"""
        success, response = self.make_mcp_request("prompts/list")
        
        if success:
            result = response.get("result", {})
            if "prompts" in result and isinstance(result["prompts"], list):
                prompts = result["prompts"]
                
                # Validate prompt schema
                valid_prompts = True
                for prompt in prompts:
                    required_prompt_fields = ["name", "description"]
                    missing_prompt_fields = [field for field in required_prompt_fields if field not in prompt]
                    if missing_prompt_fields:
                        self.log_test("Prompts List Method", False, f"Prompt missing fields: {missing_prompt_fields}", prompt)
                        valid_prompts = False
                        break
                
                if valid_prompts:
                    self.log_test("Prompts List Method", True, f"Found {len(prompts)} valid prompts", {"prompts": [p["name"] for p in prompts]})
                    return True
                else:
                    return False
            else:
                self.log_test("Prompts List Method", False, "Response missing 'prompts' array", result)
                return False
        else:
            self.log_test("Prompts List Method", False, f"Prompts list failed: {response.get('error', 'Unknown error')}")
            return False

    def test_prompts_get_method(self):
        """Test prompts/get method - Prompts specification compliance"""
        # First get available prompts
        success, response = self.make_mcp_request("prompts/list")
        if not success:
            self.log_test("Prompts Get Method", False, "Cannot get prompts list for get test")
            return False
        
        prompts = response.get("result", {}).get("prompts", [])
        if not prompts:
            self.log_test("Prompts Get Method", False, "No prompts available to test")
            return False
        
        # Test getting the first prompt
        test_prompt = prompts[0]
        prompt_name = test_prompt["name"]
        
        # Prepare test arguments based on prompt
        if prompt_name == "sql_optimization":
            test_args = {"original_query": "SELECT * FROM table"}
        elif prompt_name == "data_exploration":
            test_args = {"table_name": "test_table"}
        else:
            test_args = {}
        
        params = {
            "name": prompt_name,
            "arguments": test_args
        }
        
        success, response = self.make_mcp_request("prompts/get", params)
        
        if success:
            result = response.get("result", {})
            if "messages" in result:
                self.log_test("Prompts Get Method", True, f"Prompt '{prompt_name}' retrieved successfully")
                return True
            else:
                self.log_test("Prompts Get Method", False, "Prompt get response missing 'messages'", result)
                return False
        else:
            self.log_test("Prompts Get Method", False, f"Prompt get failed: {response.get('error', 'Unknown error')}")
            return False

    def test_resources_list_method(self):
        """Test resources/list method - Resources specification compliance"""
        success, response = self.make_mcp_request("resources/list")
        
        if success:
            result = response.get("result", {})
            if "resources" in result and isinstance(result["resources"], list):
                resources = result["resources"]
                
                # Validate resource schema
                valid_resources = True
                for resource in resources:
                    required_resource_fields = ["uri", "name"]
                    missing_resource_fields = [field for field in required_resource_fields if field not in resource]
                    if missing_resource_fields:
                        self.log_test("Resources List Method", False, f"Resource missing fields: {missing_resource_fields}", resource)
                        valid_resources = False
                        break
                
                if valid_resources:
                    self.log_test("Resources List Method", True, f"Found {len(resources)} valid resources", {"resources": [r["uri"] for r in resources]})
                    return True
                else:
                    return False
            else:
                self.log_test("Resources List Method", False, "Response missing 'resources' array", result)
                return False
        else:
            self.log_test("Resources List Method", False, f"Resources list failed: {response.get('error', 'Unknown error')}")
            return False

    def test_resources_read_method(self):
        """Test resources/read method - Resources specification compliance"""
        # First get available resources
        success, response = self.make_mcp_request("resources/list")
        if not success:
            self.log_test("Resources Read Method", False, "Cannot get resources list for read test")
            return False
        
        resources = response.get("result", {}).get("resources", [])
        if not resources:
            self.log_test("Resources Read Method", False, "No resources available to test")
            return False
        
        # Test reading the first resource
        test_resource = resources[0]
        resource_uri = test_resource["uri"]
        
        params = {
            "uri": resource_uri
        }
        
        success, response = self.make_mcp_request("resources/read", params)
        
        if success:
            result = response.get("result", {})
            if "contents" in result:
                self.log_test("Resources Read Method", True, f"Resource '{resource_uri}' read successfully")
                return True
            else:
                self.log_test("Resources Read Method", False, "Resource read response missing 'contents'", result)
                return False
        else:
            self.log_test("Resources Read Method", False, f"Resource read failed: {response.get('error', 'Unknown error')}")
            return False

    def test_json_rpc_compliance(self):
        """Test JSON-RPC 2.0 protocol compliance"""
        # Test invalid JSON-RPC request
        invalid_request = {"method": "test", "params": {}}  # Missing jsonrpc and id
        
        try:
            response = self.session.post(self.base_mcp_url, json=invalid_request, timeout=5)
            if response.status_code == 400:
                self.log_test("JSON-RPC Compliance", True, "Server correctly rejects invalid JSON-RPC requests")
                return True
            else:
                self.log_test("JSON-RPC Compliance", False, f"Server should reject invalid JSON-RPC with 400, got {response.status_code}")
                return False
        except Exception as e:
            self.log_test("JSON-RPC Compliance", False, f"Error testing JSON-RPC compliance: {str(e)}")
            return False

    def test_unknown_method_handling(self):
        """Test handling of unknown methods"""
        params = {}
        success, response = self.make_mcp_request("unknown/method", params)
        
        if not success:
            error = response.get("error", {})
            if error.get("code") == -32601:  # Method not found error code
                self.log_test("Unknown Method Handling", True, "Server correctly handles unknown methods")
                return True
            else:
                self.log_test("Unknown Method Handling", False, f"Wrong error code for unknown method: {error.get('code')}")
                return False
        else:
            self.log_test("Unknown Method Handling", False, "Server should reject unknown methods")
            return False

    def test_pagination_support(self):
        """Test pagination utilities compliance"""
        # Test tools/list with cursor parameter
        params = {"cursor": "test_cursor"}
        success, response = self.make_mcp_request("tools/list", params)
        
        if success:
            result = response.get("result", {})
            # Server should handle cursor parameter gracefully
            self.log_test("Pagination Support", True, "Server handles pagination parameters")
            return True
        else:
            # It's OK if server doesn't support pagination, but should not error
            error = response.get("error", {})
            if error.get("code") == -32602:  # Invalid params - acceptable
                self.log_test("Pagination Support", True, "Server gracefully handles pagination (not supported)")
                return True
            else:
                self.log_test("Pagination Support", False, f"Server error on pagination: {error}")
                return False

    def run_all_tests(self):
        """Run all MCP compliance tests"""
        print("=" * 60)
        print("MCP COMPLIANCE TEST SUITE")
        print("=" * 60)
        print(f"Testing server at: {self.server_url}")
        print()
        
        # Core connectivity tests
        if not self.test_server_connectivity():
            print("Server not accessible. Stopping tests.")
            return False
        
        self.test_health_endpoint()
        
        # Core MCP protocol tests
        self.test_initialize_method()
        self.test_json_rpc_compliance()
        self.test_unknown_method_handling()
        
        # Tools specification tests
        self.test_tools_list_method()
        self.test_tools_call_method()
        
        # Prompts specification tests
        self.test_prompts_list_method()
        self.test_prompts_get_method()
        
        # Resources specification tests
        self.test_resources_list_method()
        self.test_resources_read_method()
        
        # Utilities tests
        self.test_pagination_support()
        
        # Print summary
        print()
        print("=" * 60)
        print("TEST SUMMARY")
        print("=" * 60)
        print(f"Total tests: {self.passed_tests + self.failed_tests}")
        print(f"Passed: {self.passed_tests}")
        print(f"Failed: {self.failed_tests}")
        
        if self.failed_tests == 0:
            print("\n🎉 ALL TESTS PASSED - Server is MCP compliant!")
            return True
        else:
            print(f"\n❌ {self.failed_tests} tests failed - See details above")
            return False

def main():
    """Main function"""
    server_url = "http://localhost:25001"
    
    if len(sys.argv) > 1:
        server_url = sys.argv[1]
    
    print(f"Testing MCP compliance for server at: {server_url}")
    
    tester = McpComplianceTest(server_url)
    success = tester.run_all_tests()
    
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()