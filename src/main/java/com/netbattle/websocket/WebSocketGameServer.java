package com.netbattle.websocket;

import com.netbattle.common.protocol.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class WebSocketGameServer {
    private static final int WS_PORT = 8082;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Map<SocketChannel, WebSocketClient> clients;
    private volatile boolean running;
    
    public WebSocketGameServer() {
        this.clients = new ConcurrentHashMap<>();
        this.running = false;
    }
    
    public void start() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(WS_PORT));
        serverChannel.configureBlocking(false);
        
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        running = true;
        System.out.println("🌐 WebSocket server started on port " + WS_PORT);
        
        while (running) {
            selector.select(1000);
            
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                
                try {
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                } catch (IOException e) {
                    System.err.println("Error handling key: " + e.getMessage());
                    closeClient(key);
                }
            }
        }
    }
    
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            WebSocketClient client = new WebSocketClient(clientChannel);
            clients.put(clientChannel, client);
            
            System.out.println("✅ New WebSocket connection from: " + clientChannel.getRemoteAddress());
        }
    }
    
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        WebSocketClient client = clients.get(channel);
        
        if (client == null) return;
        
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            closeClient(key);
            return;
        }
        
        buffer.flip();
        
        if (!client.handshakeComplete) {
            performHandshake(channel, buffer, client);
        } else {
            String message = decodeFrame(buffer);
            if (message != null) {
                handleMessage(channel, message);
            }
        }
    }
    
    private void performHandshake(SocketChannel channel, ByteBuffer buffer, WebSocketClient client) throws IOException {
        String request = StandardCharsets.UTF_8.decode(buffer).toString();
        
        Pattern keyPattern = Pattern.compile("Sec-WebSocket-Key: (.+)");
        java.util.regex.Matcher matcher = keyPattern.matcher(request);
        
        if (matcher.find()) {
            String key = matcher.group(1).trim();
            String accept = generateAcceptKey(key);
            
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            
            channel.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
            client.handshakeComplete = true;
            
            sendMessage(channel, "{\"type\":\"connected\",\"payload\":{}}");
        }
    }
    
    private String generateAcceptKey(String key) {
        try {
            String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((key + magic).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    private String decodeFrame(ByteBuffer buffer) {
        if (buffer.remaining() < 2) return null;
        
        buffer.get();
        byte secondByte = buffer.get();
        boolean masked = (secondByte & 0x80) != 0;
        int payloadLength = secondByte & 0x7F;
        
        if (payloadLength == 126) {
            if (buffer.remaining() < 2) return null;
            payloadLength = buffer.getShort() & 0xFFFF;
        } else if (payloadLength == 127) {
            if (buffer.remaining() < 8) return null;
            payloadLength = (int) buffer.getLong();
        }
        
        byte[] masks = new byte[4];
        if (masked) {
            buffer.get(masks);
        }
        
        if (buffer.remaining() < payloadLength) return null;
        
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= masks[i % 4];
            }
        }
        
        return new String(payload, StandardCharsets.UTF_8);
    }
    
    private void sendMessage(SocketChannel channel, String message) {
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            ByteBuffer frame = ByteBuffer.allocate(payload.length + 10);
            
            frame.put((byte) 0x81);
            
            if (payload.length <= 125) {
                frame.put((byte) payload.length);
            } else if (payload.length <= 65535) {
                frame.put((byte) 126);
                frame.putShort((short) payload.length);
            } else {
                frame.put((byte) 127);
                frame.putLong(payload.length);
            }
            
            frame.put(payload);
            frame.flip();
            
            channel.write(frame);
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }
    
    private void broadcast(String message, SocketChannel excludeChannel) {
        for (Map.Entry<SocketChannel, WebSocketClient> entry : clients.entrySet()) {
            if (entry.getValue().handshakeComplete && !entry.getKey().equals(excludeChannel)) {
                sendMessage(entry.getKey(), message);
            }
        }
    }
    
    private void handleMessage(SocketChannel channel, String message) {
        System.out.println("📨 Received: " + message);
        
        if (message.contains("\"type\":\"chat\"")) {
            broadcast(message, null);
        } else if (message.contains("\"type\":\"challenge_solved\"")) {
            broadcast(message, channel);
        } else if (message.contains("\"type\":\"stats_update\"")) {
            broadcast(message, null);
        }
    }
    
    private void closeClient(SelectionKey key) {
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            clients.remove(channel);
            channel.close();
            key.cancel();
            System.out.println("❌ Client disconnected");
        } catch (IOException e) {
            System.err.println("Error closing client: " + e.getMessage());
        }
    }
    
    public void stop() {
        running = false;
        try {
            for (SocketChannel channel : clients.keySet()) {
                channel.close();
            }
            if (selector != null) selector.close();
            if (serverChannel != null) serverChannel.close();
        } catch (IOException e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }
    }
    
    private static class WebSocketClient {
        boolean handshakeComplete = false;
        String token;
        
        WebSocketClient(SocketChannel channel) {
        }
    }
    
    public static void main(String[] args) {
        try {
            WebSocketGameServer server = new WebSocketGameServer();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Shutting down WebSocket server...");
                server.stop();
            }));
            
            server.start();
        } catch (Exception e) {
            System.err.println("❌ Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

