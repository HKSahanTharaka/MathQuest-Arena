# 🎓 MathQuest Arena - Viva Preparation Guide

## 📋 Table of Contents
- [Overview](#overview)
- [Member 1: Sahan - TCP Server Core](#member-1-sahan---tcp-server-core)
- [Member 2: Sachith - NIO State Manager](#member-2-sachith---nio-state-manager)
- [Member 3: Nithakshi - UDP Event System](#member-3-nithakshi---udp-event-system)
- [Member 4: Chiran - Service Discovery/Registry & RMI Statistics](#member-4-chiran---service-discoveryregistry--rmi-statistics)
- [Member 5: Thilina - WebSocket Bridge](#member-5-thilina---websocket-bridge)
- [Common Questions for All Members](#common-questions-for-all-members)
- [System Integration Discussion](#system-integration-discussion)

---

## Overview

This guide helps each team member prepare for the viva session by outlining:
- **Key concepts** to understand
- **Code demonstrations** to prepare
- **Expected questions** and answers
- **Technical explanations** for their component
- **Integration points** with other components

**General Tips:**
- Run your component live during the demo
- Show the actual code and explain key sections
- Demonstrate error handling and edge cases
- Explain design decisions and trade-offs
- Show how your component integrates with others

---

## Member 1: Sahan - TCP Server Core

### 📌 Component Overview
**Port:** 8080  
**Files:** `GameServer.java`, `ClientHandler.java`  
**Responsibility:** Multi-threaded TCP server handling client connections, game logic, and session management

### 🎯 Key Concepts to Master

1. **TCP Sockets**
   - Connection-oriented protocol
   - Three-way handshake (SYN, SYN-ACK, ACK)
   - Reliable, ordered data delivery
   - ServerSocket vs Socket

2. **Multi-threading**
   - Thread-per-client model
   - ExecutorService and Thread Pools
   - Why 50 threads? (Resource management, optimal for expected load)
   - Difference between fixed vs cached thread pools

3. **Concurrency & Thread Safety**
   - ConcurrentHashMap for player storage
   - Synchronized blocks/methods
   - AtomicInteger for counters
   - Race conditions and deadlocks

### 📝 What to Demonstrate

#### 1. Server Startup Process
```java
// Show this code section
ServerSocket serverSocket = new ServerSocket(8080);
ExecutorService threadPool = Executors.newFixedThreadPool(50);

while (running) {
    Socket clientSocket = serverSocket.accept();
    threadPool.execute(new ClientHandler(clientSocket));
}
```

**Explain:**
- Why port 8080?
- How ServerSocket.accept() blocks until connection
- Thread pool prevents unlimited thread creation
- How ExecutorService manages thread lifecycle

#### 2. Client Connection Handling
```java
// ClientHandler constructor and run method
public void run() {
    try (BufferedReader in = new BufferedReader(...);
         PrintWriter out = new PrintWriter(...)) {
        
        // Authentication
        // Message processing loop
        // Cleanup on disconnect
    }
}
```

**Explain:**
- Try-with-resources for automatic cleanup
- BufferedReader/PrintWriter for text-based protocol
- Message format (JSON/plain text)
- How you handle client disconnection

#### 3. Concurrent Data Management
```java
// Show player storage
private static ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();

// Thread-safe operations
public synchronized void updateGameState(Player player) {
    // Critical section
}
```

**Explain:**
- Why ConcurrentHashMap vs HashMap?
- When to use synchronized blocks
- Avoiding race conditions in score updates
- Thread-safe collections (ConcurrentHashMap, CopyOnWriteArrayList)

### 🎤 Expected Questions & Answers

**Q1: Why use TCP instead of UDP for the main game server?**
> TCP ensures reliable delivery of game commands like login, challenge submission, and state updates. We can't afford to lose these critical messages. UDP is used separately for real-time position updates where occasional packet loss is acceptable.

**Q2: What happens if 100 clients connect simultaneously?**
> The thread pool has 50 threads. Connections 51-100 will queue until threads become available. The ServerSocket has a default backlog queue (typically 50). If that fills, new connections are refused with "Connection refused" error. We could increase thread pool size or implement a connection limit with graceful rejection.

**Q3: How do you prevent race conditions when updating player scores?**
> We use ConcurrentHashMap for player storage and synchronized methods for score updates. When multiple threads try to update the same player's score, the synchronized keyword ensures only one thread executes the critical section at a time.

**Q4: What's the difference between Executors.newFixedThreadPool(50) and newCachedThreadPool()?**
> - **FixedThreadPool:** Creates exactly 50 threads, reuses them. Excess tasks queue up. Predictable resource usage.
> - **CachedThreadPool:** Creates threads on demand, removes idle threads after 60s. Can grow unbounded, risking resource exhaustion.
> We chose fixed pool for predictable resource management.

**Q5: How do you handle client disconnections gracefully?**
> Each ClientHandler uses try-with-resources to auto-close sockets. In the finally block, we remove the player from active sessions, notify other players, and clean up resources. We catch SocketException for unexpected disconnects.

**Q6: Explain the message protocol between client and server.**
> We use JSON-formatted messages with a type field (LOGIN, SUBMIT_CHALLENGE, CHAT, etc.) and a payload. Example:
> ```json
> {"type": "SUBMIT_CHALLENGE", "challengeId": 1, "answer": 42}
> ```
> The server parses JSON, validates the type, and routes to appropriate handler.

**Q7: How does your server interact with other components?**
> - **REST API (8080):** Exposes HTTP endpoints for frontend
> - **NIO Manager (8081):** Sends state updates via HTTP/socket
> - **WebSocket (8082):** Notifies for real-time updates via HTTP API (8083)
> - **Service Registry (8084):** Registers on startup, sends heartbeats
> - **RMI (1099):** Calls for statistics updates

### 🔍 Deep Dive Topics

1. **Thread Pool Sizing**
   - Formula: Threads = CPU_cores * (1 + wait_time/compute_time)
   - Our choice: 50 threads for I/O-bound operations
   - Monitoring thread utilization

2. **Socket Options**
   - SO_TIMEOUT: Read timeout
   - SO_KEEPALIVE: TCP keepalive
   - TCP_NODELAY: Disable Nagle's algorithm for low latency

3. **Error Handling**
   - IOException: Network errors
   - SocketException: Connection reset
   - SocketTimeoutException: Read timeout
   - Graceful degradation strategies

### 💻 Live Demo Script

1. **Start the server:**
   ```powershell
   java -cp target/classes com.netbattle.server.core.GameServer
   ```
   Show log: "TCP Game Server started on port 8080"

2. **Connect multiple console clients:**
   ```powershell
   java -cp target/classes com.netbattle.client.GameClient
   ```
   Login with different usernames

3. **Show concurrent operations:**
   - Multiple clients submitting challenges simultaneously
   - View thread pool stats (if implemented)
   - Demonstrate thread safety with concurrent score updates

4. **Demonstrate error handling:**
   - Force disconnect a client (Ctrl+C)
   - Show server handles it gracefully
   - Other clients remain connected

5. **Show logs:**
   ```powershell
   type logs\tcp.log
   ```
   Point out connection logs, thread assignments, error handling

---

## Member 2: Sachith - NIO State Manager

### 📌 Component Overview
**Port:** 8081  
**Files:** `NIOGameStateManager.java`  
**Responsibility:** Non-blocking I/O for scalable state broadcasting to 1000+ concurrent connections

### 🎯 Key Concepts to Master

1. **NIO (New I/O) vs Traditional I/O**
   - Blocking I/O: One thread per connection
   - Non-blocking I/O: One thread handles many connections
   - Scalability comparison (thread-per-connection vs selector)

2. **Selector Pattern**
   - Multiplexing I/O operations
   - SelectionKey and interest operations (OP_ACCEPT, OP_READ, OP_WRITE)
   - Event-driven programming model

3. **Channels & Buffers**
   - ServerSocketChannel vs SocketChannel
   - ByteBuffer: position, limit, capacity
   - Direct vs heap buffers

4. **Non-blocking Mode**
   - configureBlocking(false)
   - Handling EAGAIN/EWOULDBLOCK
   - Partial reads/writes

### 📝 What to Demonstrate

#### 1. Selector Setup
```java
// Show initialization
Selector selector = Selector.open();
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);
serverChannel.socket().bind(new InetSocketAddress(8081));
serverChannel.register(selector, SelectionKey.OP_ACCEPT);
```

**Explain:**
- Selector acts as a multiplexer
- Non-blocking mode is crucial
- Registering interest in ACCEPT events
- Single thread monitors multiple channels

#### 2. Event Loop
```java
while (true) {
    selector.select(); // Blocks until events
    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    
    for (SelectionKey key : selectedKeys) {
        if (key.isAcceptable()) {
            handleAccept(key);
        } else if (key.isReadable()) {
            handleRead(key);
        } else if (key.isWritable()) {
            handleWrite(key);
        }
    }
    selectedKeys.clear();
}
```

**Explain:**
- selector.select() blocks until at least one channel is ready
- Different operations trigger different events
- Single thread handles all I/O operations
- Why clear selectedKeys?

#### 3. State Broadcasting
```java
public void broadcastGameState(GameState state) {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    buffer.put(state.toJson().getBytes());
    buffer.flip();
    
    for (SelectionKey key : selector.keys()) {
        if (key.channel() instanceof SocketChannel) {
            SocketChannel channel = (SocketChannel) key.channel();
            channel.write(buffer);
            buffer.rewind(); // Reset for next channel
        }
    }
}
```

**Explain:**
- Serialize state to JSON
- ByteBuffer operations: put, flip, rewind
- Broadcasting to all connected clients
- Handling partial writes

#### 4. ByteBuffer Management
```java
ByteBuffer buffer = ByteBuffer.allocate(1024);
int bytesRead = channel.read(buffer);
buffer.flip(); // Switch from writing to reading mode
byte[] data = new byte[buffer.remaining()];
buffer.get(data);
buffer.clear(); // Reset for next read
```

**Explain:**
- Position, limit, capacity pointers
- flip() switches from write to read mode
- clear() resets for next operation
- compact() for partial reads

### 🎤 Expected Questions & Answers

**Q1: Why is NIO more scalable than traditional I/O?**
> Traditional I/O requires one thread per connection. With 1000 clients, you need 1000 threads, causing high memory overhead and context switching. NIO uses a single thread with a Selector to monitor multiple channels. One thread can handle 500+ connections efficiently, reducing resource consumption.

**Q2: Explain the Selector pattern and how it works.**
> Selector is a multiplexer that monitors multiple channels for events (accept, read, write). Channels register their interest with the Selector. When you call select(), it blocks until at least one registered channel is ready. You then iterate through ready channels and perform operations. This is similar to epoll in Linux or IOCP in Windows.

**Q3: What's the difference between ByteBuffer.allocate() and allocateDirect()?**
> - **allocate():** Creates buffer in JVM heap. Subject to garbage collection. Slower for I/O.
> - **allocateDirect():** Creates buffer in native memory outside JVM heap. Faster for I/O operations (no copying between Java heap and native buffers). But slower to allocate/deallocate and not garbage collected.
> We use allocate() for simplicity unless I/O performance is critical.

**Q4: How do you handle partial reads/writes in non-blocking mode?**
> In non-blocking mode, read() or write() may not complete the entire operation. We check the return value:
> - **For reads:** Buffer remaining data until we have a complete message (e.g., JSON delimiter)
> - **For writes:** Store unwritten data and register OP_WRITE interest. When writable, continue writing.

**Q5: What happens if a client disconnects during a broadcast?**
> The write() operation will throw IOException or return -1. We catch this, cancel the SelectionKey, close the channel, and remove it from our client list. Other clients continue receiving the broadcast.

**Q6: Why is selector.select() better than selector.selectNow()?**
> - **select():** Blocks until at least one channel is ready. Efficient, saves CPU.
> - **select(timeout):** Blocks up to timeout milliseconds. Allows periodic tasks.
> - **selectNow():** Non-blocking, returns immediately. CPU-intensive if called in tight loop.
> We use select() to avoid busy-waiting and save CPU.

**Q7: How does your component integrate with the TCP server?**
> The TCP server handles game logic and receives player actions. When game state changes (score update, player joins), it sends a state update to our NIO manager via HTTP POST to port 8081 or direct method call. We then broadcast this state to all connected clients efficiently using NIO.

### 🔍 Deep Dive Topics

1. **Selector Internals**
   - Based on OS system calls (select, poll, epoll, kqueue)
   - Edge-triggered vs level-triggered
   - Wakeup mechanism for thread-safe selector modifications

2. **Buffer Management**
   - Buffer pooling to reduce allocations
   - Scatter/gather I/O (ScatteringByteChannel, GatheringByteChannel)
   - Memory-mapped files for large data

3. **Performance Optimization**
   - Direct buffers for high-throughput I/O
   - Buffer sizing strategies
   - SelectionKey attachment for stateful connections

4. **Concurrency Considerations**
   - Selector is not thread-safe
   - Wakeup() for cross-thread modifications
   - Lock-free message queues for state updates

### 💻 Live Demo Script

1. **Start the NIO manager:**
   ```powershell
   java -cp target/classes com.netbattle.server.nio.NIOGameStateManager
   ```
   Show log: "NIO State Manager started on port 8081"

2. **Connect monitoring tool:**
   ```powershell
   # Simulate clients
   telnet localhost 8081
   ```

3. **Trigger state broadcast:**
   - Submit challenge in frontend/console client
   - Show state update broadcast to all connected NIO clients
   - Display low latency (< 50ms)

4. **Demonstrate scalability:**
   - Show single thread handling multiple connections
   - Monitor thread count (should be 1-2 threads)
   - Compare to traditional I/O resource usage

5. **Show performance metrics:**
   - Messages per second throughput
   - Connection count
   - CPU usage (should be low)

---

## Member 3: Nithakshi - UDP Event System

### 📌 Component Overview
**Ports:** 9000 (unicast), 9001 (multicast)  
**Files:** `UDPEventServer.java`  
**Responsibility:** Real-time position updates and multicast group messaging with low latency

### 🎯 Key Concepts to Master

1. **UDP (User Datagram Protocol)**
   - Connectionless protocol
   - No handshake, no connection state
   - Unreliable, unordered delivery
   - Low overhead, low latency
   - When to use UDP vs TCP

2. **Multicast**
   - One-to-many communication
   - Multicast groups (224.0.0.0 to 239.255.255.255)
   - IGMP protocol
   - Efficient for broadcasting to multiple receivers

3. **Datagram Sockets**
   - DatagramSocket vs DatagramPacket
   - Packet size limitations (typically 65,507 bytes, practical limit ~1500 MTU)
   - No fragmentation handling at transport layer

4. **Real-time Considerations**
   - Packet loss tolerance
   - Latency vs reliability trade-off
   - Jitter and out-of-order delivery

### 📝 What to Demonstrate

#### 1. UDP Server Setup
```java
// Unicast socket
DatagramSocket socket = new DatagramSocket(9000);
byte[] buffer = new byte[1024];
DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

// Multicast socket
MulticastSocket multicastSocket = new MulticastSocket(9001);
InetAddress group = InetAddress.getByName("230.0.0.0");
multicastSocket.joinGroup(group);
```

**Explain:**
- No listen() or accept() like TCP
- Multicast group address selection
- Joining/leaving multicast groups
- Port binding

#### 2. Receiving Packets
```java
while (running) {
    socket.receive(packet); // Blocks until packet arrives
    
    String message = new String(packet.getData(), 0, packet.getLength());
    InetAddress clientAddress = packet.getAddress();
    int clientPort = packet.getPort();
    
    processPositionUpdate(message, clientAddress, clientPort);
}
```

**Explain:**
- Blocking receive() call
- Extracting sender information
- Packet data truncation (getLength() vs array length)
- Stateless nature (no connection tracking)

#### 3. Sending Unicast/Multicast
```java
// Unicast response
String response = "ACK";
byte[] data = response.getBytes();
DatagramPacket responsePacket = new DatagramPacket(
    data, data.length, clientAddress, clientPort
);
socket.send(responsePacket);

// Multicast broadcast
DatagramPacket multicastPacket = new DatagramPacket(
    eventData, eventData.length, group, 9001
);
multicastSocket.send(multicastPacket);
```

**Explain:**
- Unicast: Point-to-point response
- Multicast: Broadcast to all group members
- No delivery guarantee
- Efficiency of multicast (network layer duplication)

#### 4. Position Update Protocol
```java
// Position update format
public class PositionUpdate {
    String playerId;
    int x, y;
    long timestamp;
    
    public String serialize() {
        return String.format("%s:%d:%d:%d", playerId, x, y, timestamp);
    }
}

// High-frequency updates (e.g., 20 times/sec)
while (running) {
    PositionUpdate update = queue.poll();
    if (update != null) {
        sendUpdate(update);
    }
    Thread.sleep(50); // 20 updates/sec
}
```

**Explain:**
- Compact serialization (not JSON for performance)
- Timestamp for ordering/staleness detection
- Update frequency vs bandwidth trade-off
- Queue-based batching

### 🎤 Expected Questions & Answers

**Q1: Why use UDP instead of TCP for position updates?**
> Position updates are real-time and frequent (20/sec). UDP has lower latency (~1-5ms) vs TCP (~10-50ms) due to no handshake, no retransmission, and less overhead. For position updates, it's better to show the latest position than wait for retransmission of old packets. An occasional lost packet is acceptable.

**Q2: How do you handle packet loss in UDP?**
> We don't retransmit lost packets. Instead:
> 1. **High frequency:** Send updates 20 times/sec. If one is lost, the next arrives soon.
> 2. **Timestamps:** Receivers can detect and discard stale packets.
> 3. **Stateless:** Each packet is independent. Missing one doesn't corrupt state.
> 4. **Application-level ACKs:** For critical events, we can implement selective acknowledgment.

**Q3: What's the maximum packet size for UDP?**
> Theoretical max: 65,507 bytes (65,535 - 8 byte UDP header - 20 byte IP header). Practical limit: ~1,400-1,500 bytes to avoid IP fragmentation. Ethernet MTU is typically 1,500 bytes. We keep packets under 1,400 bytes to avoid fragmentation across different network paths.

**Q4: Explain multicast and how it's more efficient than broadcasting to each client.**
> Multicast uses a group address (e.g., 230.0.0.0). Clients join this group. When the server sends one packet to the group, routers duplicate it to all group members. This is network-layer efficiency vs application-layer: Instead of sending N packets (one per client), we send 1 packet and the network handles distribution.

**Q5: How does multicast group membership work?**
> Clients use IGMP (Internet Group Management Protocol) to join/leave groups. The router maintains a membership table. When a client joins (multicastSocket.joinGroup()), it sends an IGMP membership report. Routers periodically query for active members. When leaving, the client sends an IGMP leave message.

**Q6: What happens if packets arrive out of order?**
> UDP doesn't guarantee order. We handle this at the application level:
> 1. **Timestamps:** Each packet has a timestamp. Receiver can reorder or discard old packets.
> 2. **Sequence numbers:** Optional. Helps detect loss and reorder.
> 3. **Latest-wins:** For position updates, we simply use the latest received position.

**Q7: How do you integrate with other components?**
> When a player moves in the game (via TCP server), the server calls our UDP event system to broadcast position updates. The WebSocket bridge also receives these updates and forwards to web clients. We provide a simple API:
> ```java
> udpServer.broadcastPosition(playerId, x, y);
> ```

### 🔍 Deep Dive Topics

1. **UDP Socket Options**
   - SO_RCVBUF/SO_SNDBUF: Buffer sizes
   - SO_BROADCAST: Enable broadcast
   - SO_REUSEADDR: Allow multiple sockets on same port
   - IP_MULTICAST_TTL: Time-to-live for multicast packets

2. **Multicast Routing**
   - IGMP versions (v1, v2, v3)
   - Source-specific multicast (SSM)
   - Multicast DNS (mDNS)
   - Firewall and router configuration

3. **Performance Optimization**
   - Packet batching (send multiple updates in one packet)
   - Compression for larger payloads
   - UDP hole punching for NAT traversal
   - Congestion control (optional, application-level)

4. **Error Handling**
   - SocketException: Port already in use
   - PortUnreachableException: Recipient unavailable
   - Packet corruption detection (checksum)

### 💻 Live Demo Script

1. **Start the UDP server:**
   ```powershell
   java -cp target/classes com.netbattle.server.udp.UDPEventServer
   ```
   Show logs: "UDP Server started on port 9000", "Multicast on 230.0.0.0:9001"

2. **Send position updates:**
   - Use a test client or console client to move
   - Show updates sent via UDP
   - Display low latency (< 10ms)

3. **Demonstrate multicast:**
   - Connect multiple clients to multicast group
   - Broadcast an event
   - Show all clients receive it simultaneously

4. **Show packet loss handling:**
   - Simulate network issues (if possible)
   - Show system continues to function
   - Latest position displayed despite loss

5. **Monitor UDP traffic:**
   ```powershell
   netstat -an | findstr 9000
   ```
   Show active UDP listeners

---

## Member 4: Chiran - Service Discovery/Registry & RMI Statistics

### 📌 Component Overview
**Ports:** 8084 (Service Registry), 1099 (RMI)  
**Files:** `ServiceDiscoveryServer.java`, `ServiceRegistry.java`, `ServiceInfo.java`, `StatisticsService.java`, `StatisticsServiceImpl.java`, `RMIServer.java`  
**Responsibility:** Service registration, health monitoring, DNS-like resolution, and remote statistics via RMI

### 🎯 Key Concepts to Master

#### Part A: Service Discovery/Registry

1. **Service Discovery Pattern**
   - Service registration and deregistration
   - Health checks and heartbeats
   - DNS-like name resolution
   - Load balancing information

2. **RESTful API Design**
   - HTTP methods (GET, POST, PUT, DELETE)
   - Resource-based URLs
   - JSON request/response
   - Status codes (200, 201, 404, 500)

3. **Health Monitoring**
   - Heartbeat mechanism
   - TTL (Time-to-live)
   - Service status (UP, DOWN, UNKNOWN)
   - Automatic deregistration of dead services

#### Part B: RMI Statistics

1. **RMI (Remote Method Invocation)**
   - Client-server communication in Java
   - Stub and skeleton
   - RMI registry
   - Serialization

2. **Remote Interface**
   - Extends Remote
   - Methods throw RemoteException
   - Parameter and return type requirements

3. **Distributed Objects**
   - Object serialization
   - Pass-by-value vs pass-by-reference
   - Remote vs local objects

### 📝 What to Demonstrate

#### Part A: Service Discovery

##### 1. Service Registration
```java
@POST
@Path("/register")
public Response registerService(ServiceInfo serviceInfo) {
    String serviceId = UUID.randomUUID().toString();
    serviceInfo.setServiceId(serviceId);
    serviceInfo.setStatus("UP");
    serviceInfo.setLastHeartbeat(System.currentTimeMillis());
    
    registry.put(serviceId, serviceInfo);
    
    return Response.status(201).entity(serviceInfo).build();
}
```

**Explain:**
- RESTful endpoint design
- UUID for unique service IDs
- Initial status and heartbeat
- HTTP 201 (Created) response

##### 2. Health Monitoring
```java
@POST
@Path("/{serviceId}/heartbeat")
public Response heartbeat(@PathParam("serviceId") String serviceId) {
    ServiceInfo service = registry.get(serviceId);
    if (service == null) {
        return Response.status(404).build();
    }
    
    service.setLastHeartbeat(System.currentTimeMillis());
    service.setStatus("UP");
    
    return Response.ok().build();
}

// Background thread checks for stale services
private void checkServiceHealth() {
    long now = System.currentTimeMillis();
    for (ServiceInfo service : registry.values()) {
        if (now - service.getLastHeartbeat() > 30000) { // 30 sec timeout
            service.setStatus("DOWN");
        }
    }
}
```

**Explain:**
- Heartbeat endpoint updates last seen time
- Background thread monitors staleness
- 30-second timeout threshold
- Status transitions (UP → DOWN)

##### 3. Service Discovery
```java
@GET
@Path("/discover/{serviceName}")
public Response discoverService(@PathParam("serviceName") String serviceName) {
    List<ServiceInfo> services = registry.values().stream()
        .filter(s -> s.getName().equals(serviceName))
        .filter(s -> "UP".equals(s.getStatus()))
        .collect(Collectors.toList());
    
    if (services.isEmpty()) {
        return Response.status(404).build();
    }
    
    // Load balancing: return least loaded service
    ServiceInfo selectedService = services.stream()
        .min(Comparator.comparingInt(ServiceInfo::getLoad))
        .orElse(services.get(0));
    
    return Response.ok(selectedService).build();
}
```

**Explain:**
- DNS-like name resolution
- Filtering by status (only UP services)
- Load balancing logic
- Returning service connection details

#### Part B: RMI Statistics

##### 1. Remote Interface Definition
```java
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface StatisticsService extends Remote {
    List<PlayerStats> getLeaderboard() throws RemoteException;
    PlayerStats getPlayerStats(String username) throws RemoteException;
    List<String> getAchievements(String username) throws RemoteException;
    void updateScore(String username, int score) throws RemoteException;
}
```

**Explain:**
- Extends Remote interface
- All methods throw RemoteException
- Parameters and return types must be serializable
- This is the contract for remote calls

##### 2. Remote Object Implementation
```java
import java.rmi.server.UnicastRemoteObject;

public class StatisticsServiceImpl extends UnicastRemoteObject 
    implements StatisticsService {
    
    private Map<String, PlayerStats> statsMap = new ConcurrentHashMap<>();
    
    public StatisticsServiceImpl() throws RemoteException {
        super();
    }
    
    @Override
    public List<PlayerStats> getLeaderboard() throws RemoteException {
        return statsMap.values().stream()
            .sorted(Comparator.comparingInt(PlayerStats::getScore).reversed())
            .limit(10)
            .collect(Collectors.toList());
    }
    
    @Override
    public synchronized void updateScore(String username, int score) 
        throws RemoteException {
        PlayerStats stats = statsMap.get(username);
        if (stats != null) {
            stats.addScore(score);
        }
    }
}
```

**Explain:**
- Extends UnicastRemoteObject
- Constructor throws RemoteException
- Thread-safe operations (synchronized, ConcurrentHashMap)
- Business logic implementation

##### 3. RMI Server Setup
```java
public class RMIServer {
    public static void main(String[] args) {
        try {
            // Create registry
            Registry registry = LocateRegistry.createRegistry(1099);
            
            // Create remote object
            StatisticsService service = new StatisticsServiceImpl();
            
            // Bind to registry
            registry.rebind("StatisticsService", service);
            
            System.out.println("RMI Server ready on port 1099");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

**Explain:**
- Creating RMI registry on port 1099
- Instantiating remote object
- Binding with a name
- Registry.rebind vs Registry.bind

##### 4. RMI Client Usage
```java
// In other components (TCP server, WebSocket)
Registry registry = LocateRegistry.getRegistry("localhost", 1099);
StatisticsService stats = (StatisticsService) registry.lookup("StatisticsService");

// Make remote calls
List<PlayerStats> leaderboard = stats.getLeaderboard();
stats.updateScore("player1", 100);
```

**Explain:**
- Locating registry
- Looking up remote object by name
- Stub creation (automatic)
- Remote method calls look like local calls

### 🎤 Expected Questions & Answers

#### Service Discovery Questions

**Q1: Why do we need a service registry?**
> In a distributed system, services need to discover each other without hardcoding IP addresses. The registry acts as a centralized directory where services register their location. Other services query the registry to find available instances, enabling dynamic service discovery and load balancing.

**Q2: How does the heartbeat mechanism work?**
> Each service periodically (e.g., every 10 seconds) sends a POST request to `/api/services/{serviceId}/heartbeat`. This updates the `lastHeartbeat` timestamp. A background thread checks if `(currentTime - lastHeartbeat) > 30 seconds`. If true, the service is marked DOWN and won't be returned in discovery requests.

**Q3: How do you handle service failures?**
> When a service crashes, it stops sending heartbeats. After 30 seconds without a heartbeat, our health check marks it as DOWN. Discovery requests filter out DOWN services. If the service restarts, it re-registers with a new ID or updates its status with a heartbeat.

**Q4: What's the difference between service discovery and DNS?**
> DNS maps domain names to IP addresses. Service discovery is similar but includes additional metadata like service health, load, version, and protocol. It also supports health monitoring and dynamic updates, while DNS has longer TTL and caching delays.

#### RMI Statistics Questions

**Q5: Explain how RMI works internally.**
> RMI has three layers:
> 1. **Stub layer:** Client-side proxy. Marshals method calls into network format.
> 2. **Remote reference layer:** Manages remote object references and connections.
> 3. **Transport layer:** TCP-based network communication.
> 
> When you call a method, the stub serializes parameters, sends to server. Server skeleton deserializes, invokes actual method, serializes result, sends back. Stub deserializes and returns to caller.

**Q6: What are the advantages and disadvantages of RMI?**
> **Advantages:**
> - Simple API (looks like local method calls)
> - Built-in serialization
> - Java-to-Java communication
> - Object passing (pass-by-value for serializable objects)
> 
> **Disadvantages:**
> - Java-only (not language-agnostic like HTTP/gRPC)
> - Firewall issues (dynamic ports)
> - Less flexible than REST
> - Performance overhead of serialization

**Q7: Why use RMI for statistics instead of HTTP REST API?**
> RMI provides a natural object-oriented interface. Methods can return complex Java objects (PlayerStats, List<Achievements>) without manual JSON serialization. For Java-to-Java communication within our backend, RMI is simpler than REST. However, the frontend uses REST API, which we also expose.

**Q8: How do you handle concurrency in the statistics service?**
> We use ConcurrentHashMap for the stats storage and synchronized methods for updates. When multiple game servers call updateScore() simultaneously for the same player, synchronization ensures atomic updates. ConcurrentHashMap handles concurrent reads efficiently.

**Q9: What happens if the RMI registry crashes?**
> Existing client connections continue to work (clients hold stubs directly). New clients can't discover services until the registry restarts. In production, we'd implement registry replication or use a robust service registry like Consul or Eureka.

### 🔍 Deep Dive Topics

#### Service Registry

1. **Distributed Consensus**
   - CAP theorem (Consistency, Availability, Partition tolerance)
   - Our registry prioritizes Availability over Consistency
   - Single point of failure concerns

2. **Load Balancing Strategies**
   - Round-robin
   - Least connections
   - Least load (our approach)
   - Weighted distribution

3. **Service Versioning**
   - Semantic versioning
   - Backward compatibility
   - Blue-green deployments

#### RMI

1. **Serialization**
   - ObjectOutputStream/ObjectInputStream
   - serialVersionUID
   - Transient fields
   - Custom serialization

2. **Security**
   - RMI Security Manager (deprecated in Java 17)
   - Code signing
   - SSL/TLS for RMI
   - Firewall configuration

3. **Performance**
   - Compression for large objects
   - Connection pooling
   - Timeouts and retry logic

### 💻 Live Demo Script

#### Part A: Service Registry

1. **Start the service registry:**
   ```powershell
   java -cp target/classes com.netbattle.discovery.ServiceDiscoveryServer
   ```
   Show log: "Service Discovery Server started on port 8084"

2. **Register a service (using curl or Postman):**
   ```powershell
   curl -X POST http://localhost:8084/api/services/register ^
     -H "Content-Type: application/json" ^
     -d "{\"name\":\"GameServer\",\"host\":\"localhost\",\"port\":8080}"
   ```
   Show the response with service ID

3. **Send heartbeats:**
   ```powershell
   curl -X POST http://localhost:8084/api/services/{serviceId}/heartbeat
   ```

4. **Discover a service:**
   ```powershell
   curl http://localhost:8084/api/services/discover/GameServer
   ```
   Show the service details returned

5. **List all services:**
   ```powershell
   curl http://localhost:8084/api/services
   ```
   Show registered services and their status

#### Part B: RMI Statistics

1. **Start the RMI server:**
   ```powershell
   java -cp target/classes com.netbattle.server.rmi.RMIServer
   ```
   Show log: "RMI Server ready on port 1099"

2. **Test from console client:**
   - Login and solve challenges
   - Use console command: `leaderboard`
   - Show RMI call retrieving top players

3. **Show remote method call in code:**
   ```java
   // In TCP server
   StatisticsService stats = getRMIService();
   stats.updateScore(username, points);
   ```
   Point out this calls the remote object

4. **Show serialization:**
   - Display PlayerStats class with Serializable
   - Explain how objects are transmitted over network

---

## Member 5: Thilina - WebSocket Bridge

### 📌 Component Overview
**Ports:** 8082 (WebSocket), 8083 (Internal HTTP API)  
**Files:** `WebSocketGameServer.java`  
**Responsibility:** Real-time bidirectional communication, event broadcasting, chat, and backend-to-backend API

### 🎯 Key Concepts to Master

1. **WebSocket Protocol**
   - Upgrade from HTTP to WebSocket
   - Full-duplex communication
   - Frame-based messaging
   - Difference from HTTP polling/long polling
   - Persistent connection

2. **WebSocket Handshake**
   - HTTP Upgrade request
   - Sec-WebSocket-Key and Sec-WebSocket-Accept
   - Protocol switching (101 Switching Protocols)

3. **Real-time Communication**
   - Push vs pull model
   - Event-driven architecture
   - Message broadcasting
   - Pub/Sub pattern

4. **WebSocket Frames**
   - Text frames vs binary frames
   - Ping/Pong for keep-alive
   - Close frames
   - Fragmentation for large messages

### 📝 What to Demonstrate

#### 1. WebSocket Server Setup
```java
import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;

public class WebSocketGameServer extends WebSocketServer {
    
    private Set<WebSocket> clients = new ConcurrentHashMap().newKeySet();
    
    public WebSocketGameServer(int port) {
        super(new InetSocketAddress(port));
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        System.out.println("New connection: " + conn.getRemoteSocketAddress());
        
        // Send welcome message
        conn.send("{\"type\":\"CONNECTED\",\"message\":\"Welcome!\"}");
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        // Handle incoming messages
        processMessage(conn, message);
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }
}
```

**Explain:**
- Extending WebSocketServer class
- ConcurrentHashMap.newKeySet() for thread-safe client storage
- Lifecycle methods: onOpen, onClose, onMessage, onError
- Sending messages to individual clients

#### 2. Broadcasting to All Clients
```java
public void broadcast(String message) {
    for (WebSocket client : clients) {
        if (client.isOpen()) {
            client.send(message);
        }
    }
}

// Broadcast game state update
public void broadcastGameState(GameState state) {
    String json = new Gson().toJson(Map.of(
        "type", "GAME_STATE_UPDATE",
        "data", state
    ));
    broadcast(json);
}
```

**Explain:**
- Iterating over all connected clients
- Checking client.isOpen() before sending
- JSON message format with type and data
- Avoiding sending to closed connections

#### 3. Message Handling
```java
private void processMessage(WebSocket conn, String message) {
    try {
        JsonObject json = JsonParser.parseString(message).getAsJsonObject();
        String type = json.get("type").getAsString();
        
        switch (type) {
            case "CHAT":
                handleChat(conn, json);
                break;
            case "SUBSCRIBE_UPDATES":
                subscribeClient(conn);
                break;
            case "PING":
                conn.send("{\"type\":\"PONG\"}");
                break;
            default:
                conn.send("{\"type\":\"ERROR\",\"message\":\"Unknown type\"}");
        }
    } catch (Exception e) {
        conn.send("{\"type\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
    }
}

private void handleChat(WebSocket sender, JsonObject json) {
    String username = json.get("username").getAsString();
    String chatMessage = json.get("message").getAsString();
    
    // Broadcast to all except sender
    String broadcastMsg = new Gson().toJson(Map.of(
        "type", "CHAT",
        "username", username,
        "message", chatMessage,
        "timestamp", System.currentTimeMillis()
    ));
    
    for (WebSocket client : clients) {
        if (client != sender && client.isOpen()) {
            client.send(broadcastMsg);
        }
    }
}
```

**Explain:**
- Parsing JSON messages
- Type-based routing (switch statement)
- Chat broadcasting (excluding sender)
- Error handling with try-catch

#### 4. Internal HTTP API for Backend Communication
```java
// HTTP server for backend-to-backend communication
import com.sun.net.httpserver.HttpServer;

private void startHttpApi() {
    try {
        HttpServer server = HttpServer.create(new InetSocketAddress(8083), 0);
        
        server.createContext("/api/broadcast", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes());
                
                // Broadcast to all WebSocket clients
                broadcast(body);
                
                String response = "{\"status\":\"ok\"}";
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            }
        });
        
        server.setExecutor(null);
        server.start();
        System.out.println("HTTP API started on port 8083");
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

**Explain:**
- HTTP server for backend services to push updates
- POST endpoint for broadcasting
- Backend servers (TCP, NIO) call this to trigger WebSocket broadcasts
- Bridge between HTTP and WebSocket

#### 5. Connection Management
```java
// Ping clients periodically to keep connection alive
private void startKeepAlive() {
    new Thread(() -> {
        while (true) {
            try {
                Thread.sleep(30000); // Every 30 seconds
                
                for (WebSocket client : clients) {
                    if (client.isOpen()) {
                        client.send("{\"type\":\"PING\"}");
                    } else {
                        clients.remove(client);
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }).start();
}

// Client-side should respond with PONG
// If no PONG received in 10 seconds, consider connection dead
```

**Explain:**
- Periodic ping to detect dead connections
- Removing stale connections
- Prevents accumulation of closed sockets
- Client heartbeat mechanism

### 🎤 Expected Questions & Answers

**Q1: What are the advantages of WebSocket over HTTP polling?**
> **HTTP Polling:** Client repeatedly sends requests (e.g., every second) asking "any updates?". Wastes bandwidth, high latency (up to 1 second), server overhead from constant connections.
> 
> **WebSocket:** Single persistent connection. Server pushes updates instantly. Low latency (< 50ms), less bandwidth, fewer connections. Full-duplex allows client and server to send simultaneously.

**Q2: Explain the WebSocket handshake process.**
> 1. Client sends HTTP GET request with `Upgrade: websocket` header
> 2. Includes `Sec-WebSocket-Key` (random base64-encoded value)
> 3. Server responds with `101 Switching Protocols`
> 4. Returns `Sec-WebSocket-Accept` (hash of key + magic GUID)
> 5. Connection upgrades from HTTP to WebSocket protocol
> 6. Now bidirectional communication begins

**Q3: How do you handle disconnections?**
> We detect disconnections through:
> 1. **onClose() callback:** WebSocket library notifies us
> 2. **Ping/Pong:** Send ping every 30s. If client doesn't respond, connection is dead
> 3. **Exception on send:** If send() throws exception, connection is broken
> 
> When detected, we remove the client from our Set and clean up resources.

**Q4: What's the difference between text and binary frames in WebSocket?**
> - **Text frames:** UTF-8 encoded strings. Used for JSON, XML, plain text messages. Automatically validated as UTF-8.
> - **Binary frames:** Raw bytes. Used for images, files, compressed data, custom protocols. More efficient for non-text data.
> 
> We use text frames with JSON for readability and debugging.

**Q5: How does the internal HTTP API (port 8083) work?**
> Other backend services (TCP server, NIO manager, UDP) can't directly access WebSocket connections. So we expose an HTTP API on port 8083. When game state changes, they POST to `/api/broadcast` with the update. Our WebSocket server receives this and broadcasts to all connected web clients. This bridges HTTP backend with WebSocket frontend.

**Q6: How do you ensure thread safety with multiple clients?**
> We use `ConcurrentHashMap.newKeySet()` for storing clients. This is thread-safe for add/remove operations. The WebSocket library handles each connection in its own thread. When broadcasting, we iterate the Set (thread-safe read) and send to each client. The WebSocket library handles concurrent sends internally.

**Q7: What happens if a client's network is slow and can't receive messages fast enough?**
> Messages queue up in the WebSocket's send buffer. If the buffer fills (default ~64KB), send() will block or fail depending on configuration. We handle this by:
> 1. **Non-blocking send:** Check if writable before sending
> 2. **Drop slow clients:** If buffer exceeds limit, disconnect them
> 3. **Rate limiting:** Don't broadcast too frequently (max 20 updates/sec)

**Q8: How do you integrate with the frontend React application?**
> The React app creates a WebSocket connection in `useWebSocket.js`:
> ```javascript
> const ws = new WebSocket('ws://localhost:8082');
> ws.onmessage = (event) => {
>   const data = JSON.parse(event.data);
>   if (data.type === 'GAME_STATE_UPDATE') {
>     updateDashboard(data.data);
>   }
> };
> ```
> We send JSON messages with a `type` field. React parses and routes to appropriate handlers.

### 🔍 Deep Dive Topics

1. **WebSocket Security**
   - WSS (WebSocket Secure) over TLS
   - Origin validation (CORS for WebSocket)
   - Authentication tokens
   - Rate limiting to prevent abuse

2. **Scalability**
   - Connection limits per server (typically 10,000+)
   - Horizontal scaling with message brokers (Redis Pub/Sub, RabbitMQ)
   - Sticky sessions for load balancing
   - WebSocket clustering

3. **Message Formats**
   - JSON vs MessagePack vs Protocol Buffers
   - Schema validation
   - Versioning messages
   - Compression

4. **Error Handling**
   - Reconnection strategies (exponential backoff)
   - Message queuing during reconnection
   - Duplicate message handling
   - Timeout handling

5. **Performance Optimization**
   - Message batching (send multiple updates in one frame)
   - Debouncing rapid updates
   - Selective broadcasting (only to subscribed clients)
   - Memory management for large client counts

### 💻 Live Demo Script

1. **Start the WebSocket server:**
   ```powershell
   java -cp target/classes com.netbattle.websocket.WebSocketGameServer
   ```
   Show logs: "WebSocket server started on port 8082", "HTTP API on port 8083"

2. **Connect frontend:**
   - Open React app (http://localhost:3000)
   - Login
   - Open browser developer tools → Network → WS tab
   - Show WebSocket connection established

3. **Demonstrate real-time chat:**
   - Send chat message from one client
   - Show instant delivery to other clients
   - Display WebSocket frames in dev tools

4. **Demonstrate game state updates:**
   - Submit a challenge
   - Show dashboard update in real-time
   - Point out WebSocket message in dev tools

5. **Test backend HTTP API:**
   ```powershell
   curl -X POST http://localhost:8083/api/broadcast ^
     -H "Content-Type: application/json" ^
     -d "{\"type\":\"ANNOUNCEMENT\",\"message\":\"Server maintenance in 5 min\"}"
   ```
   Show message appears in all connected frontends

6. **Demonstrate connection status:**
   - Show "Connected" indicator in navbar (green)
   - Stop WebSocket server
   - Show "Disconnected" indicator (red)
   - Restart server
   - Show automatic reconnection

7. **Monitor WebSocket connections:**
   ```powershell
   netstat -an | findstr 8082
   ```
   Show multiple ESTABLISHED connections

---

## Common Questions for All Members

### General Architecture

**Q1: How do all the components work together?**
> **Flow Example - Player submits a challenge:**
> 1. Frontend sends HTTP POST to TCP Server (8080)
> 2. TCP Server validates answer, updates score in RMI (1099)
> 3. TCP Server sends state update to NIO Manager (8081)
> 4. NIO Manager broadcasts new state to monitoring clients
> 5. TCP Server calls WebSocket HTTP API (8083) to notify web clients
> 6. WebSocket (8082) pushes real-time update to React frontend
> 7. UDP Server (9000) broadcasts position update if player moved
> 8. Service Registry (8084) tracks all service health

**Q2: Why did we choose this architecture?**
> We wanted to demonstrate multiple network programming concepts:
> - **TCP:** Reliable communication for critical operations
> - **UDP:** Low-latency for real-time updates
> - **NIO:** Scalability with limited resources
> - **WebSocket:** Modern real-time web communication
> - **RMI:** Java distributed objects
> - **Service Registry:** Microservices pattern
> 
> This showcases different protocol choices for different use cases.

**Q3: What are the scalability limits of your system?**
> - **TCP Server:** Limited by thread pool (50 threads). Can handle ~50 concurrent operations.
> - **NIO Manager:** Can handle 1000+ connections with single thread.
> - **UDP Server:** Can process 10,000+ packets/sec. Limited by network bandwidth.
> - **WebSocket:** ~10,000 connections per server instance.
> - **RMI:** Limited by network latency and serialization overhead.
> - **Service Registry:** Single instance is bottleneck. Would need replication for production.

### Performance & Optimization

**Q4: How would you optimize your component for production?**
> **Sahan (TCP):** Increase thread pool size, implement connection pooling, add request queue with backpressure.
> 
> **Sachith (NIO):** Use direct buffers, implement zero-copy transfers, optimize buffer sizes based on MTU.
> 
> **Nithakshi (UDP):** Compress packets, batch multiple updates, implement forward error correction (FEC).
> 
> **Chiran (Registry/RMI):** Implement distributed registry (Raft consensus), add caching layer, compress RMI objects.
> 
> **Thilina (WebSocket):** Use Redis Pub/Sub for horizontal scaling, implement message batching, add binary protocol option.

**Q5: How do you handle errors and failures?**
> Each component has:
> - **Exception handling:** Try-catch blocks, proper logging
> - **Resource cleanup:** Finally blocks, try-with-resources
> - **Graceful degradation:** Continue operating with reduced functionality
> - **Retry logic:** For transient failures
> - **Circuit breakers:** Prevent cascade failures
> - **Health checks:** Detect and isolate failing components

### Security

**Q6: What security measures did you implement?**
> - **TCP Server:** SSL/TLS encryption, session tokens, input validation
> - **NIO Manager:** Authentication before broadcasting, rate limiting
> - **UDP Server:** Source validation, packet signature (optional)
> - **Service Registry:** API key authentication, HTTPS
> - **RMI:** Security manager (deprecated), firewall rules
> - **WebSocket:** Origin validation, WSS (WebSocket Secure), token-based auth

**Q7: How would you prevent DDoS attacks?**
> - **Connection limits:** Max connections per IP
> - **Rate limiting:** Max requests per second
> - **Authentication:** Require valid credentials
> - **Firewall rules:** Drop malicious traffic
> - **Load balancing:** Distribute traffic across servers
> - **Monitoring:** Detect unusual patterns

### Testing & Debugging

**Q8: How did you test your component?**
> - **Unit tests:** Test individual methods
> - **Integration tests:** Test component interaction
> - **Load tests:** Simulate many concurrent clients
> - **Stress tests:** Test beyond normal capacity
> - **Network simulation:** Introduce latency, packet loss
> - **Logging:** Detailed logs for debugging

**Q9: What tools did you use for development?**
> - **IDE:** IntelliJ IDEA / Eclipse / VS Code
> - **Build:** Maven for dependency management
> - **Testing:** JUnit, Wireshark for packet analysis
> - **Monitoring:** netstat, Task Manager, VisualVM
> - **Frontend:** React DevTools, Chrome Developer Tools
> - **Version Control:** Git

---

## System Integration Discussion

### Data Flow Examples

#### Example 1: Player Login
```
1. Player enters username in React frontend
2. Frontend → WebSocket (8082): {"type":"LOGIN","username":"player1"}
3. WebSocket → TCP Server (8080): Forward login request
4. TCP Server: Validate username uniqueness
5. TCP Server → Service Registry (8084): Register player session
6. TCP Server → RMI (1099): Create player stats object
7. TCP Server → WebSocket (8082): Confirm login
8. WebSocket → Frontend: {"type":"LOGIN_SUCCESS","token":"..."}
9. Frontend: Navigate to dashboard
```

#### Example 2: Challenge Submission
```
1. Player submits answer in frontend
2. Frontend → TCP Server (8080): POST /api/challenges/{id}/submit
3. TCP Server: Validate answer
4. TCP Server → RMI (1099): stats.updateScore(username, points)
5. RMI: Update player stats, check achievements
6. TCP Server → NIO Manager (8081): broadcastGameState(newState)
7. NIO Manager: Broadcast to all monitoring clients
8. TCP Server → WebSocket HTTP API (8083): POST /api/broadcast
9. WebSocket → All frontends: {"type":"SCORE_UPDATE", "data":...}
10. Frontend: Update dashboard, leaderboard in real-time
```

#### Example 3: Position Update
```
1. Player moves in game (frontend)
2. Frontend → UDP Server (9000): Position packet (x, y, timestamp)
3. UDP Server: Validate and parse
4. UDP Server → Multicast (9001): Broadcast to group
5. All subscribed clients: Receive position update
6. Clients: Update player position on screen
7. (Occasional packet loss is acceptable)
```

### Inter-Component Communication Matrix

| From ↓ To → | TCP | NIO | UDP | WebSocket | RMI | Registry |
|-------------|-----|-----|-----|-----------|-----|----------|
| **TCP**     | -   | HTTP| Call| HTTP API  | RMI | REST API |
| **NIO**     | -   | -   | -   | -         | -   | REST API |
| **UDP**     | -   | -   | -   | -         | -   | -        |
| **WebSocket**| HTTP| -   | -   | -         | -   | REST API |
| **RMI**     | -   | -   | -   | -         | -   | -        |
| **Registry**| -   | -   | -   | -         | -   | -        |

### Critical Integration Points

1. **TCP ↔ WebSocket:** HTTP API on port 8083 for pushing updates
2. **TCP ↔ RMI:** Direct RMI calls for statistics
3. **TCP ↔ NIO:** HTTP/socket calls for state broadcasts
4. **All ↔ Registry:** REST API for registration and discovery
5. **Frontend ↔ WebSocket:** Persistent WebSocket connection
6. **Frontend ↔ TCP:** RESTful HTTP API

---

## Final Presentation Tips

### For Each Member

1. **Start with Overview:**
   - Component name and purpose
   - Ports used
   - Key technologies

2. **Explain Core Concept:**
   - Why this protocol/pattern?
   - Advantages and trade-offs
   - Real-world use cases

3. **Live Code Demonstration:**
   - Show key code sections
   - Explain design decisions
   - Run the component live

4. **Integration:**
   - How does it connect to other components?
   - Show message flow
   - Demonstrate end-to-end feature

5. **Handle Questions:**
   - Be honest if you don't know
   - Explain your learning process
   - Discuss what you'd do differently

### Common Mistakes to Avoid

- ❌ Not understanding basic concepts (TCP vs UDP, blocking vs non-blocking)
- ❌ Unable to explain your own code
- ❌ Not testing before the viva
- ❌ Ignoring error handling and edge cases
- ❌ Not knowing how components integrate
- ❌ Over-promising ("This can handle millions of users!")

### Good Practices

- ✅ Explain trade-offs (why you chose X over Y)
- ✅ Show awareness of limitations
- ✅ Demonstrate running code, not just slides
- ✅ Use diagrams to explain complex flows
- ✅ Connect theory to your implementation
- ✅ Show enthusiasm and understanding

---

## Additional Resources

### Recommended Reading

- **Java Network Programming** by Elliotte Rusty Harold
- **Java Concurrency in Practice** by Brian Goetz
- **WebSocket API** - MDN Web Docs
- **RMI Tutorial** - Oracle Java Tutorials

### Online Resources

- **TCP/IP Guide:** https://www.tcpipguide.com/
- **NIO Tutorial:** https://jenkov.com/tutorials/java-nio/
- **WebSocket Protocol RFC 6455:** https://tools.ietf.org/html/rfc6455
- **RMI Documentation:** https://docs.oracle.com/javase/tutorial/rmi/

---

**Good luck with your viva! Remember: understanding concepts is more important than memorizing code.** 🎓✨
