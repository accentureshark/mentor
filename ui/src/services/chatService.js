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

      // Handle empty response (when backend returns null for initial connections)
      if (!responseText || responseText.trim() === '') {
        console.log('✅ Empty response received (initial connection)');
        return null;
      }

      const parsed = JSON.parse(responseText);
      console.log('✅ Respuesta JSON parseada:', parsed);

      return parsed;
    } catch (err) {
      console.error('🔥 Exception sending message:', err);
      throw new Error('Failed to send message');
    }
  },

  // Send a streaming message to the backend using Server-Sent Events
  sendStreamingMessage: async (baseUrl, serverId, message, conversationId = 'default', onToken, onComplete, onError) => {
    const url = `${normalizeBaseUrl(baseUrl)}/chat/stream`;
    const payload = { serverId, message, conversationId };

    console.log('📤 Attempting to send streaming message to:', url);
    console.log('📦 Payload:', JSON.stringify(payload, null, 2));

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'Cache-Control': 'no-cache'
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.error('❌ HTTP error response:', response.status, errorText);
        throw new Error(`Failed to send streaming message: HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let fullMessage = '';

      try {
        while (true) {
          const { done, value } = await reader.read();
          
          if (done) {
            console.log('✅ Streaming completed');
            break;
          }

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop(); // Keep incomplete line in buffer

          for (const line of lines) {
            if (line.trim() === '') continue;
            
            if (line.startsWith('data: ')) {
              const data = line.slice(6);
              
              if (data === '[DONE]') {
                console.log('✅ Streaming done signal received');
                onComplete && onComplete(fullMessage);
                return;
              }

              try {
                // For raw token data, just pass it through
                fullMessage += data;
                onToken && onToken(data);
              } catch (parseError) {
                console.warn('⚠️ Could not parse streaming data:', data);
                // Still pass through as text token
                fullMessage += data;
                onToken && onToken(data);
              }
            } else if (line.startsWith('event: ')) {
              const eventType = line.slice(7);
              console.log('📡 Event type:', eventType);
              
              if (eventType === 'complete') {
                console.log('✅ Complete event received');
                onComplete && onComplete(fullMessage);
                return;
              } else if (eventType === 'error') {
                console.error('❌ Error event received');
                onError && onError(new Error('Streaming error occurred'));
                return;
              }
            }
          }
        }
      } finally {
        reader.releaseLock();
      }
      
      // If we reach here, call complete with the accumulated message
      onComplete && onComplete(fullMessage);
      
    } catch (err) {
      console.error('🔥 Exception in streaming message:', err);
      onError && onError(err);
    }
  },

  // Check if streaming is supported by the backend
  checkStreamingSupport: async (baseUrl) => {
    try {
      // For now, assume streaming is supported if the backend is reachable
      const url = `${normalizeBaseUrl(baseUrl)}/chat/conversations`;
      const response = await fetch(url, { method: 'HEAD' });
      return response.ok;
    } catch (err) {
      console.warn('Could not check streaming support:', err);
      return false;
    }
  },


  // Clear a conversation
  clearConversation: async (baseUrl, conversationId) => {
    const url = `${normalizeBaseUrl(baseUrl)}/chat/conversations/${conversationId}`;
    console.log('[chatService] Clearing conversation, URL:', url);
    
    try {
      const response = await fetch(url, { method: 'DELETE' });
      console.log('[chatService] Clear conversation response status:', response.status);
      console.log('[chatService] Clear conversation response ok:', response.ok);
      
      if (!response.ok) {
        const errorText = await response.text();
        console.error('[chatService] Clear conversation error response:', errorText);
        throw new Error(`Failed to clear conversation: ${response.status} - ${errorText}`);
      }
      
      console.log('[chatService] Clear conversation successful');
    } catch (error) {
      console.error('[chatService] Clear conversation exception:', error);
      throw error;
    }
  },
};
