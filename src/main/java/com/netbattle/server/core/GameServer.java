// server/core/GameServer.java
package com.netbattle.server.core;

import com.netbattle.common.model.*;
import com.netbattle.common.protocol.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int TCP_PORT = 8080;
    private static final int MAX_THREADS = 50;
    
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Map<String, ClientHandler> connectedClients;
    private Map<String, GameSession> activeSessions;
    private volatile boolean running;
    
    public GameServer() {
        this.threadPool = Executors.newFixedThreadPool(MAX_THREADS);
        this.connectedClients = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        this.running = false;
    }
    
    public void start() throws IOException {
        serverSocket = new ServerSocket(TCP_PORT);
        running = true;
        
        System.out.println("🎮 NetBattle Arena Server started on port " + TCP_PORT);
        System.out.println("📊 Thread pool size: " + MAX_THREADS);
        
        // Create a default game session
        GameSession defaultSession = new GameSession("session-1", "Default Arena", 50);
        loadChallenges(defaultSession);
        activeSessions.put(defaultSession.getSessionId(), defaultSession);
        
        // Accept client connections
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✅ New connection from: " + clientSocket.getInetAddress());
                
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.execute(handler);
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("❌ Error accepting connection: " + e.getMessage());
                }
            }
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            threadPool.shutdown();
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }
    }
    
    // Load sample challenges
    private void loadChallenges(GameSession session) {
        session.addChallenge(new Challenge("ch1", "Easy Crypto", 
            "Decode: Q2lwaGVyVGV4dA==", "crypto", 100, "CipherText"));
        session.addChallenge(new Challenge("ch2", "Web Exploit", 
            "Find the hidden admin panel", "web", 200, "flag{admin_found}"));
        session.addChallenge(new Challenge("ch3", "Network Forensics", 
            "What's the secret port?", "forensics", 150, "flag{8888}"));
    }
    
    // Client management methods
    public void registerClient(String sessionId, ClientHandler handler) {
        connectedClients.put(sessionId, handler);
    }
    
    public void unregisterClient(String sessionId) {
        connectedClients.remove(sessionId);
    }
    
    public GameSession getGameSession(String sessionId) {
        return activeSessions.get(sessionId);
    }
    
    public Collection<GameSession> getAllSessions() {
        return activeSessions.values();
    }
    
    // Broadcast message to all clients in a session
    public void broadcast(String sessionId, Message message) {
        GameSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.getPlayers().keySet().forEach(playerId -> {
                ClientHandler handler = connectedClients.get(playerId);
                if (handler != null) {
                    handler.sendMessage(message);
                }
            });
        }
    }
    
    public static void main(String[] args) {
        GameServer server = new GameServer();
        
        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down server...");
            server.stop();
        }));
        
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}