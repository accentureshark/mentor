const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083/api/mcp';

export const promptService = {
  async getPrompts(serverId) {
    try {
      const response = await fetch(`${BACKEND_URL}/prompts/${serverId}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return await response.json();
    } catch (error) {
      console.error('Error fetching prompts:', error);
      throw error;
    }
  },

  async getPrompt(serverId, promptName, promptArguments = {}) {
    try {
      const response = await fetch(`${BACKEND_URL}/prompts/${serverId}/${promptName}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(promptArguments),
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return await response.text();
    } catch (error) {
      console.error('Error getting prompt:', error);
      throw error;
    }
  },
};