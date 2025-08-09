import { useState, useEffect, useRef } from 'react';
import { Card } from 'primereact/card';
import { InputTextarea } from 'primereact/inputtextarea';
import { Button } from 'primereact/button';
import { ScrollPanel } from 'primereact/scrollpanel';
import { Toast } from 'primereact/toast';
import { Avatar } from 'primereact/avatar';
import { ProgressSpinner } from 'primereact/progressspinner';
import ReactMarkdown from 'react-markdown';
import { chatService } from '../services/chatService';
import { getServerTools } from '../services/toolService';
import '../styles/chat-interface.css';

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083/api/mcp';

export const ChatInterface = ({ selectedServer, toolsAcknowledged = false }) => {
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [tools, setTools] = useState([]);
  const [infoCollapsed, setInfoCollapsed] = useState(false);
  const toast = useRef(null);
  const scrollPanelRef = useRef(null);

  const conversationId = selectedServer ? `server-${selectedServer.id}` : 'default';

  const loadConversation = async () => {
    if (!selectedServer) return;

    try {
      const history = await chatService.getConversation(BACKEND_URL, conversationId);
      setMessages(history);

      // If this is the first connection, request available tools
      if (history.length === 0 && selectedServer.status === 'CONNECTED') {
        try {
          const initial = await chatService.sendMessage(
            BACKEND_URL,
            selectedServer.id,
            '',
            conversationId
          );
          setMessages([initial]);
        } catch (err) {
          console.error('Failed to load initial tools', err);
        }
      }
    } catch (error) {
      setMessages([]);
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to load conversation',
        life: 3000,
      });
    }
  };

  useEffect(() => {
    if (!selectedServer) return;
    console.log(`[ChatInterface] Cargando tools para ${selectedServer.id}`);
    // Cargar tools del servidor seleccionado
    getServerTools(selectedServer.id)
      .then((loadedTools) => {
        console.log(`[ChatInterface] Tools cargadas para ${selectedServer.id}`, loadedTools);
        setTools(loadedTools);
      })
      .catch((err) => {
        console.error(`[ChatInterface] Error cargando tools para ${selectedServer.id}`, err);
        setTools([]);
      });
    loadConversation();
  }, [selectedServer]);

  useEffect(() => {
    if (scrollPanelRef.current) {
      const scrollElement = scrollPanelRef.current.getElement();
      scrollElement.scrollTop = scrollElement.scrollHeight;
    }
  }, [messages]);

  const handleSendMessage = async () => {
    if (!inputMessage.trim() || !selectedServer || selectedServer.status !== 'CONNECTED' || !toolsAcknowledged || loading) return;

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
              {isUser ? (
                message.content
              ) : (
                <ReactMarkdown
                  components={{
                    // Customize how markdown elements are rendered
                    p: ({children}) => <p style={{margin: '0.5em 0'}}>{children}</p>,
                    h1: ({children}) => <h3 style={{color: '#2196f3', margin: '1em 0 0.5em 0'}}>{children}</h3>,
                    h2: ({children}) => <h4 style={{color: '#2196f3', margin: '0.8em 0 0.4em 0'}}>{children}</h4>,
                    h3: ({children}) => <h5 style={{color: '#2196f3', margin: '0.6em 0 0.3em 0'}}>{children}</h5>,
                    ul: ({children}) => <ul style={{margin: '0.5em 0', paddingLeft: '1.5em'}}>{children}</ul>,
                    ol: ({children}) => <ol style={{margin: '0.5em 0', paddingLeft: '1.5em'}}>{children}</ol>,
                    li: ({children}) => <li style={{margin: '0.2em 0'}}>{children}</li>,
                    strong: ({children}) => <strong style={{color: '#1976d2'}}>{children}</strong>,
                    em: ({children}) => <em style={{color: '#666'}}>{children}</em>,
                    code: ({children}) => <code style={{backgroundColor: '#f5f5f5', padding: '0.2em 0.4em', borderRadius: '3px'}}>{children}</code>,
                    pre: ({children}) => <pre style={{backgroundColor: '#f5f5f5', padding: '1em', borderRadius: '5px', overflow: 'auto'}}>{children}</pre>
                  }}
                >
                  {message.content}
                </ReactMarkdown>
              )}
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

  // Si el servidor está conectado pero las herramientas no han sido reconocidas
  if (!toolsAcknowledged) {
    return (
        <div className="chat-interface-empty">
          <div className="chat-empty-content">
            <ProgressSpinner style={{width: '3rem', height: '3rem'}} strokeWidth="4" animationDuration="1s" />
            <h3>Revisando Herramientas del Servidor</h3>
            <p>Mostrando las herramientas disponibles en "{selectedServer.name}"...</p>
            <p><small>El chat se habilitará después de revisar las herramientas.</small></p>
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
              <div className="chat-server-info-header">
                <h3>{selectedServer.name}</h3>
                <Button
                  icon={infoCollapsed ? 'pi pi-chevron-down' : 'pi pi-chevron-up'}
                  className="p-button-text p-button-rounded p-button-sm"
                  onClick={() => setInfoCollapsed(prev => !prev)}
                />
              </div>
              {!infoCollapsed && (
                <>
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
                </>
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
                  disabled={loading || selectedServer.status !== 'CONNECTED' || !toolsAcknowledged}
                  rows={3}
                  autoResize
              />
              <Button
                  icon="pi pi-send"
                  onClick={handleSendMessage}
                  disabled={!inputMessage.trim() || loading || selectedServer.status !== 'CONNECTED' || !toolsAcknowledged}
                  className="chat-send-button"
              />
            </div>
          </div>
        </Card>
      </div>
  );
};

export default ChatInterface;
