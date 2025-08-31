#!/bin/bash

# Demo script for LLM Streaming Implementation
# Shows the difference between regular and streaming responses

echo "🚀 Demostración de Streaming LLM Responses"
echo "=========================================="
echo ""

# Check if server is running on different port
PORT=8084

echo "📋 Configuración de streaming actual:"
echo "- enable-streaming: true"
echo "- streaming-delay-ms: 50" 
echo "- streaming-word-chunk-size: 1"
echo ""

echo "🔧 Para probar manualmente:"
echo ""

echo "1. Iniciar aplicación:"
echo "   mvn spring-boot:run -Dserver.port=$PORT"
echo ""

echo "2. Endpoint tradicional (respuesta completa):"
echo "   curl -X POST http://localhost:$PORT/api/mcp/chat/send \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"message\":\"Explica qué es MCP\",\"serverId\":\"test\"}'"
echo ""

echo "3. Endpoint streaming (respuesta token por token):"
echo "   curl -X POST http://localhost:$PORT/api/mcp/chat/send/stream \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -H 'Accept: text/event-stream' \\"
echo "     -d '{\"message\":\"Explica qué es MCP\",\"serverId\":\"test\"}' \\"
echo "     --no-buffer"
echo ""

echo "4. Verificar estadísticas de streaming:"
echo "   curl http://localhost:$PORT/api/llm/cache/stats"
echo ""

echo "💡 La diferencia visual será evidente:"
echo "   - Endpoint tradicional: Respuesta aparece toda de una vez"
echo "   - Endpoint streaming: Respuesta aparece palabra por palabra"
echo ""

echo "🎛️ Para ajustar velocidad de streaming:"
echo "   application.yml:"
echo "   llm:"
echo "     performance:"
echo "       streaming-delay-ms: 30    # Más rápido"
echo "       streaming-delay-ms: 100   # Más lento"
echo ""

echo "✅ Streaming implementado exitosamente con:"
echo "   - ✅ Backward compatibility (API existente sin cambios)"
echo "   - ✅ Configurable delay y chunk size"
echo "   - ✅ Cache integration (respuestas cacheadas también streaming)"
echo "   - ✅ Error handling robusto"
echo "   - ✅ SSE endpoint para frontend"
echo ""

# Example JavaScript frontend code
echo "📱 Código JavaScript para frontend:"
cat << 'EOF'

// Consumir streaming endpoint desde frontend
const eventSource = new EventSource('/api/mcp/chat/send/stream');

eventSource.onmessage = function(event) {
    const token = event.data;
    document.getElementById('response').innerHTML += token;
};

eventSource.onerror = function(event) {
    console.log('Streaming error:', event);
    eventSource.close();
};

// O usando fetch con ReadableStream
fetch('/api/mcp/chat/send/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: "Hello", serverId: "test" })
})
.then(response => response.body.getReader())
.then(reader => {
    function read() {
        return reader.read().then(({ done, value }) => {
            if (done) return;
            
            const chunk = new TextDecoder().decode(value);
            const lines = chunk.split('\n');
            
            lines.forEach(line => {
                if (line.startsWith('data: ')) {
                    const token = line.substring(6);
                    document.getElementById('response').innerHTML += token;
                }
            });
            
            return read();
        });
    }
    return read();
});

EOF

echo ""
echo "🎯 Resultado: Experiencia de usuario más dinámica y engaging!"