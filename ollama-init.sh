#!/bin/bash
ollama serve &
pid=$!
sleep 5
echo "Preloading ${PRELOAD_MODEL_NAME} via Ollama CLI…"
# run the model with no prompt, this will preload it into memory
if ollama run "${PRELOAD_MODEL_NAME}" "" 2>/dev/null; then
    echo "Preload successful"
else
    echo "Preload failed"
fi
wait $pid
