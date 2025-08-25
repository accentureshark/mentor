# Mentor - Universal Model Context Protocol Interface

Este proyecto es un cliente universal MCP (Model Context Protocol) que puede conectarse a múltiples servidores MCP, incluyendo el servidor local Melian y varios servidores MCP públicos.

## Tecnologías Utilizadas

- **Backend:**
  - Java 17+
  - Spring Boot 3.x
  - Maven
  - REST API
  - Integración con servidores MCP vía HTTP/JSON
  - Interacción con modelos LLM (Llama/Gemma vía Ollama)
- **Frontend:**
  - React 18
  - Vite
  - PrimeReact
  - Comunicación con backend vía REST
- **Contenedores:**
  - Docker y Docker Compose (opcional para despliegue)
  - Ollama para servir modelos LLM (Llama, Gemma, etc.)

## Arquitectura

- **Backend** (`backend/`):
  - Aplicación Spring Boot que expone APIs REST para la gestión de servidores MCP y operaciones de chat.
  - Implementa el protocolo MCP (Model Context Protocol) para interactuar con servidores MCP, permitiendo descubrimiento de herramientas, ejecución de acciones y consulta de metadatos.
  - Gestiona múltiples servidores MCP configurables mediante `mcp-servers.json`.
  - Traduce las solicitudes del usuario a llamadas compatibles con el protocolo MCP y procesa las respuestas para la UI.
  - Integra con Ollama para interactuar con modelos LLM (como Llama y Gemma), permitiendo respuestas generadas por IA en el flujo conversacional.
- **UI** (`ui/`):
  - Aplicación React con componentes PrimeReact para la interfaz de usuario.
  - Permite seleccionar servidores MCP, enviar mensajes y explorar herramientas y metadatos expuestos por los servidores MCP.

## Cumplimiento del Protocolo MCP

- El backend implementa el protocolo MCP (Model Context Protocol) para la interoperabilidad con servidores MCP.
- Soporta descubrimiento dinámico de herramientas (tools), ejecución de acciones, consulta de schemas, tablas y columnas, y manejo de errores estándar MCP.
- Todas las interacciones con servidores MCP se realizan usando HTTP y mensajes JSON siguiendo las especificaciones del protocolo MCP.
- El backend actúa como traductor entre la UI y los servidores MCP, asegurando compatibilidad y extensibilidad.
- La integración con LLM (Ollama) permite enriquecer las respuestas y el procesamiento de lenguaje natural, manteniendo la compatibilidad con el protocolo MCP.

## Quick Start

### Usando Make (Recomendado)

```bash
# Desde el directorio raíz, compila y ejecuta ambos componentes del cliente
make run-client
```

Esto realizará:
- Compilación del backend (Spring Boot)
- Compilación de la UI (React + Vite)
- Inicio del backend en http://localhost:8083
- Inicio de la UI en http://localhost:5174

### Configuración Manual

```bash
# Compilar y ejecutar backend
cd mcp-client/backend
mvn spring-boot:run

# En otra terminal, compilar y ejecutar UI
cd mcp-client/ui
npm install
npm run dev
```

## Despliegue con Docker Compose

El proyecto incluye un archivo `docker-compose.yml` que permite levantar servicios como Ollama (para LLMs) y, opcionalmente, el backend y frontend de Mentor. Ollama se utiliza para servir modelos como Llama y Gemma, facilitando la integración de IA conversacional en el flujo MCP.

## Available MCP Servers

El cliente viene preconfigurado con varios servidores MCP:

1. **Melian MCP Server (Local)** - Servidor principal Melian para datos de películas
2. **GitHub MCP Server** - Acceso a repositorios e issues de GitHub
3. **File System MCP Server** - Operaciones sobre el sistema de archivos local
4. **Brave Search MCP Server** - Búsqueda web
5. **SQLite MCP Server** - Consultas a bases de datos SQLite

### Configuración en IDE

El backend carga la lista de servidores MCP desde
`backend/src/main/resources/mcp-servers.json`. Este archivo puede usarse
directamente con los plugins MCP client de VSCode o IntelliJ para mantener la configuración sincronizada.

## Uso

1. Abre http://localhost:5174 en tu navegador
2. Selecciona un servidor MCP desde la barra lateral
3. Comienza a chatear con el servidor seleccionado
4. El asistente responde estrictamente usando el contexto retornado por el servidor MCP
   y evita inferir o agregar información nueva

## Make Targets

Desde el directorio raíz:

- `make build-client` - Compila backend y UI
- `make run-client` - Ejecuta ambos componentes
- `make run-client-backend` - Solo backend
- `make run-client-ui` - Solo UI
- `make clean-client` - Limpia artefactos

## Requisitos

- Java 17+
- Node.js 18+
- Maven 3.8+
- Docker (para uso de Ollama y despliegue completo)
