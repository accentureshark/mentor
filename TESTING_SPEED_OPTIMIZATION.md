# Testing Guide: LLM Speed Optimization

## Quick Validation Steps

### 1. Start the Optimized Services

```bash
# Pull the new model (if needed)
docker run --rm -v ollama-data:/root/.ollama ollama/ollama pull gemma2:2b

# Start Ollama with optimized configuration
docker-compose up ollama

# Verify model is loaded
curl http://localhost:11435/api/tags
```

### 2. Test Backend Configuration

```bash
# Start backend
cd backend && ./start-dev.sh

# Check model configuration
curl http://localhost:8083/actuator/env | grep -i llm
```

### 3. Functional Testing

#### Test Spanish Tool Selection (Core Use Case)
```bash
curl -X POST localhost:8083/api/mcp/chat/send \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Quiero ver los esquemas disponibles en la base de datos",
    "serverId": "polenta-local"
  }'
```

#### Test English Tool Selection
```bash
curl -X POST localhost:8083/api/mcp/chat/send \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me the available tables",
    "serverId": "polenta-local"
  }'
```

#### Test Argument Extraction
```bash
curl -X POST localhost:8083/api/mcp/chat/send \
  -H "Content-Type: application/json" \
  -d '{
    "message": "List the first 5 tables from the public schema",
    "serverId": "polenta-local"
  }'
```

### 4. Performance Monitoring

#### Check Cache Statistics
```bash
curl http://localhost:8083/api/llm/cache/stats
```

#### Monitor Response Times
- **Previous model**: Expect ~2-5 seconds for tool selection
- **New model**: Expect ~1-3 seconds for tool selection (40-60% improvement)

### 5. Validate Model Switch

#### Check Current Model
```bash
# Should show gemma2:2b in logs
docker logs mentor-ollama | grep -i preload

# Check application logs
docker logs mentor-backend | grep -i "Creating.*model"
```

## Expected Results

### ✅ Success Indicators
- [ ] Model loads successfully: `gemma2:2b`
- [ ] Spanish requests work: "esquemas" → selects list_schemas tool
- [ ] English requests work: "tables" → selects list_tables tool  
- [ ] Argument extraction works: extracts parameters correctly
- [ ] Response times improved: 40-60% faster than before
- [ ] Cache statistics show hits: Performance optimizations active

### ⚠️ Rollback If
- Model fails to load or respond
- Tool selection accuracy drops significantly
- Spanish understanding degrades
- Timeout errors increase

### 🔄 Rollback Command
```bash
# Revert to previous model
export LLM_MODEL="hf.co/unsloth/gemma-3n-E4B-it-GGUF:Q4_K_XL"
docker-compose down && docker-compose up ollama
```

## Alternative Models to Try

If Gemma 2 2B doesn't meet requirements:

```bash
# For maximum speed (trade-off some quality)
export LLM_MODEL="llama3.2:1b"

# For balanced performance
export LLM_MODEL="llama3.2:3b"

# For complex instruction following
export LLM_MODEL="phi3:3.8b"
```

## Performance Baseline

Document your results:

| Model | Avg Response Time | Tool Selection Accuracy | Spanish Understanding |
|-------|-------------------|------------------------|----------------------|
| Previous (Gemma-3n) | ___s | __% | __% |
| New (Gemma2:2b) | ___s | __% | __% |
| Improvement | ___% faster | ±__% | ±__% |

## Troubleshooting

### Model Won't Load
```bash
# Check Ollama service
docker logs mentor-ollama

# Manually pull model
docker exec mentor-ollama ollama pull gemma2:2b
```

### Backend Can't Connect
```bash
# Check model is running
curl http://localhost:11435/api/tags

# Test direct model query
curl http://localhost:11435/api/generate \
  -d '{"model": "gemma2:2b", "prompt": "Hello"}'
```

### Poor Performance
- Check system resources (CPU/Memory)
- Verify GPU is being used (if available)
- Monitor Docker container logs for errors