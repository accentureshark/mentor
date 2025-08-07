// Servicio para obtener tools del backend
export async function getServerTools(serverId) {
  const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083';
  const response = await fetch(`${BACKEND_URL}/api/mcp/tools/${serverId}`);
  if (!response.ok) throw new Error('Failed to fetch tools');
  return await response.json();
}

