import { normalizeBaseUrl } from './urlUtils';

// Servicio para obtener tools del backend
export async function getServerTools(serverId) {
  const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8083';
  const url = `${normalizeBaseUrl(BACKEND_URL)}/tools/${serverId}`;
  console.log(`[toolService] Requesting tools from ${url}`);
  const start = performance.now();
  try {
    const response = await fetch(url);
    if (!response.ok) {
      console.error(`[toolService] Request error to ${url}: ${response.status}`);
      throw new Error('Failed to fetch tools');
    }
    const data = await response.json();
    const elapsed = Math.round(performance.now() - start);
    console.log(`[toolService] Tools received from ${serverId} in ${elapsed}ms`, data);
    return data;
  } catch (error) {
    const elapsed = Math.round(performance.now() - start);
    console.error(`[toolService] Failed to fetch tools for ${serverId} after ${elapsed}ms`, error);
    throw error;
  }
}
