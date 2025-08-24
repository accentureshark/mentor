// src/services/chatService.js
import { normalizeBaseUrl } from './urlUtils';

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
      return JSON.parse(responseText);
    } catch (error) {
      console.error('❌ Error sending message:', error);
      throw error;
    }
  },
};
