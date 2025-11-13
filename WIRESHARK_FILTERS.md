# 🦈 Wireshark Filters Cheat Sheet for MathQuest Arena

## Quick Reference for VIVA Demonstration

---

## 📊 By Service/Component

### Member 1: Sahan - TCP Game Server (Port 8080)
```
tcp.port == 8080
```

**Show HTTP traffic only:**
```
tcp.port == 8080 && http
```

**Show only POST requests:**
```
tcp.port == 8080 && http.request.method == "POST"
```

**Show login requests:**
```
tcp.port == 8080 && http.request.uri contains "login"
```

**Show Internal HTTP API (Port 8083):**
```
tcp.port == 8083
```

---

### Member 2: Sachith - NIO State Manager (Port 8081)
```
tcp.port == 8081
```

**Show connection establishment:**
```
tcp.port == 8081 && tcp.flags.syn == 1
```

**Show data transfer:**
```
tcp.port == 8081 && tcp.len > 0
```

**Show multiple connections simultaneously:**
```
tcp.port == 8081 && tcp.stream in {0..10}
```

---

### Member 3: Nithakshi - UDP Event System

**Unicast (Port 9000):**
```
udp.port == 9000
```

**Multicast (Port 9001):**
```
udp.port == 9001
```

**Show multicast address:**
```
ip.dst == 230.0.0.1
```

**All UDP traffic:**
```
udp.port == 9000 || udp.port == 9001
```

**Show IGMP (multicast group management):**
```
igmp
```

---

### Member 4: Chiran - Service Discovery (Port 8084) & RMI (Port 1099)

**Service Discovery:**
```
tcp.port == 8084
```

**Service registration:**
```
tcp.port == 8084 && http.request.uri contains "register"
```

**Heartbeat messages:**
```
tcp.port == 8084 && http.request.uri contains "heartbeat"
```

**RMI traffic:**
```
tcp.port == 1099
```

**RMI protocol (if available):**
```
rmi
```

---

### Member 5: Thilina - WebSocket (Port 8082)

**All WebSocket traffic:**
```
tcp.port == 8082
```

**WebSocket handshake only:**
```
tcp.port == 8082 && http.request.method == "GET" && http.upgrade == "websocket"
```

**WebSocket frames only:**
```
websocket
```

**WebSocket text frames:**
```
websocket.payload
```

---

## 🎯 By Protocol

### TCP Traffic
```
tcp
```

**TCP handshake (SYN, SYN-ACK, ACK):**
```
tcp.flags.syn == 1 || tcp.flags.ack == 1
```

**TCP connection establishment:**
```
tcp.flags.syn == 1 && tcp.flags.ack == 0
```

**TCP data transfer:**
```
tcp.len > 0
```

**TCP connection termination:**
```
tcp.flags.fin == 1 || tcp.flags.rst == 1
```

### HTTP Traffic
```
http
```

**HTTP requests only:**
```
http.request
```

**HTTP responses only:**
```
http.response
```

**HTTP POST requests:**
```
http.request.method == "POST"
```

**HTTP GET requests:**
```
http.request.method == "GET"
```

**HTTP with JSON:**
```
http && json
```

### WebSocket Traffic
```
websocket
```

**WebSocket handshake:**
```
websocket.upgrade
```

**WebSocket text frames:**
```
websocket.opcode == 1
```

**WebSocket binary frames:**
```
websocket.opcode == 2
```

**WebSocket close frames:**
```
websocket.opcode == 8
```

**WebSocket ping/pong:**
```
websocket.opcode == 9 || websocket.opcode == 10
```

### UDP Traffic
```
udp
```

**UDP to specific port:**
```
udp.port == 9000
```

**UDP with data:**
```
udp && data
```

### Multicast Traffic
```
ip.dst >= 224.0.0.0 && ip.dst <= 239.255.255.255
```

**Specific multicast group:**
```
ip.dst == 230.0.0.1
```

---

## 🔍 Advanced Filters

### All MathQuest Arena Traffic
```
tcp.port in {8080,8081,8082,8083,8084,1099} || udp.port in {9000,9001}
```

### Only Data Packets (no handshakes)
```
(tcp.port in {8080,8081,8082,8083,8084} && tcp.len > 0) || udp.port in {9000,9001}
```

### Show Client to Server Only
```
tcp.srcport > 49152 && tcp.dstport in {8080,8081,8082,8083,8084,1099}
```

### Show Server to Client Only
```
tcp.srcport in {8080,8081,8082,8083,8084,1099} && tcp.dstport > 49152
```

### Exclude Empty Packets
```
tcp.len > 0 || udp.length > 8
```

---

## 📈 Useful Statistics

### Protocol Hierarchy
`Statistics → Protocol Hierarchy`

Shows:
- Percentage of each protocol
- Packet count
- Byte count

### Conversations
`Statistics → Conversations`

Shows:
- All connections
- Duration
- Packets/bytes per connection

**Filter to specific port:**
Right-click → Apply as Filter

### I/O Graph
`Statistics → I/O Graph`

Visual timeline of traffic

**Create separate graphs for each service:**
- Graph 1: `tcp.port == 8080`
- Graph 2: `tcp.port == 8082`
- Graph 3: `udp.port == 9000`

### Endpoints
`Statistics → Endpoints`

Shows all IP addresses and ports

---

## 🎨 Display Settings for Demo

### Colorize Packets

`View → Coloring Rules`

**Suggested colors:**
- HTTP: Blue
- WebSocket: Green
- UDP: Yellow
- Errors: Red

### Custom Columns

`Edit → Preferences → Columns`

Add custom columns:
- Protocol
- Source Port
- Destination Port
- Info
- Time (relative)

---

## 🔬 Follow Streams

### TCP Stream
Right-click packet → Follow → TCP Stream

Shows entire conversation

### UDP Stream
Right-click packet → Follow → UDP Stream

### WebSocket Stream
Right-click packet → Follow → WebSocket Stream

---

## 💡 Tips for VIVA

### 1. Capture Filter (before starting capture)
```
port 8080 or port 8081 or port 8082 or port 8083 or port 8084 or port 1099 or port 9000 or port 9001
```

This reduces captured packets to only relevant traffic.

### 2. Start Fresh
- Stop capture
- Clear display: `Ctrl+W`
- Start new capture

### 3. Export Packets
`File → Export Specified Packets`

Save interesting sequences for demonstration

### 4. Packet Details View

Expand sections to show:
- Ethernet Frame
- IP Header
- TCP/UDP Header
- Application Data (HTTP, WebSocket, etc.)

### 5. Time Display

`View → Time Display Format`

Options:
- Seconds Since Beginning of Capture (good for demos)
- Time of Day (for actual timestamps)

---

## 📸 Screenshot Checklist

For each member, capture:

1. **Connection Establishment**
   - TCP 3-way handshake
   - UDP first packet

2. **Data Transfer**
   - Request packet (expanded)
   - Response packet (expanded)

3. **Protocol-Specific**
   - HTTP headers
   - WebSocket frames
   - UDP payload
   - RMI serialization

4. **Statistics**
   - Protocol hierarchy
   - Conversation view
   - I/O graph

---

## 🚀 Quick Start for Demo

### Step 1: Start Wireshark
Run as Administrator

### Step 2: Select Interface
Usually "Loopback: lo" or "Adapter for loopback traffic capture"

### Step 3: Apply Capture Filter
```
port 8080 or port 8082 or port 9000
```

### Step 4: Start Capture
Click shark fin icon

### Step 5: Generate Traffic
Run your test clients

### Step 6: Apply Display Filter
Use filters from above based on what you want to show

### Step 7: Analyze
- Follow streams
- View statistics
- Export important packets

---

## ⚠️ Troubleshooting

### Not Seeing Traffic?

1. **Check Interface**
   - Use Loopback adapter for localhost traffic
   - On Windows: "Npcap Loopback Adapter"

2. **Check Filters**
   - Remove all filters temporarily
   - Verify traffic exists

3. **Run as Admin**
   - Wireshark needs elevated privileges

4. **Install Npcap**
   - Required on Windows
   - https://npcap.com/

### Can't See WebSocket Frames?

1. Ensure you have the full capture from handshake
2. Right-click → Decode As → WebSocket
3. Check Wireshark version (update if old)

---

## 📚 Additional Resources

- **Wireshark User Guide:** https://www.wireshark.org/docs/wsug_html_chunked/
- **Display Filter Reference:** https://www.wireshark.org/docs/dfref/
- **Sample Captures:** https://wiki.wireshark.org/SampleCaptures

---

## ✅ Pre-Demo Checklist

- [ ] Wireshark installed and running
- [ ] Correct network interface selected (Loopback)
- [ ] All services running
- [ ] Test clients compiled
- [ ] Filters tested and working
- [ ] Screenshots planned
- [ ] Backup capture files saved

---

Good luck with your demonstration! 🎓
