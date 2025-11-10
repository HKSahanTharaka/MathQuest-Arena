package com.netbattle.api;

import com.netbattle.server.security.SecureAuthServer;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class RestApiServer {
    private static final int PORT = 8080;
    private HttpServer server;
    private SecureAuthServer authServer;
    private Map<String, String> challengeFlags;
    private GameDataManager gameData;
    
    public RestApiServer(SecureAuthServer authServer) {
        this.authServer = authServer;
        this.challengeFlags = new HashMap<>();
        this.gameData = GameDataManager.getInstance();
        initializeFlags();
    }
    
    private void initializeFlags() {
        challengeFlags.put("1", "42");
        challengeFlags.put("2", "89");
        challengeFlags.put("3", "15");
        challengeFlags.put("4", "9");
        challengeFlags.put("5", "3.14159");
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));
        
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/auth/logout", this::handleLogout);
        server.createContext("/api/auth/register", this::handleRegister);
        server.createContext("/api/problems", this::handleChallenges);
        server.createContext("/api/problems/submit", this::handleSubmitFlag);
        server.createContext("/api/leaderboard", this::handleLeaderboard);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/achievements", this::handleAchievements);
        server.createContext("/api/server/stats", this::handleServerStats);
        server.createContext("/api/game/status", this::handleGameStatus);
        
        server.start();
        System.out.println("🌐 REST API Server started on port " + PORT);
    }
    
    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        addCorsHeaders(exchange);
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            String username = data.get("username");
            
            if (username == null || username.trim().isEmpty()) {
                sendError(exchange, 400, "Username is required");
                return;
            }
            
            username = username.trim();
            
            if (username.length() < 2 || username.length() > 20) {
                sendError(exchange, 400, "Username must be between 2 and 20 characters");
                return;
            }
            
            // Check if username is already taken
            if (gameData.isUsernameTaken(username)) {
                sendError(exchange, 409, "Username '" + username + "' is already taken by another player");
                return;
            }
            
            String playerId = "player-" + username + "-" + System.currentTimeMillis();
            String token = java.util.UUID.randomUUID().toString();
            
            try {
                gameData.registerPlayer(playerId, username);
            } catch (IllegalArgumentException e) {
                sendError(exchange, 409, e.getMessage());
                return;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", token);
            response.put("playerId", playerId);
            response.put("username", username);
            
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            System.err.println("Login error: " + e.getMessage());
            sendError(exchange, 500, "Internal server error");
        }
    }
    
    private void handleLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        addCorsHeaders(exchange);
        
        try {
            String playerId = getPlayerIdFromRequest(exchange);
            
            if (playerId == null || playerId.isEmpty()) {
                sendError(exchange, 400, "Player ID is required");
                return;
            }
            
            // Remove player from active players (this will free up the username)
            gameData.removeActivePlayer(playerId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Logged out successfully");
            
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            System.err.println("Logout error: " + e.getMessage());
            sendError(exchange, 500, "Internal server error");
        }
    }
    
    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        addCorsHeaders(exchange);
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            String username = data.get("username");
            String password = data.get("password");
            
            if (username == null || password == null) {
                sendError(exchange, 400, "Missing credentials");
                return;
            }
            
            boolean registered = authServer.registerUser(username, password);
            
            if (registered) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Registration successful");
                sendJson(exchange, 200, response);
            } else {
                sendError(exchange, 409, "Username already exists");
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Internal server error");
        }
    }
    
    private void handleChallenges(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        String playerId = getPlayerIdFromRequest(exchange);
        List<Map<String, Object>> problems;
        
        if (playerId != null) {
            problems = gameData.getChallengesForPlayer(playerId);
        } else {
            problems = gameData.getChallengesForPlayer("guest");
        }
        
        sendJson(exchange, 200, problems);
    }
    
    private void handleSubmitFlag(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        addCorsHeaders(exchange);
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            String challengeId = data.get("challengeId");
            String submittedFlag = data.get("answer");
            String playerId = getPlayerIdFromRequest(exchange);
            
            if (challengeId == null || submittedFlag == null) {
                sendError(exchange, 400, "Missing challengeId or answer");
                return;
            }
            
            if (playerId == null) {
                sendError(exchange, 401, "Player not authenticated");
                return;
            }
            
            // Check if game is ready (minimum players joined)
            if (!gameData.isGameReady()) {
                Map<String, Object> response = new HashMap<>();
                response.put("correct", false);
                response.put("message", "Waiting for more players to join. " + 
                    gameData.getActivePlayerCount() + "/" + gameData.getMinimumPlayers() + " players joined.");
                response.put("gameReady", false);
                response.put("currentPlayerCount", gameData.getActivePlayerCount());
                response.put("minimumPlayers", gameData.getMinimumPlayers());
                sendJson(exchange, 200, response);
                return;
            }
            
            if (gameData.hasPlayerSolved(playerId, challengeId)) {
                Map<String, Object> response = new HashMap<>();
                response.put("correct", false);
                response.put("message", "You already solved this problem!");
                sendJson(exchange, 200, response);
                return;
            }
            
            String correctFlag = challengeFlags.get(challengeId);
            
            if (correctFlag == null) {
                sendError(exchange, 404, "Problem not found");
                return;
            }
            
            boolean isCorrect = correctFlag.equalsIgnoreCase(submittedFlag.trim());
            
            Map<String, Object> response = new HashMap<>();
            response.put("correct", isCorrect);
            response.put("gameReady", true);
            
            if (isCorrect) {
                int points = getChallengePoints(challengeId);
                boolean submitted = gameData.submitFlag(playerId, challengeId, points);
                
                if (submitted) {
                    response.put("message", "Correct! Excellent work!");
                    response.put("points", points);
                } else {
                    response.put("correct", false);
                    response.put("message", "Unable to submit. Please try again.");
                }
            } else {
                response.put("message", "Incorrect answer. Try again!");
            }
            
            sendJson(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("Error handling flag submission: " + e.getMessage());
            sendError(exchange, 500, "Internal server error");
        }
    }
    
    private int getChallengePoints(String challengeId) {
        switch (challengeId) {
            case "1": return 100;
            case "2": return 200;
            case "3": return 150;
            case "4": return 300;
            case "5": return 250;
            default: return 0;
        }
    }
    
    
    private void handleLeaderboard(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        List<Map<String, Object>> leaderboard = gameData.getLeaderboard();
        sendJson(exchange, 200, leaderboard);
    }
    
    private void handleStats(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        String playerId = getPlayerIdFromRequest(exchange);
        
        if (playerId == null || playerId.isEmpty()) {
            System.err.println("❌ Stats request: Player ID not found in request headers");
            sendError(exchange, 401, "Player not authenticated");
            return;
        }
        
        System.out.println("📊 Fetching stats for player: " + playerId);
        Map<String, Object> stats = gameData.getPlayerStats(playerId);
        System.out.println("📊 Stats retrieved - Score: " + stats.get("totalScore") + 
                          ", Challenges: " + stats.get("challengesSolved") + 
                          ", Rank: " + stats.get("rank"));
        sendJson(exchange, 200, stats);
    }
    
    private void handleAchievements(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        String playerId = getPlayerIdFromRequest(exchange);
        
        if (playerId == null) {
            sendError(exchange, 401, "Player not authenticated");
            return;
        }
        
        List<String> achievements = gameData.getPlayerAchievements(playerId);
        sendJson(exchange, 200, achievements);
    }
    
    private void handleServerStats(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        Map<String, Object> serverStats = gameData.getServerStats();
        sendJson(exchange, 200, serverStats);
    }
    
    private void handleGameStatus(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        Map<String, Object> gameStatus = gameData.getGameStatus();
        sendJson(exchange, 200, gameStatus);
    }
    
    private String getPlayerIdFromRequest(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        
        String playerId = headers.getFirst("X-Player-Id");
        if (playerId != null && !playerId.isEmpty()) {
            return playerId;
        }
        
        String auth = headers.getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            SecureAuthServer.AuthSession session = authServer.getSession(token);
            if (session != null) {
                return "player-" + session.getUsername() + "-";
            }
        }
        
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "playerId".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        
        return null;
    }
    
    private void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Player-Id");
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            try {
                exchange.sendResponseHeaders(204, -1);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        json = json.trim();
        if (json.startsWith("{")) {
            json = json.substring(1, json.length() - 1);
        }
        
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
    
    private void sendJson(HttpExchange exchange, int code, Object data) throws IOException {
        String json = convertToJson(data);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("success", false);
        sendJson(exchange, code, error);
    }
    
    private String convertToJson(Object obj) {
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(valueToJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(convertToJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return obj.toString();
    }
    
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof List) {
            return convertToJson(value);
        } else if (value instanceof Map) {
            return convertToJson(value);
        }
        return "\"" + value.toString() + "\"";
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
    
    public static void main(String[] args) {
        try {
            SecureAuthServer authServer = new SecureAuthServer();
            
            new Thread(() -> {
                try {
                    authServer.start();
                } catch (Exception e) {
                    System.err.println("Auth server error: " + e.getMessage());
                }
            }).start();
            
            Thread.sleep(1000);
            
            RestApiServer apiServer = new RestApiServer(authServer);
            apiServer.start();
            
            System.out.println("✅ All servers ready!");
            System.out.println("🌐 REST API: http://localhost:8080");
            System.out.println("🔒 SSL Auth: https://localhost:8443");
            
        } catch (Exception e) {
            System.err.println("Failed to start servers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

