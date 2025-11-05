// server/core/ClientHandler.java
package com.netbattle.server.core;

import com.netbattle.common.model.*;
import com.netbattle.common.protocol.*;
import java.io.*;
import java.net.*;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private Socket socket;
    private GameServer server;
    private DataInputStream input;
    private DataOutputStream output;
    private String sessionId;
    private Player player;
    private volatile boolean running;
    
    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.running = true;
    }
    
    @Override
    public void run() {
        try {
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            
            System.out.println("🔗 Client handler started for " + socket.getInetAddress());
            
            while (running && !socket.isClosed()) {
                try {
                    // Read message length first
                    int length = input.readInt();
                    byte[] data = new byte[length];
                    input.readFully(data);
                    
                    Message message = Message.fromBytes(data);
                    handleMessage(message);
                    
                } catch (EOFException e) {
                    System.out.println("Client disconnected: " + socket.getInetAddress());
                    break;
                } catch (IOException e) {
                    System.err.println("Error reading message: " + e.getMessage());
                    break;
                }
            }
            
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    private void handleMessage(Message message) {
        try {
            switch (message.getType()) {
                case LOGIN_REQUEST:
                    handleLogin(message);
                    break;
                    
                case JOIN_GAME:
                    handleJoinGame(message);
                    break;
                    
                case SUBMIT_FLAG:
                    handleFlagSubmission(message);
                    break;
                    
                case GET_CHALLENGE:
                    handleGetChallenge(message);
                    break;
                    
                case LOGOUT:
                    handleLogout();
                    break;
                    
                default:
                    System.out.println("Unknown message type: " + message.getType());
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            sendError("Internal server error");
        }
    }
    
    private void handleLogin(Message message) {
        String username = message.getPayloadAsString();
        
        // Generate session ID
        this.sessionId = UUID.randomUUID().toString();
        this.player = new Player(sessionId, username);
        
        server.registerClient(sessionId, this);
        
        // Send success response
        String response = "LOGIN_SUCCESS:" + sessionId;
        sendMessage(new Message(MessageType.LOGIN_RESPONSE, sessionId, response.getBytes()));
        
        System.out.println("✅ Player logged in: " + username + " [" + sessionId + "]");
    }
    
    private void handleJoinGame(Message message) {
        String gameSessionId = message.getPayloadAsString();
        
        GameSession session = server.getGameSession(gameSessionId);
        if (session == null) {
            sendError("Game session not found");
            return;
        }
        
        if (session.addPlayer(player)) {
            String response = "JOINED:" + session.getSessionName();
            sendMessage(new Message(MessageType.SUCCESS, sessionId, response.getBytes()));
            
            // Notify all players
            String notification = player.getUsername() + " joined the game";
            server.broadcast(gameSessionId, 
                new Message(MessageType.GAME_STATE, null, notification.getBytes()));
                
            System.out.println("🎮 " + player.getUsername() + " joined session: " + gameSessionId);
        } else {
            sendError("Game session is full");
        }
    }
    
    private void handleFlagSubmission(Message message) {
        String payload = message.getPayloadAsString();
        String[] parts = payload.split(":", 2);
        
        if (parts.length != 2) {
            sendError("Invalid flag format");
            return;
        }
        
        String challengeId = parts[0];
        String flag = parts[1];
        
        GameSession session = server.getGameSession("session-1"); // Default session
        boolean correct = session.submitFlag(player.getPlayerId(), challengeId, flag);
        
        if (correct) {
            String response = "CORRECT:" + player.getScore();
            sendMessage(new Message(MessageType.SUCCESS, sessionId, response.getBytes()));
            
            // Broadcast score update
            String notification = player.getUsername() + " solved " + challengeId + "!";
            server.broadcast("session-1", 
                new Message(MessageType.SCORE_UPDATE, null, notification.getBytes()));
                
            System.out.println("🎯 " + player.getUsername() + " solved challenge: " + challengeId);
        } else {
            sendError("Incorrect flag");
        }
    }
    
    private void handleGetChallenge(Message message) {
        GameSession session = server.getGameSession("session-1");
        
        StringBuilder challenges = new StringBuilder();
        for (Challenge ch : session.getChallenges()) {
            challenges.append(String.format("%s|%s|%s|%d|%d\n",
                ch.getChallengeId(),
                ch.getTitle(),
                ch.getDescription(),
                ch.getPoints(),
                ch.getSolveCount()));
        }
        
        sendMessage(new Message(MessageType.SUCCESS, sessionId, challenges.toString().getBytes()));
    }
    
    private void handleLogout() {
        running = false;
    }
    
    public void sendMessage(Message message) {
        try {
            byte[] data = message.toBytes();
            output.writeInt(data.length);
            output.write(data);
            output.flush();
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }
    
    private void sendError(String errorMessage) {
        sendMessage(new Message(MessageType.ERROR, sessionId, errorMessage.getBytes()));
    }
    
    private void cleanup() {
        try {
            running = false;
            if (sessionId != null) {
                server.unregisterClient(sessionId);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("🔌 Client disconnected: " + 
                (player != null ? player.getUsername() : "Unknown"));
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}
