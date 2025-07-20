import { useState } from 'react';
import { McpHeader } from '../components/McpHeader';
import { McpServerList } from '../components/McpServerList';
import { ChatInterface } from '../components/ChatInterface';
import '../styles/mcp-client-app.css';

const McpClientApp = () => {
  const [selectedServer, setSelectedServer] = useState(null);

  const handleServerSelect = (server) => {
    setSelectedServer(server);
  };

  return (
    <div className="mcp-client-app">
      <McpHeader />
      <div className="mcp-client-main">
        <div className="mcp-client-sidebar">
          <McpServerList 
            onServerSelect={handleServerSelect}
            selectedServerId={selectedServer?.id}
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