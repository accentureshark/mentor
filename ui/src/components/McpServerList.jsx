import { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Badge } from 'primereact/badge';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { useRef } from 'react';
import { mcpServerService } from '../services/mcpServerService';
import '../styles/mcp-server-list.css';

export const McpServerList = ({ onServerSelect, selectedServerId, onServersUpdate }) => {
  const [servers, setServers] = useState([]);
  const [loading, setLoading] = useState(false);
  const toast = useRef(null);
  const wsRef = useRef(null);

  const loadServers = async () => {
    setLoading(true);
    try {
      console.log('🔄 Loading servers at:', new Date().toISOString());
      const data = await mcpServerService.getAllServers();
      console.log('✅ Servers loaded successfully:', data.length, 'servers');

      if (data.length > 0) {
        console.log('📋 Server details:');
        data.forEach((server, index) => {
          console.log(`${index + 1}. ${server.name}`);
          console.log(`   - Status: ${server.status}`);
          console.log(`   - ID: ${server.id}`);
          console.log(`   - URL: ${server.url}`);
          console.log(`   - Protocol: ${server.url?.split('://')[0] || 'unknown'}`);
        });
      } else {
        console.log('📭 No servers found');
      }

      setServers(data);

      if (onServersUpdate) {
        console.log('🔄 Updating parent component with', data.length, 'servers');
        onServersUpdate(data);
      }
    } catch (error) {
      console.error('❌ Error loading servers:', error);
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to load MCP servers',
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  };

  // Force reload backend configuration
  const handleForceReload = async () => {
    console.log('🔄 Force reload requested by user');
    
    toast.current?.show({
      severity: 'info',
      summary: 'Reloading Configuration...',
      detail: 'Forcing backend to reload server configuration',
      life: 3000,
    });
    
    try {
      // Call backend reload endpoint
      const reloadedServers = await mcpServerService.reloadConfiguration();
      console.log('✅ Backend configuration reloaded:', reloadedServers.length, 'servers');
      
      // Then refresh the frontend
      await loadServers();
      
      toast.current?.show({
        severity: 'success',
        summary: 'Configuration Reloaded',
        detail: `Backend reloaded with ${reloadedServers.length} servers`,
        life: 3000,
      });
    } catch (error) {
      console.error('❌ Force reload failed:', error);
      
      toast.current?.show({
        severity: 'error',
        summary: 'Reload Failed',
        detail: 'Could not reload backend configuration. Check backend connection.',
        life: 4000,
      });
    }
  };

  // Force refresh with user feedback
  const handleManualRefresh = async () => {
    console.log('🔄 Manual refresh requested by user');
    
    toast.current?.show({
      severity: 'info',
      summary: 'Refreshing...',
      detail: 'Loading latest server configuration',
      life: 2000,
    });
    
    try {
      await loadServers();
      
      // Get the current count after refresh for feedback
      const currentServers = await mcpServerService.getAllServers();
      
      toast.current?.show({
        severity: 'success',
        summary: 'Refreshed',
        detail: `Found ${currentServers.length} servers`,
        life: 2000,
      });
    } catch (error) {
      console.error('❌ Manual refresh failed:', error);
      
      toast.current?.show({
        severity: 'error',
        summary: 'Refresh Failed',
        detail: 'Could not load server list. Check backend connection.',
        life: 4000,
      });
    }
  };

  useEffect(() => {
    loadServers();
  }, []);

  // Set up WebSocket connection for real-time config updates
  useEffect(() => {
    const connectWebSocket = () => {
      try {
        console.log('🔌 Attempting WebSocket connection to ws://localhost:8083/ws/mcp-config');
        const ws = new WebSocket('ws://localhost:8083/ws/mcp-config');
        wsRef.current = ws;

        ws.onopen = () => {
          console.log('✅ WebSocket connected successfully for MCP config updates');
        };

        ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data);
            console.log('📨 Received WebSocket message:', message);
            
            if (message.type === 'config-reload') {
              console.log('🔄 Configuration reloaded, refreshing server list...');
              
              toast.current?.show({
                severity: 'info',
                summary: 'Configuration Updated',
                detail: 'MCP server configuration has been reloaded',
                life: 3000,
              });
              
              // Reload the server list with small delay to ensure backend is ready
              setTimeout(() => {
                console.log('🔄 Executing delayed server refresh after config reload');
                loadServers();
              }, 500);
            }
          } catch (error) {
            console.error('❌ Error parsing WebSocket message:', error);
          }
        };

        ws.onclose = (event) => {
          console.log('❌ WebSocket connection closed:', event.code, event.reason);
          console.log('🔄 Will attempt to reconnect in 3 seconds...');
          // Attempt to reconnect after 3 seconds
          setTimeout(connectWebSocket, 3000);
        };

        ws.onerror = (error) => {
          console.error('❌ WebSocket error:', error);
          console.log('This may indicate the backend WebSocket server is not running');
        };
      } catch (error) {
        console.error('❌ Failed to create WebSocket connection:', error);
        console.log('🔄 Will retry connection in 5 seconds...');
        // Retry connection after 5 seconds
        setTimeout(connectWebSocket, 5000);
      }
    };

    connectWebSocket();

    // Cleanup function
    return () => {
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, []);

  const getStatusSeverity = (status) => {
    switch (status) {
      case 'CONNECTED': return 'success';
      case 'DISCONNECTED': return 'warning';
      case 'ERROR': return 'danger';
      default: return 'info';
    }
  };

  const getProtocolInfo = (url) => {
    if (!url) return { protocol: 'unknown', isRemote: false, isImplemented: false };

    const protocol = url.split('://')[0];
    const isRemote = ['http', 'https', 'ws', 'wss', 'tcp'].includes(protocol);
    const isImplemented = ['stdio', 'http', 'https'].includes(protocol);

    return { protocol, isRemote, isImplemented };
  };

  const handleServerClick = (server) => {
    console.log('Server selected:', server);
    const protocolInfo = getProtocolInfo(server.url);
    console.log(`Selected server protocol: ${protocolInfo.protocol}, isRemote: ${protocolInfo.isRemote}, implemented: ${protocolInfo.isImplemented}`);
    onServerSelect(server);
  };

  const handleRemoveServer = (server) => {
    confirmDialog({
      message: `Are you sure you want to remove ${server.name}?`,
      header: 'Confirmation',
      icon: 'pi pi-exclamation-triangle',
      accept: async () => {
        try {
          console.log('Removing server:', server.id);
          await mcpServerService.removeServer(server.id);
          toast.current?.show({
            severity: 'success',
            summary: 'Success',
            detail: 'Server removed successfully',
            life: 3000,
          });
          loadServers();
        } catch (error) {
          console.error('Error removing server:', error);
          toast.current?.show({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to remove server',
            life: 3000,
          });
        }
      },
    });
  };

  const handleConnectServer = async (server) => {
    setLoading(true);
    try {
      console.log('🔌 Connecting to server:', server.id, server.name);
      const protocolInfo = getProtocolInfo(server.url);

      const response = await mcpServerService.connectToServer(server.id);
      console.log('🌐 Response from connectToServer:', response);

      // Evaluate connection status returned by the backend
      if (response.status === 'CONNECTED') {
        toast.current?.show({
          severity: 'success',
          summary: 'Connected',
          detail: `Successfully connected to ${server.name}`,
          life: 3000,
        });
      } else {
        const errorMsg = response.lastError
            ? `Reason: ${response.lastError}`
            : 'No additional information';

        toast.current?.show({
          severity: 'warn',
          summary: 'Connection Attempted',
          detail: `${server.name} responded with status: ${response.status}. ${errorMsg}`,
          life: 5000,
        });
      }

      // Advertencia por protocolo no implementado
      if (!protocolInfo.isImplemented) {
        const protocolText = protocolInfo.isRemote ? 'remote' : 'local';
        toast.current?.show({
          severity: 'info',
          summary: 'Protocol Warning',
          detail: `${server.name} uses ${protocolInfo.protocol} (${protocolText}) — Backend implementation may be required.`,
          life: 5000,
        });
      }

      // Recargar lista de servidores para reflejar estado actualizado
      await loadServers();

    } catch (error) {
      console.error('❌ Error connecting to server:', error);
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: `Failed to connect to ${server.name}`,
        life: 4000,
      });
      await loadServers();
    } finally {
      setLoading(false);
    }
  };


  const formatLastConnected = (timestamp) => {
    if (!timestamp) return 'Never';
    const diff = Date.now() - timestamp;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (minutes > 0) return `${minutes}m ago`;
    return 'Just now';
  };

  const renderServerCard = (server) => {
    const protocolInfo = getProtocolInfo(server.url);

    return (
      <Card
        key={server.id}
        className={`mcp-server-card ${selectedServerId === server.id ? 'selected' : ''}`}
        onClick={() => handleServerClick(server)}
      >
        <div className="mcp-server-header">
          <div className="mcp-server-info">
            <h4>{server.name}</h4>
            <div className="mcp-server-badges">
              <Badge
                value={server.status}
                severity={getStatusSeverity(server.status)}
              />
              <Badge
                value={protocolInfo.protocol}
                severity={protocolInfo.isRemote ? 'info' : 'secondary'}
              />
              {protocolInfo.isRemote && (
                <Badge value="remote" severity="warning" />
              )}
            </div>
          </div>
          <div className="mcp-server-actions">
            <Button
              icon="pi pi-link"
              className="p-button-text p-button-warning p-button-sm"
              onClick={(e) => {
                e.stopPropagation();
                handleConnectServer(server);
              }}
              tooltip={protocolInfo.isImplemented ?
                "Connect to server" :
                `${protocolInfo.protocol} protocol not yet implemented`}
              disabled={loading || server.status === 'CONNECTED'}
            />
            <Button
              icon="pi pi-times"
              className="p-button-text p-button-danger p-button-sm"
              onClick={(e) => {
                e.stopPropagation();
                handleRemoveServer(server);
              }}
              tooltip="Remove server"
            />
          </div>
        </div>

        <p className="mcp-server-description">{server.description}</p>

        <div className="mcp-server-details">
          <small className="mcp-server-url">{server.url}</small>
          <small className="mcp-server-last-connected">
            Last: {formatLastConnected(server.lastConnected)}
          </small>
          {server.status === 'ERROR' && server.lastError && (
            <small className="mcp-server-error" style={{ color: '#f44336', fontStyle: 'italic' }}>
              Error: {server.lastError}
            </small>
          )}
          {!protocolInfo.isImplemented && (
            <small className="mcp-server-notice" style={{ color: '#ff9800', fontStyle: 'italic' }}>
              {protocolInfo.isRemote ? 'Remote protocol' : 'Local protocol'} - Backend implementation required
            </small>
          )}
        </div>
      </Card>
    );
  };

  return (
    <div className="mcp-server-list">
      <Toast ref={toast} />
      <ConfirmDialog />

      <div className="mcp-server-list-header">
        <h3>MCP Servers</h3>
        <div className="mcp-server-list-actions">
          <Button
            icon="pi pi-refresh"
            onClick={handleManualRefresh}
            loading={loading}
            className="p-button-text"
            tooltip="Refresh server list"
          />
          <Button
            icon="pi pi-reload"
            onClick={handleForceReload}
            loading={loading}
            className="p-button-text p-button-warning"
            tooltip="Force reload backend configuration"
          />
        </div>
      </div>

      <div className="mcp-server-list-content">
        {servers.map(renderServerCard)}

        {servers.length === 0 && !loading && (
          <div className="mcp-server-empty">
            <i className="pi pi-inbox" style={{ fontSize: '2rem', color: '#ccc' }} />
            <p>No MCP servers registered</p>
          </div>
        )}
      </div>
    </div>
  );
};