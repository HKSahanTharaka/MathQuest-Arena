// server/security/SecureAuthServer.java
package com.netbattle.server.security;

import com.netbattle.common.model.*;
import com.netbattle.common.protocol.*;
import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

public class SecureAuthServer {
    private static final int SSL_PORT = 8443;
    private static final String KEYSTORE_PATH = "resources/keystore.jks";
    private static final String KEYSTORE_PASSWORD = "netbattle123";
    
    private SSLServerSocket sslServerSocket;
    private ExecutorService threadPool;
    private Map<String, AuthSession> activeSessions;
    private Map<String, String> userCredentials; // username -> hashedPassword
    private volatile boolean running;
    
    public SecureAuthServer() {
        this.threadPool = Executors.newFixedThreadPool(20);
        this.activeSessions = new ConcurrentHashMap<>();
        this.userCredentials = new ConcurrentHashMap<>();
        loadUsers();
    }
    
    public void start() throws Exception {
        // Setup SSL context
        SSLContext sslContext = createSSLContext();
        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
        
        sslServerSocket = (SSLServerSocket) factory.createServerSocket(SSL_PORT);
        
        // Enable specific cipher suites (secure ones)
        String[] enabledCiphers = {
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256"
        };
        sslServerSocket.setEnabledCipherSuites(enabledCiphers);
        
        // Require client to send certificate (optional - set to false for now)
        sslServerSocket.setNeedClientAuth(false);
        
        System.out.println("🔒 Secure Authentication Server started on port " + SSL_PORT);
        System.out.println("🔐 SSL/TLS enabled with strong cipher suites");
        
        running = true;
        
        while (running) {
            try {
                SSLSocket clientSocket = (SSLSocket) sslServerSocket.accept();
                
                System.out.println("🔒 Secure connection from: " + 
                    clientSocket.getInetAddress());
                System.out.println("   Cipher: " + clientSocket.getSession().getCipherSuite());
                
                SecureClientHandler handler = new SecureClientHandler(clientSocket, this);
                threadPool.execute(handler);
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting secure connection: " + e.getMessage());
                }
            }
        }
    }
    
    private SSLContext createSSLContext() throws Exception {
        // Load keystore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        
        try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }
        
        // Initialize key manager factory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());
        
        // Initialize trust manager factory
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        
        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), 
            new SecureRandom());
        
        return sslContext;
    }
    
    private void loadUsers() {
        // In production, load from database
        // For demo, create some test users with hashed passwords
        try {
            userCredentials.put("admin", hashPassword("admin123"));
            userCredentials.put("player1", hashPassword("password1"));
            userCredentials.put("player2", hashPassword("password2"));
            userCredentials.put("ctf_master", hashPassword("ctf2024"));
            
            System.out.println("👥 Loaded " + userCredentials.size() + " user accounts");
        } catch (Exception e) {
            System.err.println("Error loading users: " + e.getMessage());
        }
    }
    
    // Authenticate user
    public AuthResult authenticate(String username, String password) {
        try {
            String storedHash = userCredentials.get(username);
            
            if (storedHash == null) {
                return new AuthResult(false, null, "User not found");
            }
            
            String providedHash = hashPassword(password);
            
            if (storedHash.equals(providedHash)) {
                // Generate session token
                String token = generateSessionToken();
                AuthSession session = new AuthSession(username, token);
                activeSessions.put(token, session);
                
                System.out.println("✅ User authenticated: " + username);
                return new AuthResult(true, token, "Authentication successful");
            } else {
                System.out.println("❌ Failed authentication attempt: " + username);
                return new AuthResult(false, null, "Invalid password");
            }
            
        } catch (Exception e) {
            return new AuthResult(false, null, "Authentication error");
        }
    }
    
    // Register new user
    public boolean registerUser(String username, String password) {
        if (userCredentials.containsKey(username)) {
            return false;
        }
        
        try {
            String hashedPassword = hashPassword(password);
            userCredentials.put(username, hashedPassword);
            
            System.out.println("📝 New user registered: " + username);
            return true;
        } catch (Exception e) {
            System.err.println("Error registering user: " + e.getMessage());
            return false;
        }
    }
    
    // Validate session token
    public boolean validateToken(String token) {
        AuthSession session = activeSessions.get(token);
        if (session == null) {
            return false;
        }
        
        // Check if session expired (30 minutes)
        long sessionAge = System.currentTimeMillis() - session.getCreatedAt();
        if (sessionAge > 30 * 60 * 1000) {
            activeSessions.remove(token);
            return false;
        }
        
        return true;
    }
    
    // Logout
    public void logout(String token) {
        AuthSession session = activeSessions.remove(token);
        if (session != null) {
            System.out.println("👋 User logged out: " + session.getUsername());
        }
    }
    
    public AuthSession getSession(String token) {
        return activeSessions.get(token);
    }
    
    // Hash password using SHA-256
    private String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(password.getBytes());
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
    
    // Generate secure session token
    private String generateSessionToken() {
        return UUID.randomUUID().toString() + "-" + 
               System.currentTimeMillis() + "-" +
               new Random().nextInt(10000);
    }
    
    public void stop() {
        running = false;
        threadPool.shutdown();
        
        try {
            if (sslServerSocket != null && !sslServerSocket.isClosed()) {
                sslServerSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error stopping secure server: " + e.getMessage());
        }
    }
    
    // Authentication result class
    public static class AuthResult {
        private boolean success;
        private String token;
        private String message;
        
        public AuthResult(boolean success, String token, String message) {
            this.success = success;
            this.token = token;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getToken() { return token; }
        public String getMessage() { return message; }
    }
    
    // Authentication session class
    public static class AuthSession {
        private String username;
        private String token;
        private long createdAt;
        
        public AuthSession(String username, String token) {
            this.username = username;
            this.token = token;
            this.createdAt = System.currentTimeMillis();
        }
        
        public String getUsername() { return username; }
        public String getToken() { return token; }
        public long getCreatedAt() { return createdAt; }
    }
    
    public static void main(String[] args) {
        SecureAuthServer server = new SecureAuthServer();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🔒 Shutting down secure server...");
            server.stop();
        }));
        
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start secure server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}