# 🎓 VIVA Demonstration Guide - MathQuest Arena
## Network Programming Concepts by Team Member

This guide demonstrates each team member's contributions with code explanations, runtime demonstrations, and Wireshark analysis.

---

## 📋 Table of Contents
1. [Member 1: Sahan - TCP Game Server](#member-1-sahan---tcp-game-server)
2. [Member 2: Sachith - NIO State Manager](#member-2-sachith---nio-state-manager)
3. [Member 3: Nithakshi - UDP Event System](#member-3-nithakshi---udp-event-system)
4. [Member 4: Chiran - Service Discovery & RMI](#member-4-chiran---service-discovery--rmi)
5. [Member 5: Thilina - WebSocket Bridge](#member-5-thilina---websocket-bridge)
6. [Integration Demo](#integration-demo)

---

## Member 1: Sahan - TCP Game Server

### 🎯 Network Concepts Demonstrated
- **TCP Socket Programming** with ServerSocket
- **Multi-threading** with ExecutorService (50-thread pool)
- **Concurrent Collections** (ConcurrentHashMap)
- **HTTP Protocol** implementation (REST API)
- **Session Management** and state synchronization
- **Backend-to-Backend Communication** (Internal HTTP API)

### 📁 Key Files
```
src/main/java/com/netbattle/api/
├── RestApiServer.java         # Main REST API server
├── GameDataManager.java       # Game state management
└── (ClientHandler.java)       # TCP client handling
```

### 💻 Code Walkthrough

#### 1. TCP Server Initialization (RestApiServer.java)
```java
// Line ~26: Creating HTTP server on port 8080
server = HttpServer.create(new InetSocketAddress(PORT), 0);
server.setExecutor(Executors.newFixedThreadPool(10)); // Thread pool

// Line ~30-40: Endpoint registration
server.createContext("/api/auth/login", this::handleLogin);
server.createContext("/api/problems", this::handleChallenges);
server.createContext("/api/leaderboard", this::handleLeaderboard);
```

#### 2. Multi-threading with Thread Pool
```java
// GameDataManager.java - Line ~55
private Map<String, PlayerData> players = new ConcurrentHashMap<>();
private Set<String> activePlayerIds = ConcurrentHashMap.newKeySet();

// Thread-safe operations
public synchronized void registerPlayer(String playerId, String username) {
    // Thread-safe player registration
}
```

#### 3. Internal HTTP API for Broadcasting
```java
// GameDataManager.java - Line ~247
private void broadcastPlayerJoinToWebSocket(String username, int currentCount, int minimumPlayers) {
    new Thread(() -> {
        try {
            URL url = new URL("http://localhost:8083/broadcast/player-join");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            // ... send JSON data to WebSocket server
        } catch (Exception e) { }
    }).start();
}
```

### 🔬 Live Demonstration Steps

#### Step 1: Start the TCP Server
```bash
# Terminal 1
cd "c:\Users\Sahan Tharaka\Documents\MathQuest-Arena"
java -cp target/classes com.netbattle.api.RestApiServer
```

**Expected Output:**
```
🌐 REST API Server started on port 8080
```

#### Step 2: Test TCP Connection with cURL
```bash
# Terminal 2
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"TestPlayer1\"}"
```

**Expected Response:**
```json
{
  "success": true,
  "token": "uuid-token-here",
  "playerId": "player-TestPlayer1-timestamp",
  "username": "TestPlayer1"
}
```

#### Step 3: Monitor with Wireshark

**Wireshark Filter:**
```
tcp.port == 8080
```

**What to Observe:**
1. **TCP 3-way Handshake:**
   - SYN → SYN-ACK → ACK
2. **HTTP POST Request:**
   - Shows JSON payload in packet details
3. **HTTP Response:**
   - Status: 200 OK
   - Content-Type: application/json
4. **Connection Management:**
   - TCP Keep-Alive packets
   - FIN/ACK for connection close

**Screenshot Points:**
- Capture showing TCP handshake
- HTTP request with JSON body
- Thread pool activity (show multiple concurrent requests)

#### Step 4: Demonstrate Multi-threading

**PowerShell Script to Create Multiple Connections:**
```powershell
# Test concurrent connections
1..10 | ForEach-Object -Parallel {
    $username = "Player$_"
    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" `
        -ContentType "application/json" `
        -Body "{`"username`":`"$username`"}"
} -ThrottleLimit 10
```

**What to Show:**
- Server console showing multiple concurrent login requests
- Wireshark showing multiple TCP connections simultaneously
- Thread pool handling requests in parallel

#### Step 5: Show Internal HTTP API Communication

```bash
# Trigger a game event that broadcasts to WebSocket
curl -X POST http://localhost:8080/api/problems/submit ^
  -H "Content-Type: application/json" ^
  -H "X-Player-Id: player-TestPlayer1-123" ^
  -d "{\"challengeId\":\"1\",\"answer\":\"42\"}"
```

**Wireshark Filter:**
```
tcp.port == 8083
```

**What to Observe:**
- Internal HTTP POST to port 8083 (WebSocket broadcast endpoint)
- JSON payload containing game state updates
- Backend-to-backend communication pattern

### 📊 Key Metrics to Demonstrate
- **Thread Pool Size:** 50 threads (configurable)
- **Concurrent Connections:** Show 10+ simultaneous connections
- **Response Time:** < 50ms for most requests
- **Session Management:** Multiple players with unique sessions

---

## Member 2: Sachith - NIO State Manager

### 🎯 Network Concepts Demonstrated
- **Non-blocking I/O** (Java NIO)
- **Selector Pattern** for event multiplexing
- **Channel-based Communication**
- **Efficient State Broadcasting**
- **Single Thread handling Multiple Connections**

### 📁 Key Files
```
src/main/java/com/netbattle/server/nio/
└── NIOGameStateManager.java   # NIO implementation
```

### 💻 Code Walkthrough

#### 1. NIO Server Setup
```java
// NIOGameStateManager.java - Line ~36
selector = Selector.open();
serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);  // Non-blocking mode
serverChannel.bind(new InetSocketAddress(NIO_PORT));
serverChannel.register(selector, SelectionKey.OP_ACCEPT);
```

#### 2. Event Loop with Selector
```java
// Line ~48: Main event loop
while (running) {
    int readyChannels = selector.select(1000);  // Wait for events
    
    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
    
    while (keyIterator.hasNext()) {
        SelectionKey key = keyIterator.next();
        keyIterator.remove();
        
        if (key.isAcceptable()) {
            handleAccept(key);    // New connection
        } else if (key.isReadable()) {
            handleRead(key);      // Data available to read
        } else if (key.isWritable()) {
            handleWrite(key);     // Ready to write
        }
    }
}
```

#### 3. Non-blocking Accept
```java
// Line ~76: Accepting connections without blocking
private void handleAccept(SelectionKey key) throws IOException {
    ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
    SocketChannel clientChannel = serverChannel.accept();
    
    if (clientChannel != null) {
        clientChannel.configureBlocking(false);  // Non-blocking
        clientChannel.register(selector, SelectionKey.OP_READ);
        
        PlayerSession session = new PlayerSession(clientChannel);
        playerSessions.put(clientChannel, session);
    }
}
```

#### 4. State Broadcasting to All Clients
```java
// Line ~157: Efficient broadcasting
private void broadcastGameState() {
    for (GameSession gameSession : gameSessions.values()) {
        List<Player> leaderboard = gameSession.getLeaderboard();
        
        Message stateMessage = new Message(MessageType.GAME_STATE, 
            null, state.toString().getBytes());
        
        // Send to all players in this session
        for (Map.Entry<SocketChannel, PlayerSession> entry : playerSessions.entrySet()) {
            if (gameSession.getSessionId().equals(entry.getValue().getGameSessionId())) {
                writeToChannel(entry.getKey(), stateMessage);
            }
        }
    }
}
```

### 🔬 Live Demonstration Steps

#### Step 1: Start NIO Server
```bash
# Terminal 1
java -cp target/classes com.netbattle.server.nio.NIOGameStateManager
```

**Expected Output:**
```
⚡ NIO Game State Manager started on port 8081
```

#### Step 2: Create Test Client to Connect

**Create test file: `test-nio-client.java`**
```java
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public class TestNIOClient {
    public static void main(String[] args) throws Exception {
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress("localhost", 8081));
        
        System.out.println("Connected to NIO server on port 8081");
        
        // Keep connection alive
        Thread.sleep(60000);
    }
}
```

#### Step 3: Demonstrate Selector Efficiency

**Create Multiple Connections:**
```powershell
# PowerShell - Start 20 connections simultaneously
1..20 | ForEach-Object {
    Start-Job -ScriptBlock {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect("localhost", 8081)
        Start-Sleep -Seconds 30
    }
}
```

**What to Show:**
- Server console showing all 20 connections handled by **single thread**
- Wireshark showing multiple established TCP connections
- CPU usage remains low despite many connections

#### Step 4: Wireshark Analysis

**Wireshark Filter:**
```
tcp.port == 8081
```

**What to Observe:**
1. **Non-blocking Accept:**
   - Multiple TCP connections established rapidly
   - No blocking delays between connections

2. **State Broadcasting:**
   - Periodic packets sent to all connected clients
   - Same data sent to multiple destinations

3. **Channel Operations:**
   - Read operations (OP_READ events)
   - Write operations (OP_WRITE events)
   - No thread-per-connection overhead

**Key Screenshots:**
- Single thread handling 20+ connections
- Selector waiting on multiple channels
- Efficient broadcasting pattern

### 📊 Performance Metrics
- **Connections per Thread:** 500+ possible
- **Selector Timeout:** 1000ms
- **Memory Efficiency:** Much lower than thread-per-connection
- **Latency:** < 10ms for state updates

---

## Member 3: Nithakshi - UDP Event System

### 🎯 Network Concepts Demonstrated
- **UDP Socket Programming** (DatagramSocket)
- **Unicast Messaging**
- **Multicast Groups** (230.0.0.1)
- **Event-Driven Architecture**
- **Low-Latency Communication**
- **BlockingQueue** for event processing

### 📁 Key Files
```
src/main/java/com/netbattle/server/udp/
└── UDPEventServer.java        # UDP implementation
```

### 💻 Code Walkthrough

#### 1. UDP Socket Initialization
```java
// UDPEventServer.java - Line ~39
// Unicast socket
unicastSocket = new DatagramSocket(UDP_PORT);

// Multicast socket
multicastSocket = new MulticastSocket(MULTICAST_PORT);
multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);

// Join multicast group
NetworkInterface netIf = NetworkInterface.getByInetAddress(
    InetAddress.getLocalHost());
multicastSocket.joinGroup(
    new InetSocketAddress(multicastGroup, MULTICAST_PORT), netIf);
```

#### 2. Event Queue and Processing
```java
// Line ~27
private BlockingQueue<UDPEvent> eventQueue = new LinkedBlockingQueue<>();
private ExecutorService eventProcessor = Executors.newFixedThreadPool(4);

// Line ~55: Event processor threads
for (int i = 0; i < 4; i++) {
    eventProcessor.execute(this::processEvents);
}
```

#### 3. Receiving UDP Packets
```java
// Line ~63: Non-blocking receive loop
private void receiveLoop() {
    byte[] buffer = new byte[MAX_PACKET_SIZE];
    
    while (running) {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        unicastSocket.receive(packet);  // Blocking call
        
        // Parse message
        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
        Message message = Message.fromBytes(data);
        
        // Queue event for processing
        UDPEvent event = new UDPEvent(message, 
            packet.getAddress(), packet.getPort());
        eventQueue.offer(event);
    }
}
```

#### 4. Unicast Messaging
```java
// Line ~157: Send to specific player
private void sendToPlayer(String sessionId, Message message) {
    InetSocketAddress address = playerAddresses.get(sessionId);
    
    byte[] data = message.toBytes();
    DatagramPacket packet = new DatagramPacket(
        data, data.length, 
        address.getAddress(), address.getPort());
    unicastSocket.send(packet);
}
```

#### 5. Multicast Broadcasting
```java
// Line ~182: Broadcast to multicast group
private void broadcastViaMulticast(Message message) {
    byte[] data = message.toBytes();
    DatagramPacket packet = new DatagramPacket(
        data, data.length, 
        multicastGroup, MULTICAST_PORT);  // To multicast address
    multicastSocket.send(packet);
    
    System.out.println("📢 Multicast broadcast sent");
}
```

#### 6. Heartbeat Mechanism
```java
// Line ~195: Periodic heartbeat
private void broadcastHeartbeat() {
    while (running) {
        Thread.sleep(5000);  // Every 5 seconds
        
        Message heartbeat = new Message(MessageType.GAME_STATE, 
            null, "HEARTBEAT".getBytes());
        broadcastViaMulticast(heartbeat);
    }
}
```

### 🔬 Live Demonstration Steps

#### Step 1: Start UDP Server
```bash
# Terminal 1
java -cp target/classes com.netbattle.server.udp.UDPEventServer
```

**Expected Output:**
```
📡 UDP Event Server started
   Unicast port: 9000
   Multicast: 230.0.0.1:9001
💓 Heartbeat sent to 0 players
```

#### Step 2: Create UDP Test Client

**Save as `udp-test-client.java`:**
```java
import java.net.*;
import java.io.*;

public class UDPTestClient {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        
        // Send position update
        String message = "POSITION:100,200";
        byte[] buffer = message.getBytes();
        
        DatagramPacket packet = new DatagramPacket(
            buffer, buffer.length,
            InetAddress.getByName("localhost"), 9000
        );
        
        socket.send(packet);
        System.out.println("Sent UDP packet: " + message);
        
        // Receive response
        byte[] receiveBuffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(
            receiveBuffer, receiveBuffer.length
        );
        
        socket.setSoTimeout(5000);
        socket.receive(receivePacket);
        
        String response = new String(receivePacket.getData(), 
            0, receivePacket.getLength());
        System.out.println("Received: " + response);
        
        socket.close();
    }
}
```

**Compile and Run:**
```bash
javac udp-test-client.java
java UDPTestClient
```

#### Step 3: Test Multicast Receiver

**Save as `multicast-receiver.java`:**
```java
import java.net.*;

public class MulticastReceiver {
    public static void main(String[] args) throws Exception {
        MulticastSocket socket = new MulticastSocket(9001);
        InetAddress group = InetAddress.getByName("230.0.0.1");
        NetworkInterface netIf = NetworkInterface.getByInetAddress(
            InetAddress.getLocalHost());
        
        socket.joinGroup(new InetSocketAddress(group, 9001), netIf);
        System.out.println("Joined multicast group 230.0.0.1:9001");
        
        while (true) {
            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            
            String message = new String(packet.getData(), 
                0, packet.getLength());
            System.out.println("Multicast received: " + message);
        }
    }
}
```

**Run:**
```bash
javac multicast-receiver.java
java MulticastReceiver
```

**Expected Output (every 5 seconds):**
```
Joined multicast group 230.0.0.1:9001
Multicast received: HEARTBEAT
Multicast received: HEARTBEAT
```

#### Step 4: Wireshark Analysis - UDP Unicast

**Wireshark Filter:**
```
udp.port == 9000
```

**What to Observe:**
1. **UDP Packet Structure:**
   - No connection setup (connectionless)
   - Single packet for each message
   - Source/Destination ports

2. **Packet Details:**
   - UDP header (8 bytes)
   - Payload (your message)
   - No acknowledgment

3. **Characteristics:**
   - No retransmission
   - Fast delivery
   - Small overhead

**Screenshots to Capture:**
- UDP packet with position data
- Packet details showing UDP header
- No TCP handshake (connectionless)

#### Step 5: Wireshark Analysis - Multicast

**Wireshark Filter:**
```
ip.dst == 230.0.0.1 || udp.port == 9001
```

**What to Observe:**
1. **Multicast Address:**
   - Destination IP: 230.0.0.1 (Class D)
   - All receivers get the same packet

2. **IGMP Protocol:**
   - IGMP Join Group messages
   - Membership reports

3. **Heartbeat Packets:**
   - Periodic packets every 5 seconds
   - Same packet to all group members

**Key Points to Explain:**
- Why multicast is efficient (one packet → many receivers)
- How it differs from broadcast
- Use cases (game events, notifications)

### 📊 Performance Comparison

**Demonstrate UDP vs TCP:**

| Feature | UDP (Port 9000) | TCP (Port 8080) |
|---------|----------------|----------------|
| Connection | Connectionless | Connection-oriented |
| Reliability | No guarantee | Guaranteed delivery |
| Overhead | 8 bytes header | 20+ bytes header |
| Latency | < 5ms | 10-50ms |
| Use Case | Position updates | Game state |

---

## Member 4: Chiran - Service Discovery & RMI

### 🎯 Network Concepts Demonstrated
- **Service Registry Pattern**
- **RESTful API** for service discovery
- **Heartbeat Monitoring**
- **RMI (Remote Method Invocation)**
- **Distributed Object Communication**
- **Health Checking**

### 📁 Key Files
```
src/main/java/com/netbattle/discovery/
├── ServiceDiscoveryServer.java    # Discovery server
├── ServiceRegistry.java           # Registry logic
└── ServiceInfo.java               # Service metadata

src/main/java/com/netbattle/server/rmi/
├── RMIServer.java                 # RMI server
├── StatisticsService.java         # Interface
└── StatisticsServiceImpl.java     # Implementation
```

### 💻 Code Walkthrough - Service Discovery

#### 1. Service Registry Endpoints
```java
// ServiceDiscoveryServer.java - Line ~26
server.createContext("/registry/register", this::handleRegister);
server.createContext("/registry/deregister", this::handleDeregister);
server.createContext("/registry/heartbeat", this::handleHeartbeat);
server.createContext("/registry/services", this::handleGetServices);
server.createContext("/registry/discover", this::handleDiscover);
server.createContext("/registry/stats", this::handleStats);
```

#### 2. Service Registration
```java
// Line ~38: Handle service registration
private void handleRegister(HttpExchange exchange) throws IOException {
    String body = readRequestBody(exchange);
    Map<String, String> data = parseJson(body);
    
    String serviceId = data.get("serviceId");
    String serviceName = data.get("serviceName");
    String host = data.getOrDefault("host", "localhost");
    int port = Integer.parseInt(data.get("port"));
    
    ServiceInfo service = new ServiceInfo(serviceId, serviceName, 
        host, port, protocol);
    boolean registered = registry.registerService(service);
}
```

#### 3. Heartbeat Monitoring
```java
// Line ~71: Heartbeat updates
private void handleHeartbeat(HttpExchange exchange) {
    String serviceId = data.get("serviceId");
    int activeConnections = Integer.parseInt(
        data.getOrDefault("activeConnections", "0"));
    
    registry.updateHeartbeat(serviceId, activeConnections);
}
```

#### 4. Service Discovery (DNS-like)
```java
// Line ~117: Discover service by name
private void handleDiscover(HttpExchange exchange) {
    String serviceName = query.split("name=")[1].split("&")[0];
    ServiceInfo service = registry.discoverService(serviceName);
    
    // Returns service URL and metadata
    serviceData.put("url", service.getServiceUrl());
}
```

### 💻 Code Walkthrough - RMI

#### 1. RMI Interface Definition
```java
// StatisticsService.java
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface StatisticsService extends Remote {
    List<PlayerStats> getLeaderboard() throws RemoteException;
    PlayerStats getPlayerStats(String playerId) throws RemoteException;
    List<String> getAchievements(String playerId) throws RemoteException;
    ServerStats getServerStats() throws RemoteException;
}
```

#### 2. RMI Server Setup
```java
// RMIServer.java - Line ~13
Registry registry = LocateRegistry.createRegistry(RMI_PORT);

// Create service instance
StatisticsService statsService = new StatisticsServiceImpl();

// Bind service to registry
registry.rebind(SERVICE_NAME, statsService);
```

#### 3. Remote Method Implementation
```java
// StatisticsServiceImpl.java
public class StatisticsServiceImpl extends UnicastRemoteObject 
    implements StatisticsService {
    
    @Override
    public List<PlayerStats> getLeaderboard() throws RemoteException {
        GameDataManager gameData = GameDataManager.getInstance();
        return gameData.getLeaderboard();
    }
}
```

### 🔬 Live Demonstration Steps - Service Discovery

#### Step 1: Start Service Discovery Server
```bash
# Terminal 1
java -cp target/classes com.netbattle.discovery.ServiceDiscoveryServer
```

**Expected Output:**
```
🔍 Service Discovery/Registry Server started on port 8084
   📋 Endpoints:
      POST   /registry/register   - Register a service
      POST   /registry/heartbeat  - Send heartbeat
      GET    /registry/services   - List all services
```

#### Step 2: Register a Service
```bash
# Terminal 2
curl -X POST http://localhost:8084/registry/register ^
  -H "Content-Type: application/json" ^
  -d "{\"serviceId\":\"test-service-1\",\"serviceName\":\"TestService\",\"host\":\"localhost\",\"port\":8080,\"protocol\":\"http\"}"
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Service registered successfully",
  "serviceId": "test-service-1"
}
```

#### Step 3: Send Heartbeat
```bash
curl -X POST http://localhost:8084/registry/heartbeat ^
  -H "Content-Type: application/json" ^
  -d "{\"serviceId\":\"test-service-1\",\"activeConnections\":5}"
```

#### Step 4: Discover Service
```bash
curl "http://localhost:8084/registry/discover?name=TestService"
```

**Expected Response:**
```json
{
  "serviceId": "test-service-1",
  "serviceName": "TestService",
  "host": "localhost",
  "port": 8080,
  "url": "http://localhost:8080",
  "status": "UP"
}
```

#### Step 5: List All Services
```bash
curl "http://localhost:8084/registry/services"
```

**Expected Response:**
```json
[
  {
    "serviceId": "registry-1",
    "serviceName": "Service Registry",
    "status": "UP",
    "uptime": 120000,
    ...
  },
  {
    "serviceId": "test-service-1",
    "serviceName": "TestService",
    "status": "UP",
    ...
  }
]
```

#### Step 6: Wireshark Analysis

**Wireshark Filter:**
```
tcp.port == 8084
```

**What to Observe:**
1. **HTTP POST for Registration:**
   - JSON payload with service info
   - Response with registration confirmation

2. **Periodic Heartbeats:**
   - Regular HTTP POST every 10 seconds
   - Load information in payload

3. **Service Discovery Request:**
   - HTTP GET with service name
   - Response with service URL

**Key Screenshots:**
- Service registration packet
- Heartbeat pattern over time
- Discovery request/response

### 🔬 Live Demonstration Steps - RMI

#### Step 1: Start RMI Server
```bash
# Terminal 1
java -cp target/classes com.netbattle.server.rmi.RMIServer
```

**Expected Output:**
```
📊 RMI Statistics Server started
   Port: 1099
   Service: NetBattleStats
   URL: rmi://localhost:1099/NetBattleStats
✅ Ready to accept remote calls
```

#### Step 2: Create RMI Client Test

**Save as `rmi-test-client.java`:**
```java
import com.netbattle.server.rmi.StatisticsService;
import java.rmi.registry.*;
import java.util.*;

public class RMITestClient {
    public static void main(String[] args) {
        try {
            // Lookup RMI registry
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            
            // Get remote object
            StatisticsService stats = (StatisticsService) 
                registry.lookup("NetBattleStats");
            
            System.out.println("✅ Connected to RMI server");
            
            // Call remote method
            System.out.println("\n📊 Fetching leaderboard...");
            List<?> leaderboard = stats.getLeaderboard();
            System.out.println("Leaderboard entries: " + leaderboard.size());
            
            // Call another remote method
            System.out.println("\n📈 Fetching server stats...");
            Object serverStats = stats.getServerStats();
            System.out.println("Server stats: " + serverStats);
            
        } catch (Exception e) {
            System.err.println("❌ RMI Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

**Run:**
```bash
javac -cp target/classes rmi-test-client.java
java -cp "target/classes;." RMITestClient
```

**Expected Output:**
```
✅ Connected to RMI server
📊 Fetching leaderboard...
Leaderboard entries: 5
📈 Fetching server stats...
Server stats: ServerStats{players=5, uptime=300000}
```

#### Step 3: Wireshark Analysis - RMI

**Wireshark Filter:**
```
tcp.port == 1099
```

**What to Observe:**
1. **RMI Registry Lookup:**
   - Initial connection to port 1099
   - Registry protocol handshake

2. **Remote Method Invocation:**
   - Serialized Java objects
   - Method call parameters
   - Return values

3. **Java Serialization:**
   - Binary protocol
   - Object serialization format

**Advanced Filter (RMI protocol):**
```
rmi || java
```

**Key Points to Explain:**
- How RMI differs from REST (binary vs text)
- Stub and skeleton concept
- Remote object references
- Serialization overhead

### 📊 Demonstration Flow Chart

```
Client → Registry Lookup (port 1099)
       ↓
    Get Remote Reference
       ↓
    Call Remote Method
       ↓
    Receive Serialized Result
```

---

## Member 5: Thilina - WebSocket Bridge

### 🎯 Network Concepts Demonstrated
- **WebSocket Protocol** (RFC 6455)
- **Full-Duplex Communication**
- **Custom Protocol Implementation** (no libraries)
- **WebSocket Handshake**
- **Frame Encoding/Decoding**
- **Real-time Broadcasting**
- **NIO Selector** for WebSocket connections

### 📁 Key Files
```
src/main/java/com/netbattle/websocket/
└── WebSocketGameServer.java   # WebSocket implementation

frontend/src/services/
└── websocket.js                # Client implementation
```

### 💻 Code Walkthrough - Server Side

#### 1. WebSocket Handshake
```java
// WebSocketGameServer.java - Line ~93
private void performHandshake(SocketChannel channel, ByteBuffer buffer, 
    WebSocketClient client) throws IOException {
    
    String request = StandardCharsets.UTF_8.decode(buffer).toString();
    
    // Extract WebSocket key
    Pattern keyPattern = Pattern.compile("Sec-WebSocket-Key: (.+)");
    Matcher matcher = keyPattern.matcher(request);
    
    if (matcher.find()) {
        String key = matcher.group(1).trim();
        String accept = generateAcceptKey(key);  // SHA-1 + Base64
        
        // Send upgrade response
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        
        channel.write(ByteBuffer.wrap(response.getBytes()));
        client.handshakeComplete = true;
    }
}
```

#### 2. Generate Accept Key (SHA-1)
```java
// Line ~111
private String generateAcceptKey(String key) {
    String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";  // RFC constant
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] hash = digest.digest((key + magic).getBytes());
    return Base64.getEncoder().encodeToString(hash);
}
```

#### 3. Frame Decoding
```java
// Line ~119: Decode WebSocket frame
private String decodeFrame(ByteBuffer buffer) {
    // Read first byte (FIN + opcode)
    buffer.get();
    
    // Read second byte (MASK + payload length)
    byte secondByte = buffer.get();
    boolean masked = (secondByte & 0x80) != 0;
    int payloadLength = secondByte & 0x7F;
    
    // Handle extended payload length
    if (payloadLength == 126) {
        payloadLength = buffer.getShort() & 0xFFFF;
    } else if (payloadLength == 127) {
        payloadLength = (int) buffer.getLong();
    }
    
    // Read masking key if present
    byte[] masks = new byte[4];
    if (masked) {
        buffer.get(masks);
    }
    
    // Read and unmask payload
    byte[] payload = new byte[payloadLength];
    buffer.get(payload);
    
    if (masked) {
        for (int i = 0; i < payload.length; i++) {
            payload[i] ^= masks[i % 4];  // XOR with mask
        }
    }
    
    return new String(payload, StandardCharsets.UTF_8);
}
```

#### 4. Frame Encoding
```java
// Line ~157: Encode and send message
private void sendMessage(SocketChannel channel, String message) {
    byte[] payload = message.getBytes(StandardCharsets.UTF_8);
    ByteBuffer frame = ByteBuffer.allocate(payload.length + 10);
    
    // First byte: FIN + text frame
    frame.put((byte) 0x81);
    
    // Payload length
    if (payload.length <= 125) {
        frame.put((byte) payload.length);
    } else if (payload.length <= 65535) {
        frame.put((byte) 126);
        frame.putShort((short) payload.length);
    } else {
        frame.put((byte) 127);
        frame.putLong(payload.length);
    }
    
    // Payload (server doesn't mask)
    frame.put(payload);
    frame.flip();
    
    channel.write(frame);
}
```

#### 5. Broadcasting
```java
// Line ~183: Broadcast to all connected clients
private void broadcast(String message, SocketChannel excludeChannel) {
    for (Map.Entry<SocketChannel, WebSocketClient> entry : clients.entrySet()) {
        if (entry.getValue().handshakeComplete && 
            !entry.getKey().equals(excludeChannel)) {
            sendMessage(entry.getKey(), message);
        }
    }
}
```

#### 6. Internal HTTP API for Backend Communication
```java
// Line ~236: HTTP endpoints for backend broadcasts
httpServer.createContext("/broadcast/player-join", 
    this::handlePlayerJoinBroadcast);
httpServer.createContext("/broadcast/game-status", 
    this::handleGameStatusBroadcast);
httpServer.createContext("/broadcast/score-update", 
    this::handleScoreUpdateBroadcast);
```

### 💻 Code Walkthrough - Client Side

#### 1. WebSocket Connection
```javascript
// websocket.js - Line ~15
connect(token) {
    this.ws = new WebSocket(this.url);  // ws://localhost:8082
    
    this.ws.onopen = () => {
        console.log('✅ WebSocket connected');
        this.connectionState = 'connected';
        this.emit('connected', {});
    };
    
    this.ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        this.emit(data.type, data.payload);  // Event emitter pattern
    };
}
```

#### 2. Event Listeners
```javascript
// Line ~57: Subscribe to events
on(event, callback) {
    if (!this.listeners.has(event)) {
        this.listeners.set(event, []);
    }
    this.listeners.get(event).push(callback);
}

// Usage in React:
useWebSocket('player_joined', (data) => {
    console.log('New player:', data.username);
});
```

#### 3. Sending Messages
```javascript
// Line ~68
send(type, payload) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type, payload }));
    }
}
```

### 🔬 Live Demonstration Steps

#### Step 1: Start WebSocket Server
```bash
# Terminal 1
java -cp target/classes com.netbattle.websocket.WebSocketGameServer
```

**Expected Output:**
```
🌐 WebSocket server started on port 8082
🔌 HTTP API server started on port 8083
```

#### Step 2: Test with Browser Console

**Open browser to:** `http://localhost:3000` (or any page)

**Browser Console:**
```javascript
// Create WebSocket connection
const ws = new WebSocket('ws://localhost:8082');

ws.onopen = () => {
    console.log('✅ Connected');
    ws.send(JSON.stringify({
        type: 'chat',
        payload: { message: 'Hello from browser!' }
    }));
};

ws.onmessage = (event) => {
    console.log('📨 Received:', event.data);
};
```

#### Step 3: Monitor WebSocket in Browser DevTools

**Chrome DevTools:**
1. Open DevTools (F12)
2. Go to **Network** tab
3. Click on WebSocket connection (WS)
4. View **Messages** tab

**What to Show:**
- Handshake request/response
- Sent messages (green arrow ↑)
- Received messages (red arrow ↓)
- Frame details

#### Step 4: Wireshark Analysis - WebSocket Handshake

**Wireshark Filter:**
```
tcp.port == 8082
```

**Handshake Request (from client):**
```http
GET / HTTP/1.1
Host: localhost:8082
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
```

**Handshake Response (from server):**
```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

**Key Points to Explain:**
- HTTP upgrade mechanism
- Sec-WebSocket-Key generation
- SHA-1 hash calculation
- Base64 encoding

#### Step 5: Wireshark Analysis - WebSocket Frames

**Filter for WebSocket protocol:**
```
websocket
```

**What to Observe:**

1. **Text Frame Structure:**
   ```
   Frame Header (2-14 bytes):
   - FIN (1 bit): 1
   - Opcode (4 bits): 1 (text frame)
   - MASK (1 bit): 1 (from client), 0 (from server)
   - Payload Length (7 bits or more)
   - Masking Key (4 bytes, if masked)
   
   Payload Data:
   - JSON message
   ```

2. **Ping/Pong Frames:**
   - Opcode: 9 (ping), 10 (pong)
   - Keep-alive mechanism

3. **Close Frame:**
   - Opcode: 8
   - Close code and reason

**Right-click packet → Follow → WebSocket Stream**
- Shows entire conversation
- All messages in order

#### Step 6: Demonstrate Broadcasting

**Terminal 2: Trigger broadcast via HTTP API**
```bash
curl -X POST http://localhost:8083/broadcast/player-join ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"TestPlayer\",\"currentCount\":1,\"minimumPlayers\":3}"
```

**What to Show:**
1. HTTP POST to port 8083
2. WebSocket server receives broadcast request
3. All connected WebSocket clients receive the message

**Browser Console (all tabs):**
```javascript
// All connected clients will receive:
{
  "type": "player_joined",
  "payload": {
    "username": "TestPlayer",
    "currentCount": 1,
    "minimumPlayers": 3
  }
}
```

#### Step 7: Load Testing - Multiple Connections

**HTML Test File: `websocket-test.html`**
```html
<!DOCTYPE html>
<html>
<head><title>WebSocket Test</title></head>
<body>
<h2>WebSocket Connection Test</h2>
<button onclick="createConnections()">Create 10 Connections</button>
<div id="log"></div>

<script>
let connections = [];

function createConnections() {
    for (let i = 0; i < 10; i++) {
        const ws = new WebSocket('ws://localhost:8082');
        
        ws.onopen = () => {
            log(`Connection ${i + 1}: OPEN`);
            ws.send(JSON.stringify({
                type: 'test',
                payload: { connectionId: i + 1 }
            }));
        };
        
        ws.onmessage = (event) => {
            log(`Connection ${i + 1}: ${event.data}`);
        };
        
        connections.push(ws);
    }
}

function log(message) {
    document.getElementById('log').innerHTML += 
        `<p>${new Date().toISOString()}: ${message}</p>`;
}
</script>
</body>
</html>
```

**What to Demonstrate:**
- All 10 connections established simultaneously
- Wireshark showing multiple WebSocket streams
- Server handling all connections with NIO Selector

### 📊 Performance Metrics

**Measure and Display:**
- **Connection Setup Time:** < 100ms
- **Message Latency:** < 10ms
- **Concurrent Connections:** 50+ supported
- **Frame Overhead:** 2-14 bytes per message

### 🎯 Key Concepts to Explain

1. **Full-Duplex Communication:**
   - Both client and server can send anytime
   - Unlike HTTP request-response

2. **Frame-based Protocol:**
   - Messages split into frames
   - Masking for security (client → server)

3. **Custom Implementation:**
   - No external WebSocket library used
   - Direct NIO implementation
   - Manual frame encoding/decoding

---

## Integration Demo

### 🎯 Full System Demonstration

This section shows how all components work together in a real scenario.

### 🔬 Complete Workflow Demonstration

#### Step 1: Start All Services

```bash
# Single command to start everything
scripts\start-fullstack.bat
```

**Services Starting:**
1. ✅ Service Discovery (8084)
2. ✅ REST API Server (8080)
3. ✅ WebSocket Bridge (8082)
4. ✅ NIO Manager (8081)
5. ✅ UDP Server (9000)
6. ✅ RMI Server (1099)
7. ✅ Frontend (3000)

#### Step 2: Wireshark Setup for Full Capture

**Capture Filter:**
```
tcp port 8080 or tcp port 8081 or tcp port 8082 or 
tcp port 8083 or tcp port 8084 or tcp port 1099 or 
udp port 9000 or udp port 9001
```

**Display Filters (use as needed):**
```
http                    # All HTTP traffic
websocket              # WebSocket frames
tcp.port == 8080       # REST API
tcp.port == 8082       # WebSocket
udp                    # UDP traffic
rmi                    # RMI calls
```

#### Step 3: User Login Flow

**Action: Login from browser**
1. Open `http://localhost:3000`
2. Enter username: "Player1"
3. Click Login

**What Happens (show in Wireshark):**

```
Browser → REST API (8080)
├─ POST /api/auth/login
├─ TCP handshake (SYN, SYN-ACK, ACK)
└─ HTTP POST with JSON {"username":"Player1"}

REST API → GameDataManager
├─ Register player
├─ Check username uniqueness
└─ Generate player ID and token

REST API → WebSocket (8083)
├─ POST /broadcast/player-join
└─ JSON: {"username":"Player1","currentCount":1}

WebSocket → All Clients (8082)
├─ Encode WebSocket frame
├─ Broadcast player_joined event
└─ All connected browsers receive update

Service Registry (8084)
├─ Receive heartbeat from all services
└─ Update service health status
```

**Wireshark Display Filter Sequence:**
```
tcp.port == 8080 and http.request.method == "POST"
tcp.port == 8083 and http.request.uri contains "broadcast"
tcp.port == 8082 and websocket
```

#### Step 4: Challenge Submission Flow

**Action: Submit answer to problem**
1. Go to Challenges page
2. Select problem #1
3. Submit answer: "42"

**Network Flow:**

```
1. Frontend → REST API (8080)
   POST /api/problems/submit
   Body: {"challengeId":"1","answer":"42"}
   
2. REST API validates answer
   └─ Checks GameDataManager
   
3. If correct:
   a) Update player score in GameDataManager
   b) Broadcast to WebSocket (8083)
      POST /broadcast/score-update
   c) Broadcast to WebSocket (8083)
      POST /broadcast/challenge-solved
   d) Broadcast to WebSocket (8083)
      POST /broadcast/leaderboard-update
   
4. WebSocket → All Clients (8082)
   ├─ Send score_update event
   ├─ Send challenge_solved event
   └─ Send leaderboard_update event
   
5. All browsers update:
   ├─ Player stats
   ├─ Leaderboard
   └─ Activity feed
```

#### Step 5: Demonstrate All Protocols Simultaneously

**Create Script: `test-all-protocols.bat`**
```bat
@echo off
echo Testing all network protocols...

echo.
echo 1. TCP - REST API Login
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"Player2\"}"

echo.
echo 2. HTTP - Service Discovery
curl http://localhost:8084/registry/services

echo.
echo 3. WebSocket - Send Message
REM Use browser console for this

echo.
echo 4. UDP - Send Event
java -cp target/classes UDPTestClient

echo.
echo Done! Check Wireshark for all protocols
pause
```

**Run with Wireshark capturing:**

**Statistics → Protocol Hierarchy**
Shows breakdown:
- TCP: X packets (HTTP, WebSocket)
- UDP: Y packets
- Others

#### Step 6: Performance Analysis

**Wireshark: Statistics → Conversations**
- Shows all connections
- Packets per connection
- Bytes transferred
- Duration

**Statistics → I/O Graph**
- Visual timeline of traffic
- Filter by protocol
- Shows burst patterns

### 📊 Complete Architecture Flow Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    BROWSER CLIENT                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │   HTTP   │  │WebSocket │  │   UDP    │              │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘              │
└───────┼─────────────┼─────────────┼────────────────────┘
        │             │             │
        ▼             ▼             ▼
┌─────────────────────────────────────────────────────────┐
│              NETWORK LAYER (TCP/UDP/IP)                  │
└─────────────────────────────────────────────────────────┘
        │             │             │
        ▼             ▼             ▼
┌─────────────────────────────────────────────────────────┐
│                    BACKEND SERVERS                       │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │TCP:8080  │  │WS:8082   │  │UDP:9000  │             │
│  │REST API  │◄─┤WebSocket │  │Events    │             │
│  │ (Sahan)  │  │(Thilina) │  │(Nithakshi│             │
│  └────┬─────┘  └────┬─────┘  └──────────┘             │
│       │             │                                   │
│  ┌────▼─────────────▼──────┐  ┌──────────┐            │
│  │   GameDataManager       │  │NIO:8081  │            │
│  │   (Shared State)        │  │(Sachith) │            │
│  └─────────┬───────────────┘  └──────────┘            │
│            │                                            │
│  ┌─────────▼────────┐  ┌──────────┐                   │
│  │Discovery:8084    │  │RMI:1099  │                   │
│  │Registry (Chiran) │  │(Chiran)  │                   │
│  └──────────────────┘  └──────────┘                   │
└─────────────────────────────────────────────────────────┘
```

---

## 📝 Viva Presentation Tips

### For Each Member:

**1. Introduction (1 min)**
- "I worked on [Component Name]"
- "It demonstrates [Network Concepts]"
- "Located in [File Path]"

**2. Code Explanation (2-3 min)**
- Show 2-3 key code snippets
- Explain network programming concepts
- Highlight challenging parts

**3. Live Demo (3-4 min)**
- Start your component
- Send test requests
- Show server response
- Point out key features

**4. Wireshark Analysis (3-4 min)**
- Apply filters
- Capture traffic
- Explain packet structure
- Show protocol specifics

**5. Integration (1-2 min)**
- "My component communicates with [Other Components]"
- Show integration points
- Explain data flow

### Recommended Order of Presentation:

1. **Sahan** (TCP) - Foundation server
2. **Thilina** (WebSocket) - Real-time layer
3. **Sachith** (NIO) - Efficient I/O
4. **Nithakshi** (UDP) - Low-latency events
5. **Chiran** (Discovery & RMI) - Service coordination

---

## 🎯 Quick Reference Commands

### Start Services
```bash
# All services
scripts\start-fullstack.bat

# Individual services
java -cp target/classes com.netbattle.api.RestApiServer
java -cp target/classes com.netbattle.websocket.WebSocketGameServer
java -cp target/classes com.netbattle.server.nio.NIOGameStateManager
java -cp target/classes com.netbattle.server.udp.UDPEventServer
java -cp target/classes com.netbattle.discovery.ServiceDiscoveryServer
java -cp target/classes com.netbattle.server.rmi.RMIServer
```

### Wireshark Filters
```
# By service
tcp.port == 8080              # REST API
tcp.port == 8082              # WebSocket
tcp.port == 8081              # NIO
udp.port == 9000              # UDP
tcp.port == 8084              # Discovery
tcp.port == 1099              # RMI

# By protocol
http
websocket
udp
rmi

# Combined
(tcp.port == 8080 || tcp.port == 8082) && http
```

### Test Commands
```bash
# Login
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"TestUser\"}"

# Get leaderboard
curl http://localhost:8080/api/leaderboard

# Service discovery
curl http://localhost:8084/registry/services

# WebSocket (use browser console)
new WebSocket('ws://localhost:8082')
```

---

## 📸 Screenshot Checklist

### For Each Member:

- [ ] Code screenshot (key implementation)
- [ ] Running server console
- [ ] Wireshark packet capture (filtered)
- [ ] Wireshark packet details
- [ ] Browser/client interaction
- [ ] Integration with other components

### Bonus Demonstrations:

- [ ] Load testing (multiple clients)
- [ ] Error handling
- [ ] Performance metrics
- [ ] Security features

---

## ⚠️ Common Issues & Solutions

### Wireshark Not Capturing
- Run as Administrator
- Select correct network adapter (usually Loopback)
- Check firewall settings

### Port Already in Use
```bash
# Find process using port
netstat -ano | findstr :8080
# Kill process
taskkill /PID <process_id> /F
```

### WebSocket Connection Failed
- Ensure server started first
- Check browser console for errors
- Verify port 8082 not blocked

### RMI Registry Issues
```bash
# Start RMI registry manually
start rmiregistry 1099
```

---

## 🎓 Expected Questions & Answers

### Q: Why use different protocols?
**A:** 
- TCP: Reliable, ordered delivery for critical data
- UDP: Low latency for real-time updates
- WebSocket: Full-duplex for live updates
- RMI: Distributed object access
- HTTP: Standard RESTful API

### Q: How does NIO differ from regular sockets?
**A:** 
- Non-blocking I/O
- Selector handles multiple connections
- Single thread can manage 500+ connections
- More memory efficient

### Q: Why implement WebSocket from scratch?
**A:** 
- Educational purpose
- Understanding protocol details
- No external dependencies
- Full control over implementation

### Q: How is multicast different from broadcast?
**A:**
- Multicast: Specific group (230.0.0.1)
- Broadcast: All devices on network
- Multicast is more efficient and controlled

---

## 📚 Additional Resources

### Protocol References
- **WebSocket RFC 6455:** https://tools.ietf.org/html/rfc6455
- **HTTP/1.1 RFC 2616:** https://tools.ietf.org/html/rfc2616
- **TCP RFC 793:** https://tools.ietf.org/html/rfc793
- **UDP RFC 768:** https://tools.ietf.org/html/rfc768

### Wireshark Documentation
- **Display Filters:** https://wiki.wireshark.org/DisplayFilters
- **Capture Filters:** https://wiki.wireshark.org/CaptureFilters

---

## ✅ Final Checklist

Before Viva:
- [ ] All services compile successfully
- [ ] All services start without errors
- [ ] Wireshark installed and tested
- [ ] Test scripts prepared
- [ ] Screenshots captured
- [ ] Code explanations prepared
- [ ] Integration demo practiced
- [ ] Backup plan if live demo fails

Good luck with your viva! 🎓
