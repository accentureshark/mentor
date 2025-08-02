// Servicio para obtener tools del backend
export async function getServerTools(serverId) {
  const response = await fetch(`/api/mcp/tools/${serverId}`);
  if (!response.ok) throw new Error('Failed to fetch tools');
  return await response.json();
}

