// Servicio para obtener tools del backend
export async function getServerTools(serverId) {
  const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083';
  const url = `${BACKEND_URL}/api/mcp/tools/${serverId}`;
  console.log(`[toolService] Solicitando tools de ${url}`);
  const start = performance.now();
  try {
    const response = await fetch(url);
    if (!response.ok) {
      console.error(`[toolService] Error en la solicitud a ${url}: ${response.status}`);
      throw new Error('Failed to fetch tools');
    }
    const data = await response.json();
    const elapsed = Math.round(performance.now() - start);
    console.log(`[toolService] Tools recibidas de ${serverId} en ${elapsed}ms`, data);
    return data;
  } catch (error) {
    const elapsed = Math.round(performance.now() - start);
    console.error(`[toolService] Falló la obtención de tools para ${serverId} después de ${elapsed}ms`, error);
    throw error;
  }
}

