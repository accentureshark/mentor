import { Dialog } from 'primereact/dialog';
import { Button } from 'primereact/button';
import { Card } from 'primereact/card';
import '../styles/tools-modal.css';

export const ToolsModal = ({ visible, onHide, tools, serverName }) => {
  const handleContinue = () => {
    onHide();
  };

  const footer = (
    <div>
      <Button
        label="Continuar al Chat"
        icon="pi pi-check"
        onClick={handleContinue}
        className="p-button-success"
      />
    </div>
  );

  return (
    <Dialog
      visible={visible}
      onHide={onHide}
      header={`Herramientas disponibles en ${serverName}`}
      footer={footer}
      style={{ width: '70vw', maxWidth: '800px' }}
      maximizable
      modal
      blockScroll
    >
      <div className="tools-modal-content">
        <p className="tools-modal-description">
          Este servidor MCP expone las siguientes herramientas que podrás usar en el chat:
        </p>
        
        {tools.length === 0 ? (
          <div className="tools-empty-state">
            <i className="pi pi-info-circle" style={{ fontSize: '2rem', color: '#ccc' }} />
            <p>No se encontraron herramientas disponibles en este servidor.</p>
          </div>
        ) : (
          <div className="tools-grid">
            {tools.map((tool, index) => (
              <Card key={index} className="tool-card">
                <div className="tool-header">
                  <h4 className="tool-name">
                    <i className="pi pi-wrench" style={{ marginRight: '0.5rem' }} />
                    {tool.name}
                  </h4>
                </div>
                <div className="tool-content">
                  <p className="tool-description">
                    {tool.description || 'Sin descripción disponible'}
                  </p>
                  {tool.inputSchema && tool.inputSchema.properties && (
                    <div className="tool-parameters">
                      <strong>Parámetros:</strong>
                      <ul>
                        {Object.entries(tool.inputSchema.properties).map(([paramName, paramInfo]) => (
                          <li key={paramName}>
                            <code>{paramName}</code>
                            {paramInfo.description && `: ${paramInfo.description}`}
                            {paramInfo.type && (
                              <span className="param-type"> ({paramInfo.type})</span>
                            )}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>
    </Dialog>
  );
};

export default ToolsModal;