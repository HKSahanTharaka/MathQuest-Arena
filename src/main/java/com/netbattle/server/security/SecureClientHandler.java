// server/security/SecureClientHandler.java
package com.netbattle.server.security;

import com.netbattle.common.protocol.*;
import javax.net.ssl.SSLSocket;
import java.io.*;

public class SecureClientHandler implements Runnable {
    private SSLSocket socket;
    private SecureAuthServer server;
    private DataInputStream input;
    private DataOutputStream output;
    private volatile boolean running;
    
    public SecureClientHandler(SSLSocket socket, SecureAuthServer server) {
        this.socket = socket;
        this.server = server;
        this.running = true;
    }
    
    @Override
    public void run() {
        try {
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            
            System.out.println("🔒 Secure handler started for " + socket.getInetAddress());
            
            while (running && !socket.isClosed()) {
                try {
                    int length = input.readInt();
                    byte[] data = new byte[length];
                    input.readFully(data);
                    
                    Message message = Message.fromBytes(data);
                    handleMessage(message);
                    
                } catch (EOFException e) {
                    break;
                } catch (IOException e) {
                    System.err.println("Error reading secure message: " + e.getMessage());
                    break;
                }
            }
            
        } catch (IOException e) {
            System.err.println("Secure client handler error: " + e.getMessage());
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
                    
                case LOGOUT:
                    handleLogout(message);
                    break;
                    
                default:
                    // Validate token for other requests
                    String token = message.getSessionId();
                    if (!server.validateToken(token)) {
                        sendError("Invalid or expired session");
                        return;
                    }
                    
                    // Process other secure operations here
                    System.out.println("🔒 Processing secure request: " + message.getType());
            }
        } catch (Exception e) {
            System.err.println("Error handling secure message: " + e.getMessage());
            sendError("Internal error");
        }
    }
    
    private void handleLogin(Message message) {
        String payload = message.getPayloadAsString();
        String[] credentials = payload.split(":", 2);
        
        if (credentials.length != 2) {
            sendError("Invalid credentials format");
            return;
        }
        
        String username = credentials[0];
        String password = credentials[1];
        
        // Check if it's a registration request
        if (username.startsWith("REGISTER:")) {
            username = username.substring(9);
            boolean registered = server.registerUser(username, password);
            
            if (registered) {
                sendSuccess("REGISTERED:" + username);
            } else {
                sendError("Username already exists");
            }
            return;
        }
        
        // Authenticate
        SecureAuthServer.AuthResult result = server.authenticate(username, password);
        
        if (result.isSuccess()) {
            String response = "TOKEN:" + result.getToken() + ":" + username;
            sendSuccess(response);
        } else {
            sendError(result.getMessage());
        }
    }
    
    private void handleLogout(Message message) {
        String token = message.getSessionId();
        server.logout(token);
        sendSuccess("LOGGED_OUT");
    }
    
    private void sendSuccess(String data) {
        sendMessage(new Message(MessageType.SUCCESS, null, data.getBytes()));
    }
    
    private void sendError(String error) {
        sendMessage(new Message(MessageType.ERROR, null, error.getBytes()));
    }
    
    private void sendMessage(Message message) {
        try {
            byte[] data = message.toBytes();
            output.writeInt(data.length);
            output.write(data);
            output.flush();
        } catch (IOException e) {
            System.err.println("Error sending secure message: " + e.getMessage());
        }
    }
    
    private void cleanup() {
        try {
            running = false;
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}