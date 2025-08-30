import { useState, useEffect } from 'react';
import { McpHeader } from '../components/McpHeader';
import { McpServerList } from '../components/McpServerList';
import { ChatInterface } from '../components/ChatInterface';
import { ToolsModal } from '../components/ToolsModal';
import McpFeaturesPanel from '../components/McpFeaturesPanel';
import { Splitter, SplitterPanel } from 'primereact/splitter';
import { TabView, TabPanel } from 'primereact/tabview';
import { getServerTools } from '../services/toolService';
import '../styles/mcp-client-app.css';
import '../styles/mcp-features.css';

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
    try {
      console.log('Fetching tools for server selection:', server.name);
      const tools = await getServerTools(server.id);
      setCurrentTools(tools);
      setShowToolsModal(true);
      // Si la obtención de tools es exitosa, actualizar el estado del servidor a CONNECTED en el backend
      if (tools && tools.length > 0) {
        const updatedServer = { ...server, status: 'CONNECTED', lastError: '' };
        setSelectedServer(updatedServer);
        setServers(prevServers => prevServers.map(s => s.id === server.id ? updatedServer : s));
        // Notificar al backend que el servidor está conectado
        try {
          await import('../services/mcpServerService').then(({ mcpServerService }) =>
            mcpServerService.updateServerStatus(server.id, 'CONNECTED', '')
          );
        } catch (err) {
          console.error('No se pudo actualizar el estado del servidor en el backend:', err);
        }
      }
    } catch (error) {
      console.error('Error fetching tools:', error);
      setToolsAcknowledged(prev => new Set(prev).add(server.id));
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
              <TabView>
                <TabPanel header="Chat">
                  <ChatInterface
                    selectedServer={selectedServer}
                    toolsAcknowledged={toolsAcknowledged.has(selectedServer?.id)}
                  />
                </TabPanel>
                <TabPanel header="MCP Features">
                  <McpFeaturesPanel selectedServer={selectedServer} />
                </TabPanel>
              </TabView>
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