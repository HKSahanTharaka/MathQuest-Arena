// client/GameClient.java
package com.netbattle.client;

import com.netbattle.common.model.*;
import com.netbattle.common.protocol.*;
import com.netbattle.server.rmi.StatisticsService;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.rmi.registry.*;
import java.util.*;
import java.util.concurrent.*;

public class GameClient {
    private static final String SERVER_HOST = "localhost";
    private static final int TCP_PORT = 8080;
    private static final int SSL_PORT = 8443;
    private static final int UDP_PORT = 9000;
    private static final int RMI_PORT = 1099;
    
    private Socket tcpSocket;
    private SSLSocket sslSocket;
    private DatagramSocket udpSocket;
    private StatisticsService rmiService;
    
    private DataInputStream tcpInput;
    private DataOutputStream tcpOutput;
    private DataInputStream sslInput;
    private DataOutputStream sslOutput;
    
    private String sessionToken;
    private String username;
    private ExecutorService executorService;
    private volatile boolean running;
    
    public GameClient() {
        this.executorService = Executors.newFixedThreadPool(3);
        this.running = false;
    }
    
    public void start() {
        try {
            System.out.println("🎮 NetBattle Arena Client");
            System.out.println("==========================\n");
            
            // Step 1: Secure authentication
            authenticateSecure();
            
            // Step 2: Connect to main TCP server
            connectTCP();
            
            // Step 3: Setup UDP for real-time events
            setupUDP();
            
            // Step 4: Connect to RMI statistics service
            connectRMI();
            
            running = true;
            
            // Start listeners
            executorService.execute(this::listenTCP);
            executorService.execute(this::listenUDP);
            
            // Main game loop
            gameLoop();
            
        } catch (Exception e) {
            System.err.println("❌ Client error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }
    
    private void authenticateSecure() throws Exception {
        System.out.println("🔒 Establishing secure connection...");
        
        // Trust all certificates (for development only!)
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {}
            }
        };
        
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        SSLSocketFactory factory = sslContext.getSocketFactory();
        sslSocket = (SSLSocket) factory.createSocket(SERVER_HOST, SSL_PORT);
        
        sslInput = new DataInputStream(sslSocket.getInputStream());
        sslOutput = new DataOutputStream(sslSocket.getOutputStream());
        
        System.out.println("✅ Secure connection established");
        System.out.println("   Cipher: " + sslSocket.getSession().getCipherSuite());
        
        // Login
        Scanner scanner = new Scanner(System.in);
        System.out.print("\nUsername: ");
        username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        
        String credentials = username + ":" + password;
        Message loginMsg = new Message(MessageType.LOGIN_REQUEST, null, 
            credentials.getBytes());
        
        sendSecureMessage(loginMsg);
        
        // Wait for response
        Message response = receiveSecureMessage();
        
        if (response.getType() == MessageType.SUCCESS) {
            String[] parts = response.getPayloadAsString().split(":", 2);
            if (parts.length == 2 && parts[0].equals("TOKEN")) {
                sessionToken = parts[1].split(":")[0];
                System.out.println("✅ Authentication successful!");
                System.out.println("   Session Token: " + sessionToken.substring(0, 16) + "...");
            }
        } else {
            throw new Exception("Authentication failed: " + response.getPayloadAsString());
        }
    }
    
    private void connectTCP() throws IOException {
        System.out.println("\n🔗 Connecting to game server...");
        tcpSocket = new Socket(SERVER_HOST, TCP_PORT);
        tcpInput = new DataInputStream(tcpSocket.getInputStream());
        tcpOutput = new DataOutputStream(tcpSocket.getOutputStream());
        
        System.out.println("✅ Connected to TCP server");
        
        // Send login with token
        Message loginMsg = new Message(MessageType.LOGIN_REQUEST, sessionToken, 
            username.getBytes());
        sendTCPMessage(loginMsg);
        
        // Join default game
        Message joinMsg = new Message(MessageType.JOIN_GAME, sessionToken, 
            "session-1".getBytes());
        sendTCPMessage(joinMsg);
    }
    
    private void setupUDP() throws SocketException {
        System.out.println("📡 Setting up UDP connection...");
        udpSocket = new DatagramSocket();
        
        // Register with UDP server
        Message registerMsg = new Message(MessageType.LOGIN_REQUEST, sessionToken, 
            username.getBytes());
        
        try {
            byte[] data = registerMsg.toBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length,
                InetAddress.getByName(SERVER_HOST), UDP_PORT);
            udpSocket.send(packet);
            
            System.out.println("✅ UDP connection established");
        } catch (IOException e) {
            System.err.println("Error registering UDP: " + e.getMessage());
        }
    }
    
    private void connectRMI() {
        try {
            System.out.println("📊 Connecting to statistics service...");
            Registry registry = LocateRegistry.getRegistry(SERVER_HOST, RMI_PORT);
            rmiService = (StatisticsService) registry.lookup("NetBattleStats");
            
            System.out.println("✅ RMI service connected");
        } catch (Exception e) {
            System.err.println("⚠️  Warning: Could not connect to RMI service");
            System.err.println("   Statistics features will be unavailable");
        }
    }
    
    private void gameLoop() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n🎮 Welcome to NetBattle Arena, " + username + "!");
        showMenu();
        
        while (running) {
            System.out.print("\n> ");
            String command = scanner.nextLine().trim().toLowerCase();
            
            try {
                handleCommand(command);
            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
            }
        }
    }
    
    private void handleCommand(String command) throws Exception {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0];
        
        switch (cmd) {
            case "challenges":
            case "c":
                listChallenges();
                break;
                
            case "submit":
            case "s":
                if (parts.length < 2) {
                    System.out.println("Usage: submit <challengeId>:<flag>");
                    return;
                }
                submitFlag(parts[1]);
                break;
                
            case "leaderboard":
            case "l":
                showLeaderboard();
                break;
                
            case "stats":
                showMyStats();
                break;
                
            case "rank":
                showMyRank();
                break;
                
            case "achievements":
            case "a":
                showAchievements();
                break;
                
            case "chat":
                if (parts.length < 2) {
                    System.out.println("Usage: chat <message>");
                    return;
                }
                sendChat(parts[1]);
                break;
                
            case "help":
            case "h":
                showMenu();
                break;
                
            case "quit":
            case "q":
            case "exit":
                running = false;
                System.out.println("👋 Goodbye!");
                break;
                
            default:
                System.out.println("Unknown command. Type 'help' for options.");
        }
    }
    
    private void listChallenges() throws IOException, InterruptedException {
        System.out.println("\n📋 Available Challenges:");
        System.out.println("========================");
        
        Message msg = new Message(MessageType.GET_CHALLENGE, sessionToken, null);
        sendTCPMessage(msg);
        
        // Response will arrive in listenTCP thread
        Thread.sleep(500); // Wait for response
    }
    
    private void submitFlag(String submission) throws Exception {
        String[] parts = submission.split(":", 2);
        if (parts.length != 2) {
            System.out.println("❌ Invalid format. Use: submit <challengeId>:<flag>");
            return;
        }
        
        System.out.println("🚩 Submitting flag for " + parts[0] + "...");
        
        Message msg = new Message(MessageType.SUBMIT_FLAG, sessionToken, 
            submission.getBytes());
        sendTCPMessage(msg);
        
        Thread.sleep(500); // Wait for response
    }
    
    private void showLeaderboard() throws Exception {
        if (rmiService == null) {
            System.out.println("❌ RMI service not available");
            return;
        }
        
        System.out.println("\n🏆 Global Leaderboard");
        System.out.println("=====================");
        
        List<Player> leaderboard = rmiService.getGlobalLeaderboard();
        
        for (int i = 0; i < Math.min(10, leaderboard.size()); i++) {
            Player p = leaderboard.get(i);
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "  ";
            System.out.printf("%s %2d. %-20s %6d pts\n", 
                medal, i + 1, p.getUsername(), p.getScore());
        }
    }
    
    private void showMyStats() throws Exception {
        if (rmiService == null) {
            System.out.println("❌ RMI service not available");
            return;
        }
        
        PlayerStats stats = rmiService.getPlayerStats(sessionToken);
        
        System.out.println("\n📊 Your Statistics");
        System.out.println("==================");
        System.out.println("Total Score: " + stats.getTotalScore());
        System.out.println("Challenges Solved: " + stats.getChallengesSolved());
        System.out.println("Avg Solve Time: " + 
            String.format("%.2f", stats.getAverageSolveTime() / 1000.0) + "s");
    }
    
    private void showMyRank() throws Exception {
        if (rmiService == null) {
            System.out.println("❌ RMI service not available");
            return;
        }
        
        int rank = rmiService.getPlayerRank(sessionToken);
        System.out.println("\n🏅 Your Rank: #" + rank);
    }
    
    private void showAchievements() throws Exception {
        if (rmiService == null) {
            System.out.println("❌ RMI service not available");
            return;
        }
        
        List<String> achievements = rmiService.checkAchievements(sessionToken);
        
        System.out.println("\n🎖️  Your Achievements");
        System.out.println("===================");
        
        if (achievements.isEmpty()) {
            System.out.println("No achievements yet. Keep playing!");
        } else {
            achievements.forEach(System.out::println);
        }
    }
    
    private void sendChat(String message) throws IOException {
        Message msg = new Message(MessageType.CHAT_MESSAGE, sessionToken, 
            message.getBytes());
        sendUDPMessage(msg);
        System.out.println("💬 Message sent");
    }
    
    private void showMenu() {
        System.out.println("\n📖 Commands:");
        System.out.println("  c, challenges      - List available challenges");
        System.out.println("  s, submit <id>:<flag> - Submit a flag");
        System.out.println("  l, leaderboard     - Show top players");
        System.out.println("  stats              - Show your statistics");
        System.out.println("  rank               - Show your rank");
        System.out.println("  a, achievements    - Show your achievements");
        System.out.println("  chat <message>     - Send a chat message");
        System.out.println("  h, help            - Show this menu");
        System.out.println("  q, quit            - Exit the game");
    }
    
    // TCP Listener
    private void listenTCP() {
        while (running) {
            try {
                Message message = receiveTCPMessage();
                handleTCPMessage(message);
            } catch (IOException e) {
                if (running) {
                    System.err.println("❌ TCP connection lost");
                    running = false;
                }
                break;
            }
        }
    }
    
    private void handleTCPMessage(Message message) {
        switch (message.getType()) {
            case SUCCESS:
                String payload = message.getPayloadAsString();
                
                if (payload.startsWith("CORRECT:")) {
                    int score = Integer.parseInt(payload.split(":")[1]);
                    System.out.println("\n✅ Correct flag! Your score: " + score);
                } else if (payload.startsWith("JOINED:")) {
                    System.out.println("✅ Joined game: " + payload.split(":")[1]);
                } else if (payload.contains("|")) {
                    // Challenge list
                    String[] challenges = payload.split("\n");
                    for (String ch : challenges) {
                        if (ch.trim().isEmpty()) continue;
                        String[] fields = ch.split("\\|");
                        System.out.printf("  [%s] %s - %s (%d pts, %d solves)\n",
                            fields[0], fields[1], fields[2], 
                            Integer.parseInt(fields[3]),
                            Integer.parseInt(fields[4]));
                    }
                }
                break;
                
            case ERROR:
                System.out.println("\n❌ " + message.getPayloadAsString());
                break;
                
            case GAME_STATE:
                System.out.println("\n📢 " + message.getPayloadAsString());
                break;
                
            case SCORE_UPDATE:
                System.out.println("\n🎯 " + message.getPayloadAsString());
                break;
        }
    }
    
    // UDP Listener
    private void listenUDP() {
        byte[] buffer = new byte[4096];
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                Message message = Message.fromBytes(data);
                
                handleUDPMessage(message);
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("❌ UDP error: " + e.getMessage());
                }
            }
        }
    }
    
    private void handleUDPMessage(Message message) {
        switch (message.getType()) {
            case CHAT_MESSAGE:
                System.out.println("\n💬 " + message.getPayloadAsString());
                System.out.print("> ");
                break;
                
            case FLAG_CAPTURED:
                System.out.println("\n🚩 " + message.getPayloadAsString());
                System.out.print("> ");
                break;
                
            case GAME_STATE:
                // Handle game state updates silently
                break;
        }
    }
    
    // Message sending methods
    private void sendTCPMessage(Message message) throws IOException {
        byte[] data = message.toBytes();
        tcpOutput.writeInt(data.length);
        tcpOutput.write(data);
        tcpOutput.flush();
    }
    
    private Message receiveTCPMessage() throws IOException {
        int length = tcpInput.readInt();
        byte[] data = new byte[length];
        tcpInput.readFully(data);
        return Message.fromBytes(data);
    }
    
    private void sendSecureMessage(Message message) throws IOException {
        byte[] data = message.toBytes();
        sslOutput.writeInt(data.length);
        sslOutput.write(data);
        sslOutput.flush();
    }
    
    private Message receiveSecureMessage() throws IOException {
        int length = sslInput.readInt();
        byte[] data = new byte[length];
        sslInput.readFully(data);
        return Message.fromBytes(data);
    }
    
    private void sendUDPMessage(Message message) throws IOException {
        byte[] data = message.toBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length,
            InetAddress.getByName(SERVER_HOST), UDP_PORT);
        udpSocket.send(packet);
    }
    
    private void disconnect() {
        running = false;
        executorService.shutdown();
        
        try {
            if (tcpSocket != null) tcpSocket.close();
            if (sslSocket != null) sslSocket.close();
            if (udpSocket != null) udpSocket.close();
        } catch (IOException e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        }
        
        System.out.println("🔌 Disconnected from server");
    }
    
    public static void main(String[] args) {
        GameClient client = new GameClient();
        client.start();
    }
}