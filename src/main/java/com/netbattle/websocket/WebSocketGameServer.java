package com.netbattle.websocket;

import com.netbattle.api.GameDataManager;
import com.netbattle.common.protocol.*;
import com.sun.net.httpserver.*;
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
    private static final int HTTP_API_PORT = 8083;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Map<SocketChannel, WebSocketClient> clients;
    private HttpServer httpServer;
    private volatile boolean running;
    private GameDataManager gameData;
    
    public WebSocketGameServer() {
        this.clients = new ConcurrentHashMap<>();
        this.running = false;
        this.gameData = GameDataManager.getInstance();
    }
    
    public void start() throws IOException {
        // Start HTTP API server for internal broadcasts
        startHttpApiServer();
        
        // Start WebSocket server
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(WS_PORT));
        serverChannel.configureBlocking(false);
        
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        running = true;
        System.out.println("🌐 WebSocket server started on port " + WS_PORT);
        System.out.println("🔌 HTTP API server started on port " + HTTP_API_PORT);
        
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
    
    private void startHttpApiServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(HTTP_API_PORT), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(2));
        
        httpServer.createContext("/broadcast/player-join", this::handlePlayerJoinBroadcast);
        httpServer.createContext("/broadcast/game-status", this::handleGameStatusBroadcast);
        httpServer.createContext("/broadcast/score-update", this::handleScoreUpdateBroadcast);
        httpServer.createContext("/broadcast/activity", this::handleActivityBroadcast);
        httpServer.createContext("/broadcast/leaderboard-update", this::handleLeaderboardUpdateBroadcast);
        httpServer.createContext("/broadcast/server-stats", this::handleServerStatsBroadcast);
        httpServer.createContext("/broadcast/challenge-solved", this::handleChallengeSolvedBroadcast);
        httpServer.createContext("/broadcast/session-started", this::handleSessionStartedBroadcast);
        httpServer.createContext("/broadcast/session-ended", this::handleSessionEndedBroadcast);
        
        httpServer.start();
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
            // Check if session is started - if so, block chat
            if (gameData.isSessionStarted()) {
                // Session has started, chat is disabled
                String errorMessage = "{\"type\":\"chat_error\",\"payload\":{\"message\":\"Chat is disabled during active game session. Chat will be available again after the session ends.\"}}";
                sendMessage(channel, errorMessage);
                System.out.println("🚫 Chat blocked - session in progress");
            } else {
                // Session not started yet, allow chat
                broadcast(message, null);
            }
        } else if (message.contains("\"type\":\"challenge_solved\"")) {
            broadcast(message, channel);
        } else if (message.contains("\"type\":\"stats_update\"")) {
            broadcast(message, null);
        } else if (message.contains("\"type\":\"player_joined\"")) {
            broadcast(message, null);
        } else if (message.contains("\"type\":\"game_status\"")) {
            broadcast(message, null);
        }
    }
    
    // Method to broadcast game status updates
    public void broadcastGameStatus(boolean gameReady, int currentCount, int minimumPlayers, String firstPlayerId) {
        String statusMessage = String.format(
            "{\"type\":\"game_status\",\"payload\":{\"gameReady\":%b,\"currentPlayerCount\":%d,\"minimumPlayers\":%d,\"playersNeeded\":%d,\"firstPlayerId\":\"%s\"}}",
            gameReady, currentCount, minimumPlayers, Math.max(0, minimumPlayers - currentCount), firstPlayerId != null ? firstPlayerId : ""
        );
        broadcast(statusMessage, null);
    }
    
    // Overload for backward compatibility
    public void broadcastGameStatus(boolean gameReady, int currentCount, int minimumPlayers) {
        broadcastGameStatus(gameReady, currentCount, minimumPlayers, "");
    }
    
    // Method to broadcast player join events
    public void broadcastPlayerJoin(String username, int currentCount, int minimumPlayers) {
        String joinMessage = String.format(
            "{\"type\":\"player_joined\",\"payload\":{\"username\":\"%s\",\"currentPlayerCount\":%d,\"minimumPlayers\":%d,\"playersNeeded\":%d}}",
            username, currentCount, minimumPlayers, Math.max(0, minimumPlayers - currentCount)
        );
        broadcast(joinMessage, null);
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
    
    private void handlePlayerJoinBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            // Parse JSON: {"username":"...","currentCount":1,"minimumPlayers":3}
            Map<String, String> data = parseJson(body);
            
            String username = data.get("username");
            int currentCount = Integer.parseInt(data.getOrDefault("currentCount", "0"));
            int minimumPlayers = Integer.parseInt(data.getOrDefault("minimumPlayers", "3"));
            
            broadcastPlayerJoin(username, currentCount, minimumPlayers);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling player join broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private void handleGameStatusBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            // Parse JSON: {"gameReady":false,"currentCount":1,"minimumPlayers":3,"firstPlayerId":"abc123"}
            Map<String, String> data = parseJson(body);
            
            boolean gameReady = Boolean.parseBoolean(data.getOrDefault("gameReady", "false"));
            int currentCount = Integer.parseInt(data.getOrDefault("currentCount", "0"));
            int minimumPlayers = Integer.parseInt(data.getOrDefault("minimumPlayers", "3"));
            String firstPlayerId = data.getOrDefault("firstPlayerId", "");
            
            broadcastGameStatus(gameReady, currentCount, minimumPlayers, firstPlayerId);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling game status broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
    
    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim().replace("{", "").replace("}", "");
        
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }
        return map;
    }
    
    private void handleScoreUpdateBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            String playerId = data.get("playerId");
            String username = data.get("username");
            int totalScore = Integer.parseInt(data.getOrDefault("totalScore", "0"));
            int challengesSolved = Integer.parseInt(data.getOrDefault("challengesSolved", "0"));
            int rank = Integer.parseInt(data.getOrDefault("rank", "0"));
            
            String message = String.format(
                "{\"type\":\"stats_update\",\"payload\":{\"playerId\":\"%s\",\"username\":\"%s\",\"totalScore\":%d,\"challengesSolved\":%d,\"rank\":%d}}",
                playerId, username, totalScore, challengesSolved, rank
            );
            broadcast(message, null);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling score update broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private void handleActivityBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            String messageText = data.get("message");
            String username = data.get("username");
            String challengeId = data.get("challengeId");
            int points = Integer.parseInt(data.getOrDefault("points", "0"));
            long timestamp = Long.parseLong(data.getOrDefault("timestamp", String.valueOf(System.currentTimeMillis())));
            
            String message = String.format(
                "{\"type\":\"activity\",\"payload\":{\"message\":\"%s\",\"username\":\"%s\",\"challengeId\":\"%s\",\"points\":%d,\"timestamp\":%d}}",
                messageText != null ? messageText.replace("\"", "\\\"") : "", username, challengeId, points, timestamp
            );
            broadcast(message, null);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling activity broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private void handleLeaderboardUpdateBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            // Body format: {"leaderboard":[...]}
            // Extract just the leaderboard array part
            String leaderboardJson = body;
            if (body.contains("\"leaderboard\":")) {
                int startIdx = body.indexOf("[");
                int endIdx = body.lastIndexOf("]") + 1;
                if (startIdx > 0 && endIdx > startIdx) {
                    leaderboardJson = body.substring(startIdx, endIdx);
                }
            }
            
            String message = String.format("{\"type\":\"leaderboard_update\",\"payload\":{\"leaderboard\":%s}}", leaderboardJson);
            broadcast(message, null);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling leaderboard update broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private void handleServerStatsBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            int activePlayers = Integer.parseInt(data.getOrDefault("activePlayers", "0"));
            int totalPlayers = Integer.parseInt(data.getOrDefault("totalPlayers", "0"));
            int totalChallenges = Integer.parseInt(data.getOrDefault("totalChallenges", "0"));
            long uptime = Long.parseLong(data.getOrDefault("uptime", "0"));
            boolean gameReady = Boolean.parseBoolean(data.getOrDefault("gameReady", "false"));
            int minimumPlayers = Integer.parseInt(data.getOrDefault("minimumPlayers", "3"));
            int currentPlayerCount = Integer.parseInt(data.getOrDefault("currentPlayerCount", "0"));
            
            String message = String.format(
                "{\"type\":\"server_stats\",\"payload\":{\"activePlayers\":%d,\"totalPlayers\":%d,\"totalChallenges\":%d,\"uptime\":%d,\"gameReady\":%b,\"minimumPlayers\":%d,\"currentPlayerCount\":%d}}",
                activePlayers, totalPlayers, totalChallenges, uptime, gameReady, minimumPlayers, currentPlayerCount
            );
            broadcast(message, null);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling server stats broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private void handleChallengeSolvedBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            String playerId = data.get("playerId");
            String username = data.get("username");
            String challengeId = data.get("challengeId");
            String challengeName = data.get("challengeName");
            int points = Integer.parseInt(data.getOrDefault("points", "0"));
            
            String message = String.format(
                "{\"type\":\"challenge_solved\",\"payload\":{\"playerId\":\"%s\",\"username\":\"%s\",\"challengeId\":\"%s\",\"challengeName\":\"%s\",\"points\":%d}}",
                playerId, username, challengeId, challengeName, points
            );
            broadcast(message, null);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling challenge solved broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private void handleSessionStartedBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            long startTime = Long.parseLong(data.getOrDefault("startTime", "0"));
            long endTime = Long.parseLong(data.getOrDefault("endTime", "0"));
            long duration = Long.parseLong(data.getOrDefault("duration", "0"));
            
            String message = String.format(
                "{\"type\":\"session_started\",\"payload\":{\"sessionStarted\":true,\"startTime\":%d,\"endTime\":%d,\"duration\":%d}}",
                startTime, endTime, duration
            );
            broadcast(message, null);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling session started broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private void handleSessionEndedBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            String message = "{\"type\":\"session_ended\",\"payload\":{\"sessionStarted\":false,\"sessionEnded\":true}}";
            broadcast(message, null);
            sendHttpResponse(exchange, 200, "OK");
        } catch (Exception e) {
            System.err.println("Error handling session ended broadcast: " + e.getMessage());
            sendHttpResponse(exchange, 500, "Internal server error");
        }
    }
    
    private void sendHttpResponse(HttpExchange exchange, int code, String message) throws IOException {
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (httpServer != null) {
                httpServer.stop(0);
            }
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

