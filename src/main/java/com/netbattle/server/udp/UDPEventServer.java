// server/udp/UDPEventServer.java
package com.netbattle.server.udp;

import com.netbattle.common.model.*;
import com.netbattle.common.protocol.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class UDPEventServer {
    private static final int UDP_PORT = 9000;
    private static final int MULTICAST_PORT = 9001;
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MAX_PACKET_SIZE = 4096;
    
    private DatagramSocket unicastSocket;
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    private Map<String, InetSocketAddress> playerAddresses;
    private volatile boolean running;
    
    // Event queue for processing
    private BlockingQueue<UDPEvent> eventQueue;
    private ExecutorService eventProcessor;
    
    public UDPEventServer() {
        this.playerAddresses = new ConcurrentHashMap<>();
        this.eventQueue = new LinkedBlockingQueue<>();
        this.eventProcessor = Executors.newFixedThreadPool(4);
    }
    
    public void start() throws IOException {
        // Setup unicast socket
        unicastSocket = new DatagramSocket(UDP_PORT);
        
        // Setup multicast socket
        multicastSocket = new MulticastSocket(MULTICAST_PORT);
        multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);
        
        // Join multicast group (for receiving)
        NetworkInterface netIf = NetworkInterface.getByInetAddress(
            InetAddress.getLocalHost());
        multicastSocket.joinGroup(new InetSocketAddress(multicastGroup, MULTICAST_PORT), netIf);
        
        System.out.println("📡 UDP Event Server started");
        System.out.println("   Unicast port: " + UDP_PORT);
        System.out.println("   Multicast: " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);
        
        running = true;
        
        // Start event processor threads
        for (int i = 0; i < 4; i++) {
            eventProcessor.execute(this::processEvents);
        }
        
        // Start heartbeat broadcaster
        new Thread(this::broadcastHeartbeat).start();
        
        // Main receive loop
        receiveLoop();
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                unicastSocket.receive(packet);
                
                // Parse message
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                Message message = Message.fromBytes(data);
                
                // Create event
                UDPEvent event = new UDPEvent(
                    message,
                    packet.getAddress(),
                    packet.getPort()
                );
                
                eventQueue.offer(event);
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error receiving UDP packet: " + e.getMessage());
                }
            }
        }
    }
    
    private void processEvents() {
        while (running) {
            try {
                UDPEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;
                
                handleEvent(event);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void handleEvent(UDPEvent event) {
        Message message = event.getMessage();
        
        try {
            switch (message.getType()) {
                case PLAYER_POSITION:
                    handlePlayerPosition(event);
                    break;
                    
                case FLAG_CAPTURED:
                    handleFlagCapture(event);
                    break;
                    
                case CHAT_MESSAGE:
                    handleChatMessage(event);
                    break;
                    
                case LOGIN_REQUEST:
                    handlePlayerRegistration(event);
                    break;
                    
                default:
                    System.out.println("📡 Unknown UDP event: " + message.getType());
            }
        } catch (Exception e) {
            System.err.println("Error handling event: " + e.getMessage());
        }
    }
    
    private void handlePlayerPosition(UDPEvent event) {
        String sessionId = event.getMessage().getSessionId();
        
        // Register/update player address
        playerAddresses.put(sessionId, 
            new InetSocketAddress(event.getAddress(), event.getPort()));
        
        // Broadcast position to all other players
        broadcastToAll(event.getMessage(), sessionId);
        
        // Statistics
        if (Math.random() < 0.01) { // 1% sampling
            System.out.println("📡 Position update from: " + sessionId);
        }
    }
    
    private void handleFlagCapture(UDPEvent event) {
        String payload = event.getMessage().getPayloadAsString();
        System.out.println("🚩 FLAG CAPTURED: " + payload);
        
        // Broadcast to all players via multicast
        broadcastViaMulticast(event.getMessage());
        
        // Send acknowledgment to capturing player
        Message ack = new Message(MessageType.SUCCESS, 
            event.getMessage().getSessionId(), 
            "FLAG_CAPTURED_ACK".getBytes());
        sendToPlayer(event.getMessage().getSessionId(), ack);
    }
    
    private void handleChatMessage(UDPEvent event) {
        String sessionId = event.getMessage().getSessionId();
        String chatMessage = event.getMessage().getPayloadAsString();
        
        System.out.println("💬 Chat from " + sessionId + ": " + chatMessage);
        
        // Broadcast to all players
        broadcastToAll(event.getMessage(), sessionId);
    }
    
    private void handlePlayerRegistration(UDPEvent event) {
        String sessionId = event.getMessage().getSessionId();
        
        playerAddresses.put(sessionId, 
            new InetSocketAddress(event.getAddress(), event.getPort()));
        
        System.out.println("📡 Player registered for UDP: " + sessionId);
        
        // Send confirmation
        Message response = new Message(MessageType.SUCCESS, sessionId, 
            "UDP_REGISTERED".getBytes());
        sendToPlayer(sessionId, response);
    }
    
    // Send message to specific player
    private void sendToPlayer(String sessionId, Message message) {
        InetSocketAddress address = playerAddresses.get(sessionId);
        if (address == null) return;
        
        try {
            byte[] data = message.toBytes();
            DatagramPacket packet = new DatagramPacket(
                data, data.length, address.getAddress(), address.getPort());
            unicastSocket.send(packet);
        } catch (IOException e) {
            System.err.println("Error sending to player: " + e.getMessage());
        }
    }
    
    // Broadcast to all players except sender
    private void broadcastToAll(Message message, String excludeSessionId) {
        for (Map.Entry<String, InetSocketAddress> entry : playerAddresses.entrySet()) {
            if (!entry.getKey().equals(excludeSessionId)) {
                try {
                    byte[] data = message.toBytes();
                    DatagramPacket packet = new DatagramPacket(
                        data, data.length, 
                        entry.getValue().getAddress(), 
                        entry.getValue().getPort());
                    unicastSocket.send(packet);
                } catch (IOException e) {
                    System.err.println("Error broadcasting: " + e.getMessage());
                }
            }
        }
    }
    
    // Broadcast via multicast
    private void broadcastViaMulticast(Message message) {
        try {
            byte[] data = message.toBytes();
            DatagramPacket packet = new DatagramPacket(
                data, data.length, multicastGroup, MULTICAST_PORT);
            multicastSocket.send(packet);
            
            System.out.println("📢 Multicast broadcast sent");
        } catch (IOException e) {
            System.err.println("Error multicasting: " + e.getMessage());
        }
    }
    
    // Send heartbeat every 5 seconds
    private void broadcastHeartbeat() {
        while (running) {
            try {
                Thread.sleep(5000);
                
                Message heartbeat = new Message(MessageType.GAME_STATE, null, 
                    "HEARTBEAT".getBytes());
                broadcastViaMulticast(heartbeat);
                
                System.out.println("💓 Heartbeat sent to " + playerAddresses.size() + " players");
                
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    public void stop() {
        running = false;
        eventProcessor.shutdown();
        
        if (unicastSocket != null && !unicastSocket.isClosed()) {
            unicastSocket.close();
        }
        if (multicastSocket != null && !multicastSocket.isClosed()) {
            multicastSocket.close();
        }
    }
    
    // Inner class for UDP events
    private static class UDPEvent {
        private Message message;
        private InetAddress address;
        private int port;
        
        public UDPEvent(Message message, InetAddress address, int port) {
            this.message = message;
            this.address = address;
            this.port = port;
        }
        
        public Message getMessage() { return message; }
        public InetAddress getAddress() { return address; }
        public int getPort() { return port; }
    }
    
    public static void main(String[] args) {
        UDPEventServer server = new UDPEventServer();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n📡 Shutting down UDP server...");
            server.stop();
        }));
        
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start UDP server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}