import { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Badge } from 'primereact/badge';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { useRef } from 'react';
import { mcpServerService } from '../services/mcpServerService';
import '../styles/mcp-server-list.css';

export const McpServerList = ({ onServerSelect, selectedServerId }) => {
  const [servers, setServers] = useState([]);
  const [loading, setLoading] = useState(false);
  const toast = useRef(null);

  const loadServers = async () => {
    setLoading(true);
    try {
      const data = await mcpServerService.getAllServers();
      setServers(data);
    } catch (error) {
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

  const handleServerClick = (server) => {
    onServerSelect(server);
  };

  const handleRemoveServer = (server) => {
    confirmDialog({
      message: `Are you sure you want to remove ${server.name}?`,
      header: 'Confirmation',
      icon: 'pi pi-exclamation-triangle',
      accept: async () => {
        try {
          await mcpServerService.removeServer(server.id);
          toast.current?.show({
            severity: 'success',
            summary: 'Success',
            detail: 'Server removed successfully',
            life: 3000,
          });
          loadServers();
        } catch (error) {
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
        {servers.map((server) => (
          <Card
            key={server.id}
            className={`mcp-server-card ${selectedServerId === server.id ? 'selected' : ''}`}
            onClick={() => handleServerClick(server)}
          >
            <div className="mcp-server-header">
              <div className="mcp-server-info">
                <h4>{server.name}</h4>
                <Badge
                  value={server.status}
                  severity={getStatusSeverity(server.status)}
                  className="mcp-server-status"
                />
              </div>
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
            
            <p className="mcp-server-description">{server.description}</p>
            
            <div className="mcp-server-details">
              <small className="mcp-server-url">{server.url}</small>
              <small className="mcp-server-last-connected">
                Last: {formatLastConnected(server.lastConnected)}
              </small>
            </div>
          </Card>
        ))}

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