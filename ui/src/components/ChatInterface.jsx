import { useState, useEffect, useRef } from 'react';
import { Card } from 'primereact/card';
import { InputText } from 'primereact/inputtext';
import { Button } from 'primereact/button';
import { ScrollPanel } from 'primereact/scrollpanel';
import { Toast } from 'primereact/toast';
import { Avatar } from 'primereact/avatar';
import { chatService } from '../services/chatService';
import '../styles/chat-interface.css';

export const ChatInterface = ({ selectedServer }) => {
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId] = useState('default');
  const toast = useRef(null);
  const scrollPanelRef = useRef(null);

  const loadConversation = async () => {
    if (!selectedServer) return;
    
    try {
      const data = await chatService.getConversation(conversationId);
      setMessages(data);
    } catch (error) {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to load conversation',
        life: 3000,
      });
    }
  };

  useEffect(() => {
    loadConversation();
  }, [selectedServer, conversationId]);

  useEffect(() => {
    // Scroll to bottom when new messages are added
    if (scrollPanelRef.current) {
      const scrollElement = scrollPanelRef.current.getElement();
      scrollElement.scrollTop = scrollElement.scrollHeight;
    }
  }, [messages]);

  const handleSendMessage = async () => {
    if (!inputMessage.trim() || !selectedServer || selectedServer.status !== 'CONNECTED' || loading) return;


    const userMessage = {
      id: Date.now().toString(),
      role: 'USER',
      content: inputMessage,
      timestamp: Date.now(),
      serverId: selectedServer.id,
    };

    setMessages(prev => [...prev, userMessage]);
    setInputMessage('');
    setLoading(true);

    try {
      const response = await chatService.sendMessage(
        selectedServer.id,
        inputMessage,
        conversationId
      );
      
      setMessages(prev => [...prev, response]);
    } catch (error) {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to send message',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const formatTimestamp = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const renderMessage = (message) => {
    const isUser = message.role === 'USER';
    
    return (
      <div key={message.id} className={`chat-message ${isUser ? 'user' : 'assistant'}`}>
        <div className="chat-message-avatar">
          <Avatar
            icon={isUser ? 'pi pi-user' : 'pi pi-android'}
            shape="circle"
            className={isUser ? 'user-avatar' : 'assistant-avatar'}
          />
        </div>
        <div className="chat-message-content">
          <div className="chat-message-header">
            <span className="chat-message-role">
              {isUser ? 'You' : selectedServer?.name || 'Assistant'}
            </span>
            <span className="chat-message-time">
              {formatTimestamp(message.timestamp)}
            </span>
          </div>
          <div className="chat-message-text">
            {message.content}
          </div>
        </div>
      </div>
    );
  };

  if (!selectedServer || selectedServer.status !== 'CONNECTED') {
    return (
        <div className="chat-interface-empty">
          <div className="chat-empty-content">
            <i className="pi pi-comments" style={{ fontSize: '3rem', color: '#ccc' }} />
            {!selectedServer ? (
                <>
                  <h3>Select an MCP Server</h3>
                  <p>Choose an MCP server from the left panel to start chatting</p>
                </>
            ) : (
                <>
                  <h3>Server Not Connected</h3>
                  <p>The selected server "{selectedServer.name}" is not connected. Please connect to the server first.</p>
                </>
            )}
          </div>
        </div>
    );
  }

  return (
    <div className="chat-interface">
      <Toast ref={toast} />
      
      <Card className="chat-container">
        <div className="chat-header">
          <div className="chat-server-info">
            <h3>{selectedServer.name}</h3>
            <p>{selectedServer.description}</p>
          </div>
        </div>

        <div className="chat-messages">
          <ScrollPanel ref={scrollPanelRef} className="chat-scroll-panel" style={{ height: '100%' }}>
            {messages.length === 0 ? (
              <div className="chat-welcome">
                <h4>Welcome to {selectedServer.name}</h4>
                <p>Start a conversation by typing a message below.</p>
              </div>
            ) : (
              messages.map(renderMessage)
            )}
            {loading && (
              <div className="chat-message assistant">
                <div className="chat-message-avatar">
                  <Avatar
                    icon="pi pi-android"
                    shape="circle"
                    className="assistant-avatar"
                  />
                </div>
                <div className="chat-message-content">
                  <div className="chat-typing-indicator">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
            )}
          </ScrollPanel>
        </div>

        <div className="chat-input">
          <div className="chat-input-container">
            <InputText
                value={inputMessage}
                onChange={(e) => setInputMessage(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder={selectedServer.status === 'CONNECTED' ? `Message ${selectedServer.name}...` : 'Server not connected'}
                className="chat-input-field"
                disabled={loading || selectedServer.status !== 'CONNECTED'}
            />
            <Button
                icon="pi pi-send"
                onClick={handleSendMessage}
                disabled={!inputMessage.trim() || loading || selectedServer.status !== 'CONNECTED'}
                className="chat-send-button"
            />
          </div>
        </div>
      </Card>
    </div>
  );
};