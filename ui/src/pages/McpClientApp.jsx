import { useState, useEffect } from 'react';
import { McpHeader } from '../components/McpHeader';
import { McpServerList } from '../components/McpServerList';
import { ChatInterface } from '../components/ChatInterface';
import { ToolsModal } from '../components/ToolsModal';
import { getServerTools } from '../services/toolService';
import '../styles/mcp-client-app.css';

const McpClientApp = () => {
  const [selectedServer, setSelectedServer] = useState(null);
  const [servers, setServers] = useState([]);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [toolsAcknowledged, setToolsAcknowledged] = useState(new Set());
  const [showToolsModal, setShowToolsModal] = useState(false);
  const [currentTools, setCurrentTools] = useState([]);
  const toggleSidebar = () => setSidebarCollapsed(prev => !prev);

  const handleServerSelect = async (server) => {
    setSelectedServer(server);
    
    // Si el servidor está conectado y no hemos mostrado las herramientas aún
    if (server.status === 'CONNECTED' && !toolsAcknowledged.has(server.id)) {
      try {
        console.log('Fetching tools for first-time server selection:', server.name);
        const tools = await getServerTools(server.id);
        setCurrentTools(tools);
        setShowToolsModal(true);
      } catch (error) {
        console.error('Error fetching tools:', error);
        // Si no se pueden obtener las herramientas, marcar como reconocido para permitir el chat
        setToolsAcknowledged(prev => new Set(prev).add(server.id));
      }
    }
  };

  const handleToolsModalHide = () => {
    setShowToolsModal(false);
    if (selectedServer) {
      // Marcar las herramientas como reconocidas para este servidor
      setToolsAcknowledged(prev => new Set(prev).add(selectedServer.id));
    }
  };

  const handleServersUpdate = (updatedServers) => {
    setServers(updatedServers);

    // Si hay un servidor seleccionado, actualízalo con los nuevos datos
    if (selectedServer) {
      const updatedSelectedServer = updatedServers.find(s => s.id === selectedServer.id);
      if (updatedSelectedServer) {
        setSelectedServer(updatedSelectedServer);
      }
    }
  };

  return (
      <div className="mcp-client-app">
        <McpHeader onToggleSidebar={toggleSidebar} sidebarCollapsed={sidebarCollapsed} />
        <div className="mcp-client-main">
          <div className={`mcp-client-sidebar${sidebarCollapsed ? ' collapsed' : ''}`}>
            <McpServerList
                onServerSelect={handleServerSelect}
                selectedServerId={selectedServer?.id}
                onServersUpdate={handleServersUpdate}
            />
          </div>
          <div className="mcp-client-chat">
            <ChatInterface 
              selectedServer={selectedServer} 
              toolsAcknowledged={toolsAcknowledged.has(selectedServer?.id)}
            />
          </div>
        </div>
        
        <ToolsModal
          visible={showToolsModal}
          onHide={handleToolsModalHide}
          tools={currentTools}
          serverName={selectedServer?.name || ''}
        />
      </div>
  );
};

export default McpClientApp;