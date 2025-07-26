import '../styles/header.css';
import logo from '../assets/shark-ia.png';
import { Button } from 'primereact/button';

export const McpHeader = ({ onToggleSidebar, sidebarCollapsed }) => {  return (
    <header className="header-container">
      <div className="header-logo">
          <Button
              icon="pi pi-bars"
              className="p-button-text sidebar-toggle-button"
              onClick={onToggleSidebar}
              aria-label="Toggle sidebar"
          />
        <img src={logo} alt="Logo" className="logo" height={80} width={100} />
        <h1 className="header-title">MCP Client - Model Context Protocol Interface</h1>
      </div>
      <div className="header-info">
        <span className="header-info-text">No authentication required</span>
      </div>
    </header>
  );
};