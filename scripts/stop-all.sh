#!/bin/bash
# scripts/stop-all.sh

echo "🛑 Stopping NetBattle Arena Servers..."

if [ -f .pids ]; then
    while read pid; do
        if ps -p $pid > /dev/null; then
            echo "Stopping process $pid..."
            kill $pid
        fi
    done < .pids
    
    rm .pids
    echo "✅ All servers stopped"
else
    echo "❌ No PIDs file found. Servers may not be running."
fi