// src/services/chatService.js

const normalizeBaseUrl = (url) => {
  const trimmed = url.replace(/\/+$/, '');
  return trimmed.endsWith('/api/mcp') ? trimmed : `${trimmed}/api/mcp`;
};

const getCurrentEventType = (lines, currentLine) => {
  // Look for the most recent event: line before the current data: line
  const currentIndex = lines.indexOf(currentLine);
  for (let i = currentIndex - 1; i >= 0; i--) {
    if (lines[i].startsWith('event:')) {
      return lines[i].substring(6).trim();
    }
  }
  return 'message'; // default event type
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

  // Send a message with streaming response
  sendMessageStream: (baseUrl, serverId, message, conversationId = 'default', onEvent) => {
    const url = `${normalizeBaseUrl(baseUrl)}/chat/send/stream`;
    const payload = { serverId, message, conversationId };

    console.log('📤 Attempting to send streaming message to:', url);
    console.log('📦 Payload:', JSON.stringify(payload, null, 2));

    return new Promise((resolve, reject) => {
      const eventSource = new EventSource(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      // Use fetch for SSE since EventSource doesn't support POST
      fetch(url, {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'Cache-Control': 'no-cache'
        },
        body: JSON.stringify(payload),
      }).then(response => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        const processStream = () => {
          reader.read().then(({ done, value }) => {
            if (done) {
              console.log('✅ Stream completed');
              resolve();
              return;
            }

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop(); // Keep incomplete line in buffer

            for (const line of lines) {
              if (line.trim() === '') continue;
              
              if (line.startsWith('event:')) {
                const event = line.substring(6).trim();
                continue;
              }
              
              if (line.startsWith('data:')) {
                const data = line.substring(5).trim();
                
                // Parse the SSE event
                const eventType = getCurrentEventType(lines, line);
                
                console.log('📡 SSE Event:', eventType, 'Data:', data);
                
                if (onEvent) {
                  onEvent({ type: eventType, data });
                }
                
                if (eventType === 'error') {
                  reject(new Error(data));
                  return;
                }
                
                if (eventType === 'complete') {
                  resolve();
                  return;
                }
              }
            }

            processStream();
          }).catch(reject);
        };

        processStream();
      }).catch(reject);
    });
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
