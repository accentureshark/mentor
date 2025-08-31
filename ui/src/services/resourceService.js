const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083/api/mcp';

export const resourceService = {
  async getResources(serverId) {
    try {
      const response = await fetch(`${BACKEND_URL}/resources/${serverId}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return await response.json();
    } catch (error) {
      console.error('Error fetching resources:', error);
      throw error;
    }
  },

  async readResource(serverId, uri) {
    try {
      const response = await fetch(`${BACKEND_URL}/resources/${serverId}/read`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(uri),
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return await response.text();
    } catch (error) {
      console.error('Error reading resource:', error);
      throw error;
    }
  },
};