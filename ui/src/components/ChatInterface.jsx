import { useState, useEffect, useRef } from 'react';
import { Card } from 'primereact/card';
import { InputTextarea } from 'primereact/inputtextarea';
import { Button } from 'primereact/button';
import { ScrollPanel } from 'primereact/scrollpanel';
import { Toast } from 'primereact/toast';
import { Avatar } from 'primereact/avatar';
import sharkLogo from '../assets/shark-ia.png';
import ReactMarkdown from 'react-markdown';
import { chatService } from '../services/chatService';
import { getServerTools } from '../services/toolService';
import '../styles/chat-interface.css';

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083/api/mcp';

export const ChatInterface = ({ selectedServer, toolsAcknowledged = false }) => {
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [tools, setTools] = useState([]);
  const [infoCollapsed, setInfoCollapsed] = useState(false);
  const [editingIndex, setEditingIndex] = useState(null);
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
    console.log(`[ChatInterface] Loading tools for ${selectedServer.id}`);
    // Load tools from the selected server
    getServerTools(selectedServer.id)
      .then((loadedTools) => {
        console.log(`[ChatInterface] Tools loaded for ${selectedServer.id}`, loadedTools);
        setTools(loadedTools);
      })
      .catch((err) => {
        console.error(`[ChatInterface] Error loading tools for ${selectedServer.id}`, err);
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

  const handleInputChange = (e) => {
    const value = e.target.value;
    setInputMessage(value);

    const lastWord = value.split(/\s+/).pop();
    if (lastWord) {
      const matches = tools
        .map((t) => t.name)
        .filter((name) => name.toLowerCase().startsWith(lastWord.toLowerCase()));
      setSuggestions(matches);
    } else {
      setSuggestions([]);
    }
  };

  const handleSuggestionClick = (suggestion) => {
    const words = inputMessage.split(/\s+/);
    words[words.length - 1] = suggestion;
    setInputMessage(words.join(' ') + ' ');
    setSuggestions([]);
  };

  const handleCopy = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.current?.show({
        severity: 'info',
        summary: 'Copied',
        detail: 'Message copied',
        life: 2000,
      });
    } catch (err) {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to copy',
        life: 2000,
      });
    }
  };

  const handleEditMessage = (index) => {
    const message = messages[index];
    if (!message || message.role !== 'USER') return;
    setInputMessage(message.content);
    setMessages(prev => prev.slice(0, index));
    setEditingIndex(index);
  };

  const handleSendMessage = async () => {
    if (!inputMessage.trim() || !selectedServer || selectedServer.status !== 'CONNECTED' || !toolsAcknowledged || loading) return;

    setSuggestions([]);
    setLoading(true);

    const sendSequential = async (userMsgs) => {
      let newMessages = [];
      for (const msg of userMsgs) {
        newMessages = [...newMessages, msg];
        setMessages(newMessages);
        const response = await chatService.sendMessage(
          BACKEND_URL,
          selectedServer.id,
          msg.content,
          conversationId
        );
        newMessages = [...newMessages, response];
        setMessages(newMessages);
      }
    };

    try {
      if (editingIndex !== null) {
        await chatService.clearConversation(BACKEND_URL, conversationId);
        const priorUserMessages = messages.filter(m => m.role === 'USER');
        const allUserMessages = [
          ...priorUserMessages,
          {
            id: Date.now().toString(),
            role: 'USER',
            content: inputMessage,
            timestamp: Date.now(),
            serverId: selectedServer.id,
          },
        ];
        setMessages([]);
        await sendSequential(allUserMessages);
        setEditingIndex(null);
      } else {
        const userMessage = {
          id: Date.now().toString(),
          role: 'USER',
          content: inputMessage,
          timestamp: Date.now(),
          serverId: selectedServer.id,
        };

        setMessages(prev => [...prev, userMessage]);
        const response = await chatService.sendMessage(
          BACKEND_URL,
          selectedServer.id,
          inputMessage,
          conversationId
        );
        setMessages(prev => [...prev, response]);
      }
      setInputMessage('');
    } catch (error) {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Error sending message',
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
        summary: 'Cleared',
        detail: 'Conversation cleared',
        life: 2000,
      });
    } catch (error) {
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Error clearing conversation',
        life: 3000,
      });
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    } else if (e.key === 'Tab' && suggestions.length > 0) {
      e.preventDefault();
      handleSuggestionClick(suggestions[0]);
    }
    // Allow Shift+Enter for new lines in multiline input
  };

  const formatTimestamp = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const renderMessage = (message, index) => {
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
              <div className="chat-message-meta">
                <span className="chat-message-role">
                  {isUser ? 'You' : selectedServer?.name || 'Assistant'}
                </span>
                <span className="chat-message-time">
                  {formatTimestamp(message.timestamp)}
                </span>
              </div>
              <div className="chat-message-actions">
                <Button
                  icon="pi pi-copy"
                  className="p-button-text p-button-rounded p-button-sm"
                  onClick={() => handleCopy(message.content)}
                  tooltip="Copy message"
                />
                {isUser && (
                  <Button
                    icon="pi pi-pencil"
                    className="p-button-text p-button-rounded p-button-sm"
                    onClick={() => handleEditMessage(index)}
                    tooltip="Edit message"
                  />
                )}
              </div>
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
                  <h3>Select an MCP Server</h3>
                    <p>Select an MCP server from the left panel to start chatting</p>
                </>
            ) : (
                <>
                  <h3>Server Not Connected</h3>
                  <p>The selected server "{selectedServer.name}" is not connected. Please connect the server first.</p>
                </>
            )}
          </div>
        </div>
    );
  }

  // If the server is connected but the tools have not been acknowledged
  if (!toolsAcknowledged) {
    return (
        <div className="chat-interface-empty">
          <div className="chat-empty-content">
            <img src={sharkLogo} alt="loading" className="shark-spinner" />
              <h3>Reviewing Server Tools</h3>
              <p>Showing the available tools on "{selectedServer.name}"...</p>
            <p><small>The chat will be enabled after reviewing the tools.</small></p>
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
                      <strong>Available tools:</strong>
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
                    <p>Start a conversation by typing a message below.</p>
                  </div>
              ) : (
                  messages.map((m, i) => renderMessage(m, i))
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
                  onChange={handleInputChange}
                  onKeyDown={handleKeyPress}
                  placeholder={`Type your message to ${selectedServer.name}... (Enter to send, Shift+Enter for new line)`}
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
            {suggestions.length > 0 && (
              <ul className="chat-suggestions">
                {suggestions.map((s) => (
                  <li key={s} onClick={() => handleSuggestionClick(s)}>
                    {s}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      </div>
  );
};

export default ChatInterface;
