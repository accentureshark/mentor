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
                                 ⇄ [Ollama (LLM Llama)]
```

- El **frontend** (React) interactúa con el backend mediante APIs REST.
- El **backend** gestiona la lógica de negocio, orquesta las llamadas a servidores MCP y a LLM (Llama), y expone endpoints REST.
- El backend puede conectarse a múltiples servidores MCP (configurados en `src/main/resources/mcp-servers.json`).
- Para tareas de generación de lenguaje natural, el backend utiliza el servicio Ollama, que expone un modelo Llama (configurable en docker-compose).

### Flujo de Datos
1. El usuario realiza una consulta desde la UI.
2. El backend recibe la petición y determina si debe consultar un servidor MCP, el LLM, o ambos.
3. Si la consulta requiere generación de lenguaje, el backend envía la petición a Ollama (Llama) y procesa la respuesta.
4. Si la consulta requiere datos estructurados, el backend consulta el servidor MCP correspondiente.
5. El backend unifica la respuesta y la envía a la UI.

## Cumplimiento del Protocolo MCP

El backend implementa el Model Content Protocol (MCP), permitiendo:
- Descubrir y listar herramientas (tools) disponibles en cada servidor MCP.
- Ejecutar herramientas específicas enviando requests estructurados según el protocolo MCP.
- Gestionar respuestas y errores de forma estandarizada.
- Configurar múltiples servidores MCP en `mcp-servers.json`.

## Integración con LLM (Llama)

- El backend se conecta a un servicio Ollama, que expone un modelo Llama (por defecto, `hf.co/unsloth/gemma-3n-E4B-it-GGUF:Q4_K_XL`).
- Ollama se levanta como servicio Docker (ver `docker-compose.yml`).
- El backend utiliza este LLM para generación de respuestas en lenguaje natural, resúmenes, explicaciones y asistencia conversacional.

## Configuración de Servidores MCP

- Los servidores MCP disponibles se configuran en `src/main/resources/mcp-servers.json`.
- Cada entrada define el id, nombre, descripción, URL y opciones como `prewarm`.
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
    PRELOAD_MODEL_NAME: hf.co/unsloth/gemma-3n-E4B-it-GGUF:Q4_K_XL
    CUDA_VISIBLE_DEVICES: 0
```

Esto permite que el backend acceda al modelo Llama para tareas de NLP.
