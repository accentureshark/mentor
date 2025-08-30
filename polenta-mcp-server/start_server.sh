#!/bin/bash
# Polenta MCP Server Startup Script

echo "Starting Polenta MCP Server..."
echo "Server will be available at http://localhost:25001"
echo ""

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is required but not installed."
    exit 1
fi

# Check if pip is available
if ! command -v pip &> /dev/null && ! command -v pip3 &> /dev/null; then
    echo "Error: pip is required but not installed."
    exit 1
fi

# Install dependencies if not already installed
echo "Installing dependencies..."
if command -v pip3 &> /dev/null; then
    pip3 install -r requirements.txt
else
    pip install -r requirements.txt
fi

echo ""
echo "Starting server..."
python3 polenta_server.py