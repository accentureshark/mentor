import { useState, useEffect } from 'react';
import { McpHeader } from '../components/McpHeader';
import { McpServerList } from '../components/McpServerList';
import { ChatInterface } from '../components/ChatInterface';
import { ToolsModal } from '../components/ToolsModal';
import { Splitter, SplitterPanel } from 'primereact/splitter';
import { getServerTools } from '../services/toolService';
import '../styles/mcp-client-app.css';

const McpClientApp = () => {
  const [selectedServer, setSelectedServer] = useState(null);
  const [servers, setServers] = useState([]);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [sidebarSize, setSidebarSize] = useState(25);
  const [toolsAcknowledged, setToolsAcknowledged] = useState(new Set());
  const [showToolsModal, setShowToolsModal] = useState(false);
  const [currentTools, setCurrentTools] = useState([]);
  const toggleSidebar = () => setSidebarCollapsed(prev => !prev);

  const handleServerSelect = async (server) => {
    setSelectedServer(server);
    
    // If the server is connected and we haven't shown the tools yet
    if (server.status === 'CONNECTED' && !toolsAcknowledged.has(server.id)) {
      try {
        console.log('Fetching tools for first-time server selection:', server.name);
        const tools = await getServerTools(server.id);
        setCurrentTools(tools);
        setShowToolsModal(true);
      } catch (error) {
        console.error('Error fetching tools:', error);
        // If tools cannot be fetched, mark as acknowledged to allow the chat
        setToolsAcknowledged(prev => new Set(prev).add(server.id));
      }
    }
  };

  const handleToolsModalHide = () => {
    setShowToolsModal(false);
    if (selectedServer) {
      // Mark the tools as acknowledged for this server
      setToolsAcknowledged(prev => new Set(prev).add(selectedServer.id));
    }
  };

  const handleServersUpdate = (updatedServers) => {
    setServers(updatedServers);

    // If a server is selected, update it with the new data
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
          <Splitter style={{ width: '100%', height: '100%' }} gutterSize={8} onResizeEnd={(e) => setSidebarSize(e.sizes[0])}>
            <SplitterPanel size={sidebarCollapsed ? 0 : sidebarSize} minSize={10} className="mcp-client-sidebar" style={sidebarCollapsed ? { display: 'none' } : {}}>
              {!sidebarCollapsed && (
                <McpServerList
                  onServerSelect={handleServerSelect}
                  selectedServerId={selectedServer?.id}
                  onServersUpdate={handleServersUpdate}
                />
              )}
            </SplitterPanel>
            <SplitterPanel size={sidebarCollapsed ? 100 : 100 - sidebarSize} className="mcp-client-chat">
              <ChatInterface
                selectedServer={selectedServer}
                toolsAcknowledged={toolsAcknowledged.has(selectedServer?.id)}
              />
            </SplitterPanel>
          </Splitter>
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