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

  const loadServers = async () => {
    setLoading(true);
    try {
      console.log('Loading servers...');
      const data = await mcpServerService.getAllServers();
      console.log('Servers loaded:', data);

      data.forEach(server => {
        console.log(`Server: ${server.name}`);
        console.log(`  - Status: ${server.status}`);
        console.log(`  - ID: ${server.id}`);
        console.log(`  - URL: ${server.url}`);
        console.log(`  - Protocol: ${server.url?.split('://')[0] || 'unknown'}`);
        console.log('---');
      });

      setServers(data);

      if (onServersUpdate) {
        console.log('Updating parent component with servers:', data);
        onServersUpdate(data);
      }
    } catch (error) {
      console.error('Error loading servers:', error);
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

  useEffect(() => {
    loadServers();
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
      console.log('Attempting to connect to server:', server.id, server.name);
      const protocolInfo = getProtocolInfo(server.url);
      console.log(`Connecting via protocol: ${protocolInfo.protocol}, isRemote: ${protocolInfo.isRemote}`);

      const response = await mcpServerService.connectToServer(server.id);
      console.log('Connection response:', response);
      console.log('Connection attempt completed for:', server.name);

      // Solo mostrar warning si el protocolo no está implementado
      if (!protocolInfo.isImplemented) {
        const protocolText = protocolInfo.isRemote ? 'remote' : 'local';
        toast.current?.show({
          severity: 'warning',
          summary: 'Implementation Pending',
          detail: `${server.name} uses ${protocolInfo.protocol}:// protocol (${protocolText}) - Backend implementation required`,
          life: 5000,
        });
      } else {
        toast.current?.show({
          severity: 'success',
          summary: 'Connected',
          detail: `Successfully connected to ${server.name}`,
          life: 3000,
        });
      }

      // Recargar inmediatamente para actualizar el estado
      await loadServers();

    } catch (error) {
      console.error('Error connecting to server:', error);
      toast.current?.show({
        severity: 'error',
        summary: 'Error',
        detail: `Failed to connect to ${server.name}`,
        life: 3000,
      });
      // Recargar servidores incluso en caso de error para reflejar el estado actual
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
        <Button
          icon="pi pi-refresh"
          onClick={loadServers}
          loading={loading}
          className="p-button-text"
          tooltip="Refresh servers"
        />
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