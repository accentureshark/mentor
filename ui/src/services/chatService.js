const API_BASE_URL = 'http://localhost:8083/api/mcp';

export const chatService = {
  // Get all conversation IDs
  getConversations: async () => {
    const response = await fetch(`${API_BASE_URL}/chat/conversations`);
    if (!response.ok) {
      throw new Error('Failed to fetch conversations');
    }
    return response.json();
  },

  // Get messages for a specific conversation
  getConversation: async (conversationId = 'default') => {
    const response = await fetch(`${API_BASE_URL}/chat/conversations/${conversationId}`);
    if (!response.ok) {
      throw new Error('Failed to fetch conversation');
    }
    return response.json();
  },

  // Send a message to an MCP server
  sendMessage: async (serverId, message, conversationId = 'default') => {
    const response = await fetch(`${API_BASE_URL}/chat/send`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        serverId,
        message,
        conversationId,
      }),
    });
    if (!response.ok) {
      throw new Error('Failed to send message');
    }
    return response.json();
  },

  // Clear a conversation
  clearConversation: async (conversationId) => {
    const response = await fetch(`${API_BASE_URL}/chat/conversations/${conversationId}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      throw new Error('Failed to clear conversation');
    }
  },
};