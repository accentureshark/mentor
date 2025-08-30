const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083/api/mcp';

export const capabilityService = {
  async getCapabilities(serverId) {
    try {
      const response = await fetch(`${BACKEND_URL}/capabilities/${serverId}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return await response.json();
    } catch (error) {
      console.error('Error fetching capabilities:', error);
      throw error;
    }
  },

  async refreshCapabilities(serverId) {
    try {
      const response = await fetch(`${BACKEND_URL}/capabilities/${serverId}/refresh`, {
        method: 'POST',
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return await response.json();
    } catch (error) {
      console.error('Error refreshing capabilities:', error);
      throw error;
    }
  },
};