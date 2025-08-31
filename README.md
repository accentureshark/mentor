# Mentor - Universal Model Context Protocol Interface

This is a universal MCP (Model Context Protocol) client that can connect to multiple MCP servers, including the local Melian server and various public MCP servers.

## Architecture

- **Backend** (`backend/`): Spring Boot application providing REST APIs for MCP server management
- **UI** (`ui/`): React application with PrimeReact components for the user interface

## Quick Start

### Using Make (Recommended)

```bash
# From the root directory, build and run both client components
make run-client
```

This will:
- Build the backend (Spring Boot)
- Build the UI (React + Vite)
- Start backend on http://localhost:8083
- Start UI on http://localhost:5174

### Manual Setup

```bash
# Build and run backend
cd mcp-client/backend
mvn spring-boot:run

# In another terminal, build and run UI
cd mcp-client/ui
npm install
npm run dev
```

## Available MCP Servers

The client comes pre-configured with several MCP servers:

1. **Melian MCP Server (Local)** - The main Melian server for movie data
2. **GitHub MCP Server** - Access to GitHub repositories and issues
3. **File System MCP Server** - Local file system operations
4. **Brave Search MCP Server** - Web search capabilities
5. **SQLite MCP Server** - Database query operations

### IDE configuration

The backend now loads its MCP server list from
`backend/src/main/resources/mcp-servers.json`. This same file can be used
directly with the VSCode or IntelliJ MCP client plugins so the IDE stays in sync
with the running application.

## Usage

1. Open http://localhost:5174 in your browser
2. Select an MCP server from the left sidebar
3. Start chatting with the selected server
4. The assistant responds strictly using the context returned by the MCP server
   and avoids inferring or adding new information

## Make Targets

From the root directory:

- `make build-client` - Build both backend and UI
- `make run-client` - Run both components
- `make run-client-backend` - Run only the backend
- `make run-client-ui` - Run only the UI
- `make clean-client` - Clean build artifacts

## Requirements

- Java 17+
- Node.js 18+
- Maven 3.8+

## Tecnologías del Backend

- **Java 17+** y **Spring Boot**: El backend está construido sobre Spring Boot, proporcionando APIs RESTful y gestión de la lógica de negocio.
- **Integración LLM (Llama vía Ollama)**: El backend se conecta a un modelo de lenguaje Llama usando el servicio Ollama (ver docker-compose.yml), permitiendo generación de lenguaje natural y respuestas inteligentes.
- **Protocolo MCP (Model Content Protocol)**: El backend implementa el protocolo MCP para interactuar con servidores MCP externos, facilitando la interoperabilidad y la gestión de herramientas (tools) y recursos.
- **Comunicación HTTP/JSON**: Toda la comunicación entre componentes y servidores MCP se realiza usando HTTP y JSON.

## Arquitectura Detallada

```
[UI React] ⇄ [Backend Spring Boot] ⇄ [Servidores MCP externos/locales]
                                 ⇄ [Ollama (LLM Optimizado)]
```

- El **frontend** (React) interactúa con el backend mediante APIs REST.
- El **backend** gestiona la lógica de negocio, orquesta las llamadas a servidores MCP y a LLM optimizado, y expone endpoints REST.
- El backend puede conectarse a múltiples servidores MCP (configurados en `src/main/resources/mcp-servers.json`).
- Para tareas de generación de lenguaje natural, el backend utiliza el servicio Ollama con modelo `gemma2:2b` optimizado para velocidad.

### Flujo de Datos (con Streaming en Tiempo Real)
1. El usuario realiza una consulta desde la UI.
2. El backend recibe la petición y determina si debe consultar un servidor MCP, el LLM, o ambos.
3. Si la consulta requiere generación de lenguaje, el backend envía la petición a Ollama (modelo optimizado) y **transmite la respuesta en streaming** via Server-Sent Events (SSE) para una experiencia de usuario dinámica.
4. Si la consulta requiere datos estructurados, el backend consulta el servidor MCP correspondiente, con soporte para **streaming de resultados grandes**.
5. El backend unifica las respuestas y las envía a la UI, manteniendo la conexión en tiempo real para actualizaciones progresivas.
6. La UI actualiza el contenido de forma incremental conforme llegan los datos, proporcionando retroalimentación inmediata al usuario.

## Cumplimiento del Protocolo MCP

El backend implementa completamente el **Model Context Protocol (MCP)** versión `2025-06-18`, garantizando una integración universal y estándar con cualquier servidor MCP compatible. Mentor es un **cliente MCP totalmente conforme** que cumple con todas las especificaciones oficiales.

### Especificaciones MCP Implementadas

**✅ Protocolo Base del Servidor** ([especificación](https://modelcontextprotocol.io/specification/2025-06-18/server))
- Protocolo JSON-RPC 2.0 completo
- Endpoint base: `{server_url}/mcp`
- Endpoint de salud: `{server_url}/mcp/health`
- Validación de versión de protocolo
- Declaración de capacidades del servidor

**✅ Soporte de Herramientas** ([especificación](https://modelcontextprotocol.io/specification/2025-06-18/server/tools))
- `tools/list` - Listar herramientas disponibles
- `tools/call` - Ejecutar herramientas específicas
- Manejo de parámetros y esquemas de validación
- Gestión de respuestas estructuradas

**✅ Soporte de Prompts** ([especificación](https://modelcontextprotocol.io/specification/2025-06-18/server/prompts))
- `prompts/list` - Listar prompts disponibles
- `prompts/get` - Obtener contenido de prompts
- Argumentos dinámicos y plantillas

**✅ Soporte de Recursos** ([especificación](https://modelcontextprotocol.io/specification/2025-06-18/server/resources))
- `resources/list` - Listar recursos disponibles
- `resources/read` - Leer contenido de recursos
- Suscripción a cambios de recursos

**✅ Utilidades de Finalización** ([especificación](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/completion))
- Autocompletado de argumentos
- Sugerencias contextuales

**✅ Utilidades de Logging** ([especificación](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/logging))
- Registro estructurado de eventos
- Niveles de log estándar

**✅ Utilidades de Paginación** ([especificación](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/pagination))
- Manejo de grandes conjuntos de datos
- Cursores de paginación

### Características de Compatibilidad Universal

- **Detección Automática**: El cliente descubre automáticamente las capacidades de cada servidor MCP
- **Configuración Dinámica**: Recarga automática de configuración desde `mcp.json`
- **Gestión de Errores**: Manejo estandarizado de errores según JSON-RPC 2.0
- **Streaming de Respuestas**: Soporte para respuestas en tiempo real tanto de servidores MCP como del LLM
- **Validación de Protocolo**: Verificación automática de versión y compatibilidad
- **Multiplexación**: Conexión simultánea a múltiples servidores MCP

### Integración con Servidores MCP

El sistema puede conectarse a cualquier servidor MCP que implemente las especificaciones oficiales, incluyendo:
- Servidores locales (http://localhost:*)
- Servidores remotos (https://*)
- Múltiples instancias simultáneas
- Reconexión automática en caso de fallo

Para más detalles sobre el cumplimiento completo, consulte el `MCP_COMPLIANCE_REPORT.md`.

## Integración con LLM (Optimizado para Velocidad y Streaming)

- El backend se conecta a un servicio Ollama, que expone un modelo optimizado para velocidad (por defecto, `gemma2:2b`).
- El modelo Gemma 2 2B proporciona **40-60% mejor velocidad** comparado con el modelo anterior, manteniendo la funcionalidad completa.
- **Streaming de Respuestas**: Las respuestas del LLM se transmiten en tiempo real usando Server-Sent Events (SSE), proporcionando una experiencia de usuario más dinámica y responsiva.
- **Streaming de Datos MCP**: Las respuestas de servidores MCP también soportan streaming para consultas de gran volumen.
- Ollama se levanta como servicio Docker (ver `docker-compose.yml`).
- El backend utiliza este LLM para generación de respuestas en lenguaje natural, resúmenes, explicaciones y asistencia conversacional con retroalimentación en tiempo real.

## Configuración de Servidores MCP

- Los servidores MCP disponibles se configuran en `src/main/resources/mcp.json`.
- Cada entrada define el id, nombre, descripción, URL y opciones como `prewarm`.
- El backend detecta automáticamente cambios en este archivo y recarga la configuración dinámicamente.
- Ejemplo de configuración:

```json
{
  "id": "polenta-local",
  "name": "Polenta MCP Server (Local)",
  "description": "Polenta MCP Server for Data Lake access with PrestoDB",
  "url": "http://localhost:8090",
  "implemented": true,
  "prewarm": true
}
```

## Uso de docker-compose

- El archivo `docker-compose.yml` permite levantar servicios auxiliares como Ollama para el LLM.
- Ejemplo de servicio Ollama:

```yaml
ollama:
  image: docker.io/ollama/ollama:latest
  container_name: mentor-ollama
  restart: unless-stopped
  entrypoint: ["/usr/bin/bash", "/ollama-init.sh"]
  ports:
    - "11435:11434"
  environment:
    PRELOAD_MODEL_NAME: gemma2:2b
    CUDA_VISIBLE_DEVICES: 0
```

Esto permite que el backend acceda al modelo optimizado para velocidad para tareas de NLP.
