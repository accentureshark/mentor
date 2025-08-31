# Intent Keywords Configuration System

This document describes the new YAML-based intent keyword detection system that replaces hardcoded intent detection logic in the mentor repository.

## Overview

The system uses a YAML configuration file to define keywords for different user intents in Spanish and English. This makes the system easily extensible - new intents or keywords can be added by simply editing the configuration file without any code changes.

## Configuration Location

The intent keywords are configured in `backend/src/main/resources/application.yml` under the `intent.keywords` section.

## Configuration Structure

### Intents

Each intent has the following structure:

```yaml
intent:
  keywords:
    intents:
      INTENT_NAME:
        keywords:
          spanish:
            - "keyword1"
            - "phrase with multiple words"
          english:
            - "english_keyword1"
            - "english phrase"
        toolNames:
          - "tool_name_1"
          - "tool_name_2"
        description: "Description of what this intent does"
```

### Available Intents

1. **LIST_SCHEMAS**: List database schemas
   - Spanish: esquemas, cuales son los esquemas, listar esquemas, etc.
   - English: schemas, list schemas, show schemas, etc.
   - Tool: list_schemas

2. **LIST_TABLES**: List database tables
   - Spanish: tablas, que tablas hay, mostrar tablas, etc.
   - English: tables, list tables, show tables, etc.
   - Tool: list_tables

3. **DESCRIBE**: Describe table structure
   - Spanish: estructura, describir, formato, etc.
   - English: describe, structure, schema, etc.
   - Tool: describe_table

4. **QUERY**: Query or search data
   - Spanish: consulta, buscar, filtrar, datos, etc.
   - English: query, search, filter, data, etc.
   - Tools: query_presto, query_data

5. **SAMPLE**: Get sample data
   - Spanish: ejemplos, muestra, filas, etc.
   - English: sample, examples, rows, etc.
   - Tool: sample_data

6. **SEARCH**: Search tables by keyword
   - Spanish: buscar tablas, encontrar tablas, etc.
   - English: search tables, find tables, etc.
   - Tool: search_tables

7. **REPOSITORIES**: Work with code repositories
   - Spanish: repositorios, repos, proyectos, etc.
   - English: repositories, repos, projects, etc.
   - Tools: list_repositories, search_repositories

8. **FILES**: Work with files and content
   - Spanish: archivos, contenido, ficheros, etc.
   - English: files, contents, documents, etc.
   - Tools: get_file_contents, list_files

### Patterns

Special patterns for response detection:

```yaml
patterns:
  schema_response:
    keywords:
      spanish: ["listame", "esquemas"]
      english: ["list", "schema"]
    combined_checks: ["information_schema"]
```

### Fallback Configuration

Default tool selection order when no specific intent is detected:

```yaml
fallback:
  default_tool_selection_order:
    - "query_data"
    - "list_tables"
    - "describe_table"
    - "sample_data"
    - "search_tables"
```

## How It Works

### Intent Detection Flow

1. **User Input**: User sends a message like "cuales son los esquemas"
2. **Keyword Matching**: `IntentKeywordService` checks the message against all intent keywords
3. **Intent Identification**: Finds that "cuales son los esquemas" matches LIST_SCHEMAS intent
4. **Tool Selection**: Returns the appropriate tool name ("list_schemas")
5. **Tool Execution**: The selected tool is executed

### Usage in Code

The system is used in two main places:

1. **IntelligentToolSelector**: Uses `IntentKeywordService.detectIntentAndGetTool()` in fallback scenarios
2. **ChatService**: Uses `IntentKeywordService.isSchemaResponse()` for response pattern detection

## Adding New Intents

To add a new intent:

1. Add the intent definition to `application.yml`:
```yaml
NEW_INTENT:
  keywords:
    spanish:
      - "nuevo"
      - "crear algo"
    english:
      - "new"
      - "create something"
  toolNames:
    - "create_tool"
  description: "Intent to create something new"
```

2. Update `IntentKeywordProperties.IntentConfig` class to include the new intent field:
```java
private IntentDefinition NEW_INTENT;
```

3. Update the `getAllIntents()` method to include the new intent in the map

That's it! No other code changes are needed.

## Testing

The system includes comprehensive tests in `IntentKeywordServiceTest.java` that verify:

- Spanish keyword detection
- English keyword detection
- Case-insensitive matching
- Keyword matching within longer text
- Proper tool selection
- Fallback behavior
- Schema response pattern detection

## Migration Benefits

This new system provides several benefits over the previous hardcoded approach:

1. **Maintainability**: Keywords are centralized in configuration
2. **Extensibility**: New intents can be added without code changes
3. **Bilingual Support**: Maintains support for Spanish and English
4. **Testability**: Intent detection logic is now easily testable
5. **Flexibility**: Multiple tools can be associated with each intent
6. **Performance**: Configurable fallback ordering improves tool selection

## Examples

### Spanish Examples
- "cuales son los esquemas" → LIST_SCHEMAS → list_schemas
- "que tablas hay" → LIST_TABLES → list_tables
- "estructura de la tabla usuarios" → DESCRIBE → describe_table
- "dame datos de la tabla ventas" → QUERY → query_data

### English Examples
- "list schemas" → LIST_SCHEMAS → list_schemas
- "show me the tables" → LIST_TABLES → list_tables
- "describe table structure" → DESCRIBE → describe_table
- "get sample data" → SAMPLE → sample_data