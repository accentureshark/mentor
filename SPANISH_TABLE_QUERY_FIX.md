# Spanish Table Data Query Recognition Fix

## Problem
The user query "dame los registros de la tabla natios del esquema tiny" (give me the records from the nation table in the tiny schema) was incorrectly being routed to schema listing tools instead of the data querying tool.

## Root Cause
The `IntelligentToolSelector` was prioritizing entity matching over intent detection, causing queries with table names to be routed to table/schema listing tools rather than data query tools.

## Solution
Enhanced the tool selection logic with:

### 1. Improved Intent Detection
Added `isTableDataRequest()` method that recognizes Spanish data query patterns:
- "registros de la tabla X" (records from table X)
- "datos de la tabla X" (data from table X) 
- "dame X de la tabla Y" where X is data-related
- "muéstrame los datos/registros" (show data/records)
- "necesito/quiero X de tabla Y" (I need/want X from table Y)

### 2. Enhanced Spanish Term Matching
Updated `trySpanishTranslationMatching()` to include data query terms:
- "registros" (records)
- "datos" (data) 
- "información" (information)
- "contenido" (content)

### 3. Priority Scoring Adjustment
Increased QUERY intent scoring from 30 to 60 points when tools match query patterns, ensuring data query tools are prioritized over entity-matching tools.

## Results
✅ **Before Fix:**
- "dame los registros de la tabla natios del esquema tiny" → `list_tables` (wrong)

✅ **After Fix:** 
- "dame los registros de la tabla natios del esquema tiny" → `query_presto` (correct)

## Preserved Functionality
The fix maintains existing functionality for other query types:
- "lista todas las tablas" → `list_tables`
- "lista todos los esquemas" → `list_schemas`
- Schema information requests still route correctly

## Technical Details
The fix is implemented in `IntelligentToolSelector.java`:
1. `detectPrimaryIntent()` now checks for table data patterns first (highest priority)
2. `isTableDataRequest()` detects Spanish data query patterns 
3. Scoring logic gives 60 points for QUERY intent vs 50 for entity matching
4. Spanish translation matching includes new data query terms

## Testing
Created comprehensive tests in `SpanishDataQueryRecognitionTest.java` that verify:
- Spanish table data queries route to `query_presto`
- Various Spanish data query patterns work correctly
- Existing functionality remains unbroken