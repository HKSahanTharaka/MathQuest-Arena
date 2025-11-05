#!/bin/bash
# scripts/start-all.sh

echo "🎮 Starting NetBattle Arena Servers..."
echo "======================================"

# Generate keystore if not exists
if [ ! -f "resources/keystore.jks" ]; then
    echo "🔐 Generating SSL keystore..."
    cd resources
    bash generate-keystore.sh
    cd ..
fi

# Compile all code
echo "📦 Compiling project..."
mvn clean compile

# Start all servers in background
echo ""
echo "🚀 Starting servers..."

# 1. RMI Statistics Service (Member 5)
echo "📊 Starting RMI Service..."
java -cp target/classes com.netbattle.server.rmi.RMIServer > logs/rmi.log 2>&1 &
RMI_PID=$!
echo "   PID: $RMI_PID"
sleep 2

# 2. Secure Authentication Server (Member 4)
echo "🔒 Starting Secure Auth Server..."
java -cp target/classes com.netbattle.server.security.SecureAuthServer > logs/ssl.log 2>&1 &
SSL_PID=$!
echo "   PID: $SSL_PID"
sleep 2

# 3. Main TCP Game Server (Member 1)
echo "🎮 Starting TCP Game Server..."
java -cp target/classes com.netbattle.server.core.GameServer > logs/tcp.log 2>&1 &
TCP_PID=$!
echo "   PID: $TCP_PID"
sleep 2

# 4. NIO Game State Manager (Member 2)
echo "⚡ Starting NIO Manager..."
java -cp target/classes com.netbattle.server.nio.NIOGameStateManager > logs/nio.log 2>&1 &
NIO_PID=$!
echo "   PID: $NIO_PID"
sleep 2

# 5. UDP Event Server (Member 3)
echo "📡 Starting UDP Server..."
java -cp target/classes com.netbattle.server.udp.UDPEventServer > logs/udp.log 2>&1 &
UDP_PID=$!
echo "   PID: $UDP_PID"
sleep 2

# Save PIDs
echo "$RMI_PID" > .pids
echo "$SSL_PID" >> .pids
echo "$TCP_PID" >> .pids
echo "$NIO_PID" >> .pids
echo "$UDP_PID" >> .pids

echo ""
echo "✅ All servers started!"
echo ""
echo "Server Status:"
echo "  📊 RMI Service:    localhost:1099"
echo "  🔒 SSL Auth:       localhost:8443"
echo "  🎮 TCP Server:     localhost:8080"
echo "  ⚡ NIO Manager:    localhost:8081"
echo "  📡 UDP Server:     localhost:9000"
echo ""
echo "To start client: java -cp target/classes com.netbattle.client.GameClient"
echo "To stop servers: bash scripts/stop-all.sh"
echo "View logs: tail -f logs/*.log"