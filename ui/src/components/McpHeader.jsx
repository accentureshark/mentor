import '../styles/header.css';
import logo from '../assets/shark-ia.png';

export const McpHeader = () => {
  return (
    <header className="header-container">
      <div className="header-logo">
        <img src={logo} alt="Logo" className="logo" height={80} width={100} />
        <h1 className="header-title">MCP Client - Model Context Protocol Interface</h1>
      </div>
      <div className="header-info">
        <span className="header-info-text">No authentication required</span>
      </div>
    </header>
  );
};