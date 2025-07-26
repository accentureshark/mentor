import { useState, useEffect } from 'react';
import { McpHeader } from '../components/McpHeader';
import { McpServerList } from '../components/McpServerList';
import { ChatInterface } from '../components/ChatInterface';
import '../styles/mcp-client-app.css';

const McpClientApp = () => {
  const [selectedServer, setSelectedServer] = useState(null);
  const [servers, setServers] = useState([]);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const toggleSidebar = () => setSidebarCollapsed(prev => !prev);

  const handleServerSelect = (server) => {
    setSelectedServer(server);
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
            <ChatInterface selectedServer={selectedServer} />
          </div>
        </div>
      </div>
  );
};

export default McpClientApp;