import { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { TabView, TabPanel } from 'primereact/tabview';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Tag } from 'primereact/tag';
import { Badge } from 'primereact/badge';
import { Accordion, AccordionTab } from 'primereact/accordion';
import { capabilityService } from '../services/capabilityService';
import { resourceService } from '../services/resourceService';
import { promptService } from '../services/promptService';

export const McpFeaturesPanel = ({ selectedServer }) => {
  const [capabilities, setCapabilities] = useState(null);
  const [resources, setResources] = useState([]);
  const [prompts, setPrompts] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (selectedServer && selectedServer.status === 'CONNECTED') {
      loadServerFeatures();
    }
  }, [selectedServer]);

  const loadServerFeatures = async () => {
    if (!selectedServer) return;
    
    setLoading(true);
    try {
      // Load capabilities
      const caps = await capabilityService.getCapabilities(selectedServer.id);
      setCapabilities(caps);

      // Load resources if supported
      if (caps.resources) {
        try {
          const res = await resourceService.getResources(selectedServer.id);
          setResources(res);
        } catch (error) {
          console.warn('Failed to load resources:', error);
          setResources([]);
        }
      }

      // Load prompts if supported
      if (caps.prompts) {
        try {
          const prom = await promptService.getPrompts(selectedServer.id);
          setPrompts(prom);
        } catch (error) {
          console.warn('Failed to load prompts:', error);
          setPrompts([]);
        }
      }
    } catch (error) {
      console.error('Error loading server features:', error);
    } finally {
      setLoading(false);
    }
  };

  const getCapabilityBadge = (capability) => {
    return capability ? 
      <Badge value="✓" severity="success" /> : 
      <Badge value="✗" severity="secondary" />;
  };

  const resourceActions = (rowData) => {
    return (
      <Button
        icon="pi pi-eye"
        className="p-button-text p-button-sm"
        onClick={() => viewResource(rowData.uri)}
        tooltip="View resource"
      />
    );
  };

  const promptActions = (rowData) => {
    return (
      <Button
        icon="pi pi-play"
        className="p-button-text p-button-sm"
        onClick={() => usePrompt(rowData.name)}
        tooltip="Use prompt"
      />
    );
  };

  const viewResource = async (uri) => {
    try {
      const content = await resourceService.readResource(selectedServer.id, uri);
      console.log('Resource content:', content);
      // TODO: Show in a dialog or modal
    } catch (error) {
      console.error('Error viewing resource:', error);
    }
  };

  const usePrompt = async (promptName) => {
    try {
      const prompt = await promptService.getPrompt(selectedServer.id, promptName);
      console.log('Prompt content:', prompt);
      // TODO: Insert into chat input or show in a dialog
    } catch (error) {
      console.error('Error using prompt:', error);
    }
  };

  if (!selectedServer || selectedServer.status !== 'CONNECTED') {
    return (
      <Card className="mcp-features-panel">
        <p>Please connect to a server to view MCP features.</p>
      </Card>
    );
  }

  if (loading) {
    return (
      <Card className="mcp-features-panel">
        <p>Loading MCP features...</p>
      </Card>
    );
  }

  return (
    <Card className="mcp-features-panel">
      <h3>MCP Features - {selectedServer.name}</h3>
      
      {capabilities && (
        <div className="capabilities-overview">
          <h4>Server Capabilities</h4>
          <div className="capabilities-grid">
            <div className="capability-item">
              Tools: {getCapabilityBadge(capabilities.tools)}
            </div>
            <div className="capability-item">
              Resources: {getCapabilityBadge(capabilities.resources)}
            </div>
            <div className="capability-item">
              Prompts: {getCapabilityBadge(capabilities.prompts)}
            </div>
            <div className="capability-item">
              Logging: {getCapabilityBadge(capabilities.logging)}
            </div>
            <div className="capability-item">
              Sampling: {getCapabilityBadge(capabilities.sampling)}
            </div>
          </div>
        </div>
      )}

      <TabView>
        {capabilities?.resources && (
          <TabPanel header={`Resources (${resources.length})`}>
            <DataTable value={resources} size="small" stripedRows>
              <Column field="name" header="Name" />
              <Column field="description" header="Description" />
              <Column field="uri" header="URI" />
              <Column field="mimeType" header="Type" />
              <Column body={resourceActions} header="Actions" style={{ width: '8rem' }} />
            </DataTable>
          </TabPanel>
        )}

        {capabilities?.prompts && (
          <TabPanel header={`Prompts (${prompts.length})`}>
            <DataTable value={prompts} size="small" stripedRows>
              <Column field="name" header="Name" />
              <Column field="description" header="Description" />
              <Column body={promptActions} header="Actions" style={{ width: '8rem' }} />
            </DataTable>
          </TabPanel>
        )}

        <TabPanel header="Capabilities">
          <Accordion>
            <AccordionTab header="Raw Capabilities Data">
              <pre>{JSON.stringify(capabilities, null, 2)}</pre>
            </AccordionTab>
          </Accordion>
        </TabPanel>
      </TabView>

      <div className="features-actions">
        <Button
          label="Refresh Features"
          icon="pi pi-refresh"
          onClick={loadServerFeatures}
          className="p-button-sm"
        />
      </div>
    </Card>
  );
};

export default McpFeaturesPanel;