# 🎯 VIVA Quick Reference Card

## For Each Team Member

---

## Member 1: Sahan - TCP Game Server

### 🎤 What to Say:
"I implemented the TCP-based REST API server that handles user authentication, game logic, and session management. It uses multi-threading with a 50-thread pool to handle concurrent client connections efficiently."

### 📝 Key Code Points:
- **File:** `src/main/java/com/netbattle/api/RestApiServer.java`
- **Line 26:** HttpServer creation with ExecutorService
- **Line 30-40:** REST endpoint registration
- **Concept:** Thread pool pattern for concurrent connections

### 🖥️ Demo Command:
```bash
# Terminal 1: Start server
java -cp target/classes com.netbattle.api.RestApiServer

# Terminal 2: Test
java TestTcpClient
```

### 🦈 Wireshark Filter:
```
tcp.port == 8080 && http
```

### 🎯 What to Show:
1. TCP 3-way handshake (SYN → SYN-ACK → ACK)
2. HTTP POST request with JSON payload
3. Server response (200 OK)
4. Multiple concurrent connections

---

## Member 2: Sachith - NIO State Manager

### 🎤 What to Say:
"I developed the NIO-based state manager using Java's non-blocking I/O. It uses a Selector to handle 50+ connections with a single thread, making it highly efficient and scalable."

### 📝 Key Code Points:
- **File:** `src/main/java/com/netbattle/server/nio/NIOGameStateManager.java`
- **Line 36:** Selector initialization
- **Line 42:** Non-blocking configuration
- **Line 48:** Event loop with select()
- **Concept:** Non-blocking I/O, Selector pattern

### 🖥️ Demo Command:
```bash
# Terminal 1: Start server
java -cp target/classes com.netbattle.server.nio.NIOGameStateManager

# Terminal 2: Test client
java TestNioClient
```

### 🦈 Wireshark Filter:
```
tcp.port == 8081
```

### 🎯 What to Show:
1. Single thread handling multiple connections
2. Selector waiting on multiple channels
3. Non-blocking accept operations
4. State broadcasting to all clients

---

## Member 3: Nithakshi - UDP Event System

### 🎤 What to Say:
"I created the UDP event system for low-latency communication. It supports both unicast for individual player updates and multicast for broadcasting to groups, using a queue-based event processing architecture."

### 📝 Key Code Points:
- **File:** `src/main/java/com/netbattle/server/udp/UDPEventServer.java`
- **Line 39:** DatagramSocket for unicast
- **Line 42:** MulticastSocket setup
- **Line 27:** BlockingQueue for events
- **Line 195:** Heartbeat broadcasting
- **Concept:** Connectionless UDP, multicast groups

### 🖥️ Demo Command:
```bash
# Terminal 1: Start server
java -cp target/classes com.netbattle.server.udp.UDPEventServer

# Terminal 2: Test unicast
java TestUdpClient

# Terminal 3: Test multicast
java TestMulticastReceiver
```

### 🦈 Wireshark Filter:
```
udp.port == 9000 || ip.dst == 230.0.0.1
```

### 🎯 What to Show:
1. UDP packet structure (no connection)
2. Unicast message delivery
3. Multicast group join (IGMP)
4. Periodic heartbeat packets
5. Low overhead (8-byte header)

---

## Member 4: Chiran - Service Discovery & RMI

### 🎤 What to Say:
"I developed two components: a service discovery registry for DNS-like service resolution with health monitoring, and an RMI-based statistics service for distributed object access across the system."

### 📝 Key Code Points:

**Service Discovery:**
- **File:** `src/main/java/com/netbattle/discovery/ServiceDiscoveryServer.java`
- **Line 26:** REST endpoints for registry
- **Line 71:** Heartbeat monitoring
- **Concept:** Service registry pattern, health checks

**RMI:**
- **File:** `src/main/java/com/netbattle/server/rmi/RMIServer.java`
- **Line 13:** RMI registry creation
- **Line 18:** Remote object binding
- **Concept:** Remote Method Invocation, distributed objects

### 🖥️ Demo Command:
```bash
# Terminal 1: Start Service Discovery
java -cp target/classes com.netbattle.discovery.ServiceDiscoveryServer

# Terminal 2: Start RMI
java -cp target/classes com.netbattle.server.rmi.RMIServer

# Terminal 3: Test RMI
java -cp "target/classes;." TestRmiClient
```

### 🦈 Wireshark Filter:
```
tcp.port == 8084 || tcp.port == 1099
```

### 🎯 What to Show:
1. Service registration HTTP POST
2. Heartbeat messages (periodic)
3. Service discovery query/response
4. RMI registry lookup
5. Remote method invocation (binary protocol)

---

## Member 5: Thilina - WebSocket Bridge

### 🎤 What to Say:
"I implemented a WebSocket server from scratch without external libraries, handling the handshake, frame encoding/decoding, and real-time bidirectional communication between the backend and frontend."

### 📝 Key Code Points:
- **File:** `src/main/java/com/netbattle/websocket/WebSocketGameServer.java`
- **Line 93:** WebSocket handshake
- **Line 111:** SHA-1 accept key generation
- **Line 119:** Frame decoding with masking
- **Line 157:** Frame encoding
- **Line 236:** Internal HTTP API for broadcasts
- **Concept:** WebSocket protocol, full-duplex communication

### 🖥️ Demo Command:
```bash
# Terminal 1: Start server
java -cp target/classes com.netbattle.websocket.WebSocketGameServer

# Browser: Open test client
start test-websocket.html
```

### 🦈 Wireshark Filter:
```
tcp.port == 8082 || websocket
```

### 🎯 What to Show:
1. HTTP upgrade handshake
2. Sec-WebSocket-Accept calculation
3. WebSocket frame structure
4. Masking/unmasking
5. Bidirectional messaging
6. Broadcasting to multiple clients

---

## 🎬 Presentation Flow (10-12 mins per member)

### 1. Introduction (1 min)
- Component name
- Network concepts
- Ports used

### 2. Architecture Overview (1 min)
- Where it fits in system
- How it communicates with other components

### 3. Code Walkthrough (3 mins)
- Show 2-3 key code snippets
- Explain network programming concepts
- Highlight technical challenges

### 4. Live Demonstration (4 mins)
- Start the server
- Run test client
- Show interaction
- Display server logs

### 5. Wireshark Analysis (3 mins)
- Apply filter
- Show packet capture
- Explain protocol details
- Highlight key features

### 6. Questions (flexible)

---

## 🔧 Pre-Demo Setup Checklist

### Before Starting:
- [ ] All code compiled: `scripts\compile.bat`
- [ ] Test clients compiled: `compile-tests.bat`
- [ ] Wireshark installed and tested
- [ ] Browser open (for WebSocket)
- [ ] Multiple terminals ready
- [ ] Backup slides/diagrams prepared

### Terminal Setup:
- **Terminal 1:** Your server
- **Terminal 2:** Test client
- **Terminal 3:** Additional tests
- **Wireshark:** Running with correct filter

---

## 💡 Tips for Success

### Do:
✅ Explain concepts clearly  
✅ Show code before running it  
✅ Use Wireshark to prove it works  
✅ Demonstrate real network traffic  
✅ Be ready for questions  
✅ Have backup if demo fails  

### Don't:
❌ Rush through code  
❌ Skip Wireshark analysis  
❌ Assume everything works (test first!)  
❌ Use jargon without explaining  
❌ Panic if something goes wrong  

---

## 🤔 Expected Questions & Answers

### Q: Why use TCP instead of UDP?
**A:** TCP provides reliable, ordered delivery needed for critical game state. UDP is used for real-time updates where some packet loss is acceptable.

### Q: What's the advantage of NIO over traditional sockets?
**A:** NIO uses non-blocking I/O with selectors, allowing one thread to handle multiple connections efficiently. Traditional sockets require one thread per connection.

### Q: How does multicast differ from broadcast?
**A:** Multicast sends to a specific group (230.0.0.1) that clients opt into. Broadcast sends to everyone on the network. Multicast is more efficient and controlled.

### Q: Why implement WebSocket from scratch?
**A:** Educational purpose - to understand the protocol deeply. Shows knowledge of HTTP upgrade, frame structure, masking, and binary protocols.

### Q: How does RMI work?
**A:** RMI allows calling methods on remote objects. The stub on client serializes the call, sends it over network, skeleton on server deserializes and executes, then sends result back.

### Q: What happens if a service fails?
**A:** Service Discovery tracks heartbeats. If a service misses heartbeats, it's marked DOWN. Clients can discover healthy services only.

---

## 📊 Performance Numbers to Mention

| Component | Metric | Value |
|-----------|--------|-------|
| TCP Server | Thread Pool | 50 threads |
| TCP Server | Response Time | < 50ms |
| NIO Manager | Connections/Thread | 500+ |
| NIO Manager | Latency | < 10ms |
| UDP Events | Packet Size | < 1KB |
| UDP Events | Frequency | 5s heartbeat |
| WebSocket | Frame Overhead | 2-14 bytes |
| WebSocket | Connections | 50+ |
| RMI | Port | 1099 |
| Discovery | Heartbeat | 10s interval |

---

## 🎯 One-Liner Summaries

**Sahan:** "Multi-threaded TCP server with REST API and concurrent connection handling"

**Sachith:** "Non-blocking NIO using Selector pattern for scalable state management"

**Nithakshi:** "Low-latency UDP communication with unicast and multicast support"

**Chiran:** "Service registry with health monitoring and RMI for distributed statistics"

**Thilina:** "Custom WebSocket implementation for full-duplex real-time communication"

---

## 🚨 Emergency Backup Plan

If live demo fails:
1. Show pre-recorded video
2. Use screenshots
3. Explain from code
4. Show Wireshark capture file
5. Use diagrams

Have ready:
- Screenshots of successful runs
- Wireshark .pcap files
- Architecture diagrams
- Code printouts

---

## 📞 Quick Help

**Server won't start?**
```bash
# Check if port is in use
netstat -ano | findstr :8080

# Kill process
taskkill /PID <pid> /F
```

**Can't see traffic in Wireshark?**
1. Use Loopback adapter
2. Run as Administrator
3. Check capture filter
4. Verify service is running

**Test client fails?**
1. Verify server is running
2. Check firewall
3. Use localhost/127.0.0.1
4. Check port numbers

---

Good luck with your VIVA! 🎓 You've got this! 💪
