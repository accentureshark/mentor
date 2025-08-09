// src/services/chatService.js

const normalizeBaseUrl = (url) => {
  const trimmed = url.replace(/\/+$/, '');
  return trimmed.endsWith('/api/mcp') ? trimmed : `${trimmed}/api/mcp`;
};

export const chatService = {
  // Get messages for a specific conversation
  getConversation: async (baseUrl, conversationId = 'default') => {
    const url = `${normalizeBaseUrl(baseUrl)}/chat/conversations/${conversationId}`;
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error('Failed to fetch conversation');
    }
    return response.json();
  },

  // Send a message to the backend
  sendMessage: async (baseUrl, serverId, message, conversationId = 'default') => {
    const url = `${normalizeBaseUrl(baseUrl)}/chat/send`;
    const payload = { serverId, message, conversationId };

    console.log('📤 Attempting to send message to:', url);
    console.log('📦 Payload:', JSON.stringify(payload, null, 2));

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const responseText = await response.text();

      console.log('📬 Raw response text:', responseText);

      if (!response.ok) {
        console.error('❌ HTTP error response:', response.status, responseText);
        throw new Error(`Failed to send message: HTTP ${response.status}`);
      }

      const parsed = JSON.parse(responseText);
      console.log('✅ Respuesta JSON parseada:', parsed);

      return parsed;
    } catch (err) {
      console.error('🔥 Exception sending message:', err);
      throw new Error('Failed to send message');
    }
  },


  // Clear a conversation
  clearConversation: async (baseUrl, conversationId) => {
    const url = `${normalizeBaseUrl(baseUrl)}/chat/conversations/${conversationId}`;
    const response = await fetch(url, { method: 'DELETE' });
    if (!response.ok) {
      throw new Error('Failed to clear conversation');
    }
  },
};
