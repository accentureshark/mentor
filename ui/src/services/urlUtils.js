// src/services/urlUtils.js

export function normalizeBaseUrl(url) {
  // Elimina cualquier ocurrencia repetida de /api/mcp al final
  let trimmed = url.replace(/\/+$/, '');
  trimmed = trimmed.replace(/(\/api\/mcp)+$/, '');
  return `${trimmed}/api/mcp`;
}
