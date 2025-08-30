# Universal MCP Client Configuration Documentation

## Overview
This MCP client has been refactored to be universal and configurable, removing all hardcoded values that would prevent it from being used in different environments and locales.

## Key Changes Made

### 1. Removed Hardcoded Values
- **GitHub Token**: Removed hardcoded `github_pat_*` from mcp.json, now uses `${GITHUB_PERSONAL_ACCESS_TOKEN}` environment variable
- **Language**: Removed hardcoded Spanish requirement, now configurable via `UI_LOCALE` and `LLM_DEFAULT_LOCALE`
- **URLs**: Removed hardcoded localhost URLs, now configurable via environment variables
- **UI Messages**: Removed hardcoded emojis and messages, now configurable through properties
- **LLM Parameters**: Removed hardcoded temperature (0.7) and timeout (2 minutes), now configurable

### 2. Internationalization Support
- Added `I18nService` for handling localized messages
- Added `UiProperties` for configurable UI elements and messages
- Added support for multiple locales through environment variables
- Configurable emoji prefixes and message templates

### 3. Environment Variable Support
All sensitive and environment-specific values are now configurable:

```bash
# LLM Configuration
LLM_PROVIDER=ollama|openai|anthropic|...
LLM_MODEL=your_model_name
LLM_BASE_URL=http://your-llm-endpoint
LLM_API_KEY=your_api_key
LLM_TEMPERATURE=0.7
LLM_TIMEOUT_MINUTES=2

# UI Configuration
UI_LOCALE=en|es|fr|de|...
UI_SUCCESS_PREFIX=✅
UI_ERROR_PREFIX=❌
# ... more UI customization

# MCP Server Environment
GITHUB_PERSONAL_ACCESS_TOKEN=your_token
# ... other server-specific variables
```

### 4. Configurable MCP Server Definitions
The `mcp.json` now supports environment variable substitution and additional configuration:

```json
{
  "servers": [
    {
      "id": "github-stdio",
      "name": "GitHub MCP Server",
      "url": "stdio://docker run -i --rm -e GITHUB_PERSONAL_ACCESS_TOKEN=${GITHUB_PERSONAL_ACCESS_TOKEN} ...",
      "environment": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "${GITHUB_PERSONAL_ACCESS_TOKEN}",
        "GITHUB_DYNAMIC_TOOLSETS": "1"
      }
    }
  ]
}
```

## MCP Client Compliance

This implementation now follows universal MCP client best practices:

### Protocol Support
- ✅ JSON-RPC 2.0 communication
- ✅ HTTP and STDIO transport protocols
- ✅ Dynamic tool discovery and execution
- ✅ Resource access capabilities
- ✅ Prompt template support

### Client Features
- ✅ Server lifecycle management
- ✅ Tool parameter validation
- ✅ Error handling and logging
- ✅ Configuration-driven server setup
- ✅ Environment-specific deployments

### Extensibility
- ✅ Pluggable LLM providers
- ✅ Configurable UI components
- ✅ Locale-specific message templates
- ✅ Custom emoji and formatting
- ✅ Server-specific environment variables

## Usage Examples

### English Deployment
```bash
cp .env.example .env
# Edit .env with your configuration
export $(cat .env | xargs)
./start-dev.sh
```

### Spanish Deployment
```bash
cp .env.spanish .env
# Edit .env with your configuration
export $(cat .env | xargs)
./start-dev.sh
```

### Custom Deployment
```bash
# Create your own .env file
LLM_PROVIDER=openai
LLM_MODEL=gpt-4
LLM_API_KEY=your_openai_key
UI_LOCALE=fr
UI_MSG_SUCCESS_CONTACT=Contacté avec succès %s...
# ... more customization
```

## Migration from Hardcoded Version

If migrating from the previous hardcoded version:

1. **Update environment**: Create `.env` file from `.env.example`
2. **Set GitHub token**: `GITHUB_PERSONAL_ACCESS_TOKEN=your_token`
3. **Choose locale**: `UI_LOCALE=es` for Spanish or `UI_LOCALE=en` for English
4. **Configure LLM**: Set your preferred LLM provider and model
5. **Restart application**: The new configuration will be loaded automatically

## Testing the Changes

The application should now:
- ✅ Start without hardcoded values
- ✅ Support multiple languages through configuration
- ✅ Work with different LLM providers
- ✅ Allow custom UI messages and emojis
- ✅ Support environment-specific server configurations

This makes the MCP client truly universal and suitable for deployment in any environment.