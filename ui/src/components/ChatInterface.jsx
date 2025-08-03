import { useState, useEffect, useRef } from 'react';
import { Card } from 'primereact/card';
import { InputTextarea } from 'primereact/inputtextarea';
import { Button } from 'primereact/button';
import { ScrollPanel } from 'primereact/scrollpanel';
import { Toast } from 'primereact/toast';
import { Avatar } from 'primereact/avatar';
import { chatService } from '../services/chatService';
import { getServerTools } from '../services/toolService';
import '../styles/chat-interface.css';

const normalizeBaseUrl = (url) => {
  const trimmed = url.replace(/\/+$/, '');
  return trimmed.endsWith('/api/mcp') ? trimmed : `${trimmed}/api/mcp`;
};

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083/api/mcp';

export const ChatInterface = ({ selectedServer }) => {
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId] = useState('default');
  const [tools, setTools] = useState([]);
  const toast = useRef(null);
  const scrollPanelRef = useRef(null);

  const loadConversation = async () => {
    if (!selectedServer) return;

    try {
      const newMessage = await chatService.sendMessage(
          BACKEND_URL,
          selectedServer.id,
          inputMessage,
          conversationId
      );
      setMessages(prev => [...prev, newMessage]);
    } catch (error) {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to send message',
        life: 3000,
      });
    }
  };

  useEffect(() => {
    if (!selectedServer) return;
    // Cargar tools del servidor seleccionado
    getServerTools(selectedServer.id)
      .then(setTools)
      .catch(() => setTools([]));
    loadConversation();
    // eslint-disable-next-line
  }, [selectedServer, conversationId]);

  useEffect(() => {
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
          BACKEND_URL,
          selectedServer.id,
          inputMessage,
          conversationId
      );
      setMessages(prev => [...prev, response]);
    } catch (error) {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Error al enviar mensaje',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  };

  const handleClearMessages = async () => {
    try {
      await chatService.clearConversation(BACKEND_URL, conversationId);
      setMessages([]);
      toast.current?.show({
        severity: 'success',
        summary: 'Limpiado',
        detail: 'Conversación limpiada',
        life: 2000,
      });
    } catch (error) {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Error al limpiar conversación',
        life: 3000,
      });
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
    // Allow Shift+Enter for new lines in multiline input
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
              {isUser ? 'Tú' : selectedServer?.name || 'Asistente'}
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
                  <h3>Selecciona un Servidor MCP</h3>
                  <p>Elige un servidor MCP del panel izquierdo para comenzar a chatear</p>
                </>
            ) : (
                <>
                  <h3>Servidor No Conectado</h3>
                  <p>El servidor seleccionado "{selectedServer.name}" no está conectado. Por favor, conecta al servidor primero.</p>
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
              {/* Mostrar tools del servidor */}
              {tools.length > 0 && (
                <div className="chat-server-tools">
                  <strong>Herramientas disponibles:</strong>
                  <ul>
                    {tools.map(tool => (
                      <li key={tool.name}>
                        <b>{tool.name}</b>: {tool.description}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
            <div className="chat-header-actions">
              <Button
                  icon="pi pi-trash"
                  className="p-button-text"
                  onClick={handleClearMessages}
                  disabled={messages.length === 0}
                  tooltip="Clear conversation"
              />
            </div>
          </div>

          <div className="chat-messages">
            <ScrollPanel ref={scrollPanelRef} className="chat-scroll-panel" style={{ height: '100%' }}>
              {messages.length === 0 ? (
                  <div className="chat-welcome">
                    <h4>Bienvenido a {selectedServer.name}</h4>
                    <p>Comienza una conversación escribiendo un mensaje abajo.</p>
                  </div>
              ) : (
                  messages.map(renderMessage)
              )}
              {loading && (
                  <div className="chat-message assistant">
                    <div className="chat-message-avatar">
                      <Avatar icon="pi pi-android" shape="circle" className="assistant-avatar" />
                    </div>
                    <div className="chat-message-content">
                      <div className="chat-typing-indicator">
                        <span></span><span></span><span></span>
                      </div>
                    </div>
                  </div>
              )}
            </ScrollPanel>
          </div>

          <div className="chat-input">
            <div className="chat-input-container">
              <InputTextarea
                  value={inputMessage}
                  onChange={(e) => setInputMessage(e.target.value)}
                  onKeyDown={handleKeyPress}
                  placeholder={`Escribe tu mensaje para ${selectedServer.name}... (Enter para enviar, Shift+Enter para nueva línea)`}
                  className="chat-input-field"
                  disabled={loading || selectedServer.status !== 'CONNECTED'}
                  rows={3}
                  autoResize
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

export default ChatInterface;