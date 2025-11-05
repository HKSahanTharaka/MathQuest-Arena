// server/nio/NIOGameStateManager.java
package com.netbattle.server.nio;

import com.netbattle.common.model.*;
import com.netbattle.common.protocol.*;
import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NIOGameStateManager {
    private static final int NIO_PORT = 8081;
    private static final int BUFFER_SIZE = 8192;
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Map<SocketChannel, PlayerSession> playerSessions;
    private Map<String, GameSession> gameSessions;
    private volatile boolean running;
    
    public NIOGameStateManager() {
        this.playerSessions = new ConcurrentHashMap<>();
        this.gameSessions = new ConcurrentHashMap<>();
        this.running = false;
    }
    
    public void start() throws IOException {
        // Initialize selector
        selector = Selector.open();
        
        // Create non-blocking server socket
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(NIO_PORT));
        
        // Register for ACCEPT events
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("⚡ NIO Game State Manager started on port " + NIO_PORT);
        running = true;
        
        // Event loop
        while (running) {
            try {
                // Wait for events (timeout: 1 second)
                int readyChannels = selector.select(1000);
                
                if (readyChannels == 0) {
                    continue;
                }
                
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
                
                // Periodic game state broadcast (every 100ms)
                broadcastGameState();
                
            } catch (IOException e) {
                System.err.println("Error in NIO event loop: " + e.getMessage());
            }
        }
    }
    
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            PlayerSession session = new PlayerSession(clientChannel);
            playerSessions.put(clientChannel, session);
            
            System.out.println("⚡ NIO: New client connected - " + clientChannel.getRemoteAddress());
        }
    }
    
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        PlayerSession session = playerSessions.get(channel);
        
        if (session == null) {
            return;
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int bytesRead;
        
        try {
            bytesRead = channel.read(buffer);
        } catch (IOException e) {
            System.out.println("⚡ Client disconnected");
            closeChannel(channel);
            return;
        }
        
        if (bytesRead == -1) {
            closeChannel(channel);
            return;
        }
        
        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        
        // Process message
        try {
            Message message = Message.fromBytes(data);
            processMessage(channel, session, message);
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }
    
    private void processMessage(SocketChannel channel, PlayerSession session, Message message) {
        switch (message.getType()) {
            case PLAYER_POSITION:
                updatePlayerPosition(session, message);
                break;
                
            case JOIN_GAME:
                handleJoinGame(session, message);
                break;
                
            case GET_CHALLENGE:
                sendChallenges(channel, session);
                break;
                
            default:
                System.out.println("⚡ NIO: Unhandled message type: " + message.getType());
        }
    }
    
    private void updatePlayerPosition(PlayerSession session, Message message) {
        String[] coords = message.getPayloadAsString().split(",");
        if (coords.length == 2) {
            try {
                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                
                if (session.getPlayer() != null) {
                    session.getPlayer().setPosition(new Position(x, y));
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid position data");
            }
        }
    }
    
    private void handleJoinGame(PlayerSession session, Message message) {
        String gameSessionId = message.getPayloadAsString();
        GameSession gameSession = gameSessions.get(gameSessionId);
        
        if (gameSession == null) {
            // Create new game session if doesn't exist
            gameSession = new GameSession(gameSessionId, "Arena " + gameSessionId, 50);
            gameSessions.put(gameSessionId, gameSession);
        }
        
        // Create player if not exists
        if (session.getPlayer() == null) {
            Player player = new Player(UUID.randomUUID().toString(), "Player_NIO_" + 
                session.getChannel().hashCode());
            session.setPlayer(player);
        }
        
        gameSession.addPlayer(session.getPlayer());
        session.setGameSessionId(gameSessionId);
        
        // Send confirmation
        writeToChannel(session.getChannel(), 
            new Message(MessageType.SUCCESS, null, "JOINED".getBytes()));
    }
    
    private void sendChallenges(SocketChannel channel, PlayerSession session) {
        GameSession gameSession = gameSessions.get(session.getGameSessionId());
        if (gameSession != null) {
            StringBuilder sb = new StringBuilder();
            for (Challenge ch : gameSession.getChallenges()) {
                sb.append(ch.getChallengeId()).append(",")
                  .append(ch.getTitle()).append(",")
                  .append(ch.getPoints()).append(";");
            }
            
            writeToChannel(channel, 
                new Message(MessageType.SUCCESS, null, sb.toString().getBytes()));
        }
    }
    
    // Broadcast game state to all connected clients
    private void broadcastGameState() {
        for (GameSession gameSession : gameSessions.values()) {
            List<Player> leaderboard = gameSession.getLeaderboard();
            
            StringBuilder state = new StringBuilder("LEADERBOARD:");
            for (int i = 0; i < Math.min(10, leaderboard.size()); i++) {
                Player p = leaderboard.get(i);
                state.append(p.getUsername()).append(":")
                     .append(p.getScore()).append(",");
            }
            
            Message stateMessage = new Message(MessageType.GAME_STATE, null, 
                state.toString().getBytes());
            
            // Send to all players in this session
            for (Map.Entry<SocketChannel, PlayerSession> entry : playerSessions.entrySet()) {
                if (gameSession.getSessionId().equals(entry.getValue().getGameSessionId())) {
                    writeToChannel(entry.getKey(), stateMessage);
                }
            }
        }
    }
    
    private void handleWrite(SelectionKey key) {
        // Handle pending writes if necessary
    }
    
    private void writeToChannel(SocketChannel channel, Message message) {
        try {
            byte[] data = message.toBytes();
            ByteBuffer buffer = ByteBuffer.allocate(data.length + 4);
            buffer.putInt(data.length);
            buffer.put(data);
            buffer.flip();
            
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } catch (IOException e) {
            System.err.println("Error writing to channel: " + e.getMessage());
        }
    }
    
    private void closeChannel(SocketChannel channel) {
        try {
            playerSessions.remove(channel);
            channel.close();
        } catch (IOException e) {
            System.err.println("Error closing channel: " + e.getMessage());
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (selector != null) {
                selector.close();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
        } catch (IOException e) {
            System.err.println("Error stopping NIO manager: " + e.getMessage());
        }
    }
    
    // Inner class to track player sessions
    private static class PlayerSession {
        private SocketChannel channel;
        private Player player;
        private String gameSessionId;
        
        public PlayerSession(SocketChannel channel) {
            this.channel = channel;
        }
        
        public SocketChannel getChannel() { return channel; }
        public Player getPlayer() { return player; }
        public void setPlayer(Player player) { this.player = player; }
        public String getGameSessionId() { return gameSessionId; }
        public void setGameSessionId(String gameSessionId) { 
            this.gameSessionId = gameSessionId; 
        }
    }
    
    public static void main(String[] args) {
        NIOGameStateManager manager = new NIOGameStateManager();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n⚡ Shutting down NIO manager...");
            manager.stop();
        }));
        
        try {
            manager.start();
        } catch (IOException e) {
            System.err.println("Failed to start NIO manager: " + e.getMessage());
            e.printStackTrace();
        }
    }
}