const API_BASE_URL = 'http://localhost:8083/api/mcp';

export const mcpServerService = {
  // Get all MCP servers
  getAllServers: async () => {
    const response = await fetch(`${API_BASE_URL}/servers`);
    if (!response.ok) {
      throw new Error('Failed to fetch MCP servers');
    }
    return response.json();
  },

  // Get a specific MCP server
  getServer: async (id) => {
    const response = await fetch(`${API_BASE_URL}/servers/${id}`);
    if (!response.ok) {
      throw new Error('Failed to fetch MCP server');
    }
    return response.json();
  },

  // Add a new MCP server
  addServer: async (server) => {
    const response = await fetch(`${API_BASE_URL}/servers`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(server),
    });
    if (!response.ok) {
      throw new Error('Failed to add MCP server');
    }
    return response.json();
  },

  // Remove an MCP server
  removeServer: async (id) => {
    const response = await fetch(`${API_BASE_URL}/servers/${id}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      throw new Error('Failed to remove MCP server');
    }
  },

  // Update server status
  updateServerStatus: async (id, status) => {
    const response = await fetch(`${API_BASE_URL}/servers/${id}/status`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(status),
    });
    if (!response.ok) {
      throw new Error('Failed to update server status');
    }
    return response.json();
  },
};