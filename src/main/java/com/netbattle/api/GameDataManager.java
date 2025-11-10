package com.netbattle.api;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class GameDataManager {
    private static GameDataManager instance;
    private static final int MINIMUM_PLAYERS = 5;
    
    private Map<String, PlayerData> players;
    private Map<String, Set<String>> solvedChallenges;
    private Map<String, Long> playerLoginTimes;
    private Map<String, Map<String, Integer>> dailyActivity;
    private Set<String> activePlayerIds; // Track currently active players
    private Map<String, String> activeUsernames; // Track active usernames (username -> playerId)
    private long serverStartTime;
    private volatile boolean gameReady;
    
    private GameDataManager() {
        this.players = new ConcurrentHashMap<>();
        this.solvedChallenges = new ConcurrentHashMap<>();
        this.playerLoginTimes = new ConcurrentHashMap<>();
        this.dailyActivity = new ConcurrentHashMap<>();
        this.activePlayerIds = ConcurrentHashMap.newKeySet();
        this.activeUsernames = new ConcurrentHashMap<>();
        this.serverStartTime = System.currentTimeMillis();
        this.gameReady = false;
    }
    
    public static synchronized GameDataManager getInstance() {
        if (instance == null) {
            instance = new GameDataManager();
        }
        return instance;
    }
    
    /**
     * Check if a username is already taken by an active player
     * @param username The username to check
     * @return true if username is already taken, false otherwise
     */
    public boolean isUsernameTaken(String username) {
        String normalizedUsername = username.toLowerCase().trim();
        String existingPlayerId = activeUsernames.get(normalizedUsername);
        
        if (existingPlayerId != null) {
            // Check if the existing player is still active
            if (activePlayerIds.contains(existingPlayerId)) {
                return true;
            } else {
                // Player is no longer active, remove from active usernames
                activeUsernames.remove(normalizedUsername);
            }
        }
        
        return false;
    }
    
    /**
     * Register a player with a username
     * @param playerId The unique player ID
     * @param username The username (must be unique among active players)
     * @throws IllegalArgumentException if username is already taken
     */
    public void registerPlayer(String playerId, String username) {
        String normalizedUsername = username.toLowerCase().trim();
        
        // Check if username is already taken by an active player
        String existingPlayerId = activeUsernames.get(normalizedUsername);
        if (existingPlayerId != null && activePlayerIds.contains(existingPlayerId) && !existingPlayerId.equals(playerId)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken by another player");
        }
        
        boolean isNewPlayer = !players.containsKey(playerId);
        boolean wasAlreadyActive = activePlayerIds.contains(playerId);
        
        if (isNewPlayer) {
            players.put(playerId, new PlayerData(playerId, username));
            solvedChallenges.put(playerId, ConcurrentHashMap.newKeySet());
            playerLoginTimes.put(playerId, System.currentTimeMillis());
            System.out.println("📝 Registered player: " + username + " (ID: " + playerId + ")");
        } else {
            // Player is reconnecting - verify username matches
            PlayerData player = players.get(playerId);
            if (player != null && !player.username.equals(username)) {
                // Username mismatch - this shouldn't happen, but log it
                System.out.println("⚠️  Warning: Username mismatch for player " + playerId + 
                    ". Expected: " + player.username + ", Got: " + username);
            }
            playerLoginTimes.put(playerId, System.currentTimeMillis());
            System.out.println("👤 Player logged in: " + username);
        }
        
        // Add to active players and active usernames
        activePlayerIds.add(playerId);
        activeUsernames.put(normalizedUsername, playerId);
        
        // Check if game is ready (minimum players reached)
        updateGameReadyStatus();
        
        // Broadcast player join event if this is a new active player
        if (!wasAlreadyActive) {
            broadcastPlayerJoinToWebSocket(username, activePlayerIds.size(), MINIMUM_PLAYERS);
            // Also broadcast server stats update
            broadcastServerStatsUpdate();
        }
    }
    
    private void broadcastServerStatsUpdate() {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:8083/broadcast/server-stats");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                
                Map<String, Object> serverStats = getServerStats();
                String json = String.format(
                    "{\"activePlayers\":%d,\"totalPlayers\":%d,\"totalChallenges\":%d,\"uptime\":%d,\"gameReady\":%b,\"minimumPlayers\":%d,\"currentPlayerCount\":%d}",
                    serverStats.get("activePlayers"), serverStats.get("totalPlayers"), 
                    serverStats.get("totalChallenges"), serverStats.get("uptime"),
                    serverStats.get("gameReady"), serverStats.get("minimumPlayers"),
                    serverStats.get("currentPlayerCount")
                );
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("📡 Broadcasted server stats update");
                }
                conn.disconnect();
            } catch (Exception e) {
                // WebSocket server might not be running, ignore silently
            }
        }).start();
    }
    
    public void removeActivePlayer(String playerId) {
        activePlayerIds.remove(playerId);
        
        // Remove from active usernames when player becomes inactive
        PlayerData player = players.get(playerId);
        if (player != null) {
            String normalizedUsername = player.username.toLowerCase().trim();
            String mappedPlayerId = activeUsernames.get(normalizedUsername);
            if (playerId.equals(mappedPlayerId)) {
                activeUsernames.remove(normalizedUsername);
            }
        }
        
        updateGameReadyStatus();
    }
    
    private void updateGameReadyStatus() {
        boolean wasReady = gameReady;
        gameReady = activePlayerIds.size() >= MINIMUM_PLAYERS;
        
        if (!wasReady && gameReady) {
            System.out.println("✅ Game is now ready! Minimum players (" + MINIMUM_PLAYERS + ") reached!");
        } else if (wasReady && !gameReady) {
            System.out.println("⏳ Waiting for more players. Currently: " + activePlayerIds.size() + "/" + MINIMUM_PLAYERS);
        }
        
        // Broadcast game status update via WebSocket HTTP API
        broadcastGameStatusToWebSocket(gameReady, activePlayerIds.size(), MINIMUM_PLAYERS);
        // Also broadcast server stats when game status changes
        broadcastServerStatsUpdate();
    }
    
    public boolean isGameReady() {
        return gameReady;
    }
    
    public int getActivePlayerCount() {
        return activePlayerIds.size();
    }
    
    public int getMinimumPlayers() {
        return MINIMUM_PLAYERS;
    }
    
    public boolean submitFlag(String playerId, String challengeId, int points) {
        // Check if game is ready (minimum players joined)
        if (!gameReady) {
            System.out.println("⏸️  Cannot submit flag: Waiting for minimum players (" + activePlayerIds.size() + "/" + MINIMUM_PLAYERS + ")");
            return false;
        }
        
        PlayerData player = players.get(playerId);
        if (player == null) return false;
        
        Set<String> solved = solvedChallenges.get(playerId);
        if (solved == null) {
            solved = ConcurrentHashMap.newKeySet();
            solvedChallenges.put(playerId, solved);
        }
        
        if (solved.contains(challengeId)) {
            return false;
        }
        
        solved.add(challengeId);
        player.addScore(points);
        player.incrementChallengesSolved();
        
        recordDailyActivity(playerId);
        
        System.out.println("✅ " + player.username + " solved problem " + challengeId + " (+"+points+" points)");
        
        // Get challenge name for broadcast
        String challengeName = getChallengeName(challengeId);
        
        // Broadcast score update via WebSocket
        broadcastScoreUpdate(playerId, player.username, player.totalScore, player.challengesSolved, calculatePlayerRank(playerId));
        
        // Broadcast activity event
        broadcastActivity(player.username, challengeId, points);
        
        // Broadcast challenge solved event
        broadcastChallengeSolved(playerId, player.username, challengeId, challengeName, points);
        
        // Broadcast leaderboard update
        broadcastLeaderboardUpdate();
        
        return true;
    }
    
    private String getChallengeName(String challengeId) {
        switch (challengeId) {
            case "1": return "Basic Arithmetic";
            case "2": return "Fibonacci Sequence";
            case "3": return "Prime Numbers";
            case "4": return "Quadratic Equation";
            case "5": return "Circle Area";
            default: return "Challenge " + challengeId;
        }
    }
    
    private void broadcastChallengeSolved(String playerId, String username, String challengeId, String challengeName, int points) {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:8083/broadcast/challenge-solved");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                
                String json = String.format(
                    "{\"playerId\":\"%s\",\"username\":\"%s\",\"challengeId\":\"%s\",\"challengeName\":\"%s\",\"points\":%d}",
                    playerId, username, challengeId, challengeName, points
                );
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("📡 Broadcasted challenge solved: " + username + " solved " + challengeId);
                }
                conn.disconnect();
            } catch (Exception e) {
                // WebSocket server might not be running, ignore silently
            }
        }).start();
    }
    
    private void recordDailyActivity(String playerId) {
        String today = getDayOfWeek();
        
        Map<String, Integer> playerActivity = dailyActivity.computeIfAbsent(
            playerId, 
            k -> new ConcurrentHashMap<>()
        );
        
        playerActivity.merge(today, 1, Integer::sum);
    }
    
    private String getDayOfWeek() {
        java.time.LocalDate date = java.time.LocalDate.now();
        String day = date.getDayOfWeek().toString().substring(0, 3);
        // Capitalize first letter, lowercase rest (e.g., "MON" -> "Mon")
        return day.charAt(0) + day.substring(1).toLowerCase();
    }
    
    public List<Map<String, Object>> getLeaderboard() {
        List<Map<String, Object>> leaderboard = new ArrayList<>();
        
        List<PlayerData> sortedPlayers = new ArrayList<>(players.values());
        sortedPlayers.sort((p1, p2) -> Integer.compare(p2.totalScore, p1.totalScore));
        
        int rank = 1;
        for (PlayerData player : sortedPlayers) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", player.playerId);
            entry.put("username", player.username);
            entry.put("score", player.totalScore);
            entry.put("challengesSolved", player.challengesSolved);
            entry.put("isOnline", isPlayerOnline(player.playerId));
            entry.put("rank", rank++);
            leaderboard.add(entry);
        }
        
        return leaderboard;
    }
    
    public Map<String, Object> getPlayerStats(String playerId) {
        PlayerData player = players.get(playerId);
        
        // If player doesn't exist, try to find by matching playerId prefix
        // (in case playerId format changed or there's a mismatch)
        if (player == null) {
            System.out.println("⚠️  Player not found with exact ID: " + playerId);
            // Try to find player by username if playerId contains username
            for (PlayerData p : players.values()) {
                if (p.playerId.equals(playerId) || playerId.contains(p.username)) {
                    player = p;
                    System.out.println("✅ Found player by username match: " + p.username);
                    break;
                }
            }
        }
        
        if (player == null) {
            System.out.println("⚠️  Returning default stats for playerId: " + playerId);
            return getDefaultStats(playerId);
        }
        
        Set<String> solved = solvedChallenges.getOrDefault(playerId, new HashSet<>());
        long playTime = calculatePlayTime(playerId);
        int rank = calculatePlayerRank(playerId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("playerId", player.playerId);
        stats.put("username", player.username);
        stats.put("totalScore", player.totalScore);
        stats.put("rank", rank);
        stats.put("challengesSolved", player.challengesSolved);
        stats.put("totalPlayTime", playTime);
        stats.put("firstSeen", player.firstSeen);
        stats.put("lastSeen", System.currentTimeMillis());
        stats.put("solvedChallenges", new ArrayList<>(solved));
        stats.put("scoreHistory", formatScoreHistory(player.scoreHistory));
        stats.put("weeklyActivity", getWeeklyActivity(playerId));
        
        System.out.println("✅ Stats for " + player.username + ": Score=" + player.totalScore + 
                          ", Solved=" + player.challengesSolved + ", Rank=" + rank);
        
        return stats;
    }
    
    private List<Map<String, Object>> getWeeklyActivity(String playerId) {
        List<Map<String, Object>> weeklyData = new ArrayList<>();
        String[] daysOfWeek = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        
        Map<String, Integer> playerActivity = dailyActivity.getOrDefault(playerId, new HashMap<>());
        
        for (String day : daysOfWeek) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("day", day);
            // Check both uppercase and capitalized versions for backwards compatibility
            int solves = playerActivity.getOrDefault(day.toUpperCase(), 0);
            if (solves == 0) {
                solves = playerActivity.getOrDefault(day, 0);
            }
            dayData.put("solves", solves);
            weeklyData.add(dayData);
        }
        
        return weeklyData;
    }
    
    private List<Map<String, Object>> formatScoreHistory(List<ScoreEvent> history) {
        List<Map<String, Object>> formatted = new ArrayList<>();
        for (ScoreEvent event : history) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("score", event.score);
            entry.put("timestamp", event.timestamp);
            formatted.add(entry);
        }
        return formatted;
    }
    
    private Map<String, Object> getDefaultStats(String playerId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("playerId", playerId);
        stats.put("username", "Unknown");
        stats.put("totalScore", 0);
        stats.put("rank", players.size() + 1);
        stats.put("challengesSolved", 0);
        stats.put("totalPlayTime", 0);
        stats.put("firstSeen", System.currentTimeMillis());
        stats.put("lastSeen", System.currentTimeMillis());
        stats.put("solvedChallenges", new ArrayList<>());
        stats.put("scoreHistory", new ArrayList<>());
        stats.put("weeklyActivity", getWeeklyActivity(playerId)); // Include weekly activity even for new players
        return stats;
    }
    
    public List<String> getPlayerAchievements(String playerId) {
        List<String> achievements = new ArrayList<>();
        PlayerData player = players.get(playerId);
        
        if (player == null) {
            return achievements;
        }
        
        Set<String> solved = solvedChallenges.getOrDefault(playerId, new HashSet<>());
        
        if (!solved.isEmpty()) {
            achievements.add("First Solver");
        }
        
        if (player.challengesSolved >= 3) {
            achievements.add("Math Enthusiast");
        }
        
        if (player.challengesSolved >= 5) {
            achievements.add("Math Wizard");
        }
        
        if (player.totalScore >= 500) {
            achievements.add("Point Collector");
        }
        
        if (player.totalScore >= 1000) {
            achievements.add("Mathematical Genius");
        }
        
        int rank = calculatePlayerRank(playerId);
        if (rank == 1) {
            achievements.add("Top Mathematician");
        } else if (rank <= 3) {
            achievements.add("Elite Solver");
        }
        
        return achievements;
    }
    
    public Map<String, Object> getServerStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Use activePlayerIds.size() for active players (players currently in game)
        // This is more accurate than checking last seen time
        int activeCount = activePlayerIds.size();
        
        stats.put("activePlayers", activeCount);
        stats.put("totalPlayers", players.size());
        stats.put("totalChallenges", 5);
        stats.put("uptime", System.currentTimeMillis() - serverStartTime);
        stats.put("gameReady", gameReady);
        stats.put("minimumPlayers", MINIMUM_PLAYERS);
        stats.put("currentPlayerCount", activeCount);
        
        return stats;
    }
    
    public Map<String, Object> getGameStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("gameReady", gameReady);
        status.put("currentPlayerCount", activePlayerIds.size());
        status.put("minimumPlayers", MINIMUM_PLAYERS);
        status.put("playersNeeded", Math.max(0, MINIMUM_PLAYERS - activePlayerIds.size()));
        return status;
    }
    
    private void broadcastPlayerJoinToWebSocket(String username, int currentCount, int minimumPlayers) {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:8083/broadcast/player-join");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                
                String json = String.format(
                    "{\"username\":\"%s\",\"currentCount\":%d,\"minimumPlayers\":%d}",
                    username, currentCount, minimumPlayers
                );
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("📡 Broadcasted player join: " + username);
                }
                conn.disconnect();
            } catch (Exception e) {
                // WebSocket server might not be running, ignore silently
            }
        }).start();
    }
    
    private void broadcastGameStatusToWebSocket(boolean gameReady, int currentCount, int minimumPlayers) {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:8083/broadcast/game-status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                
                String json = String.format(
                    "{\"gameReady\":%b,\"currentCount\":%d,\"minimumPlayers\":%d}",
                    gameReady, currentCount, minimumPlayers
                );
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("📡 Broadcasted game status: " + currentCount + "/" + minimumPlayers);
                }
                conn.disconnect();
            } catch (Exception e) {
                // WebSocket server might not be running, ignore silently
            }
        }).start();
    }
    
    private void broadcastScoreUpdate(String playerId, String username, int totalScore, int challengesSolved, int rank) {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:8083/broadcast/score-update");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                
                String json = String.format(
                    "{\"playerId\":\"%s\",\"username\":\"%s\",\"totalScore\":%d,\"challengesSolved\":%d,\"rank\":%d}",
                    playerId, username, totalScore, challengesSolved, rank
                );
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("📡 Broadcasted score update for " + username);
                }
                conn.disconnect();
            } catch (Exception e) {
                // WebSocket server might not be running, ignore silently
            }
        }).start();
    }
    
    private void broadcastActivity(String username, String challengeId, int points) {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:8083/broadcast/activity");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                
                String json = String.format(
                    "{\"message\":\"%s solved challenge %s (+%d points)\",\"username\":\"%s\",\"challengeId\":\"%s\",\"points\":%d,\"timestamp\":%d}",
                    username, challengeId, points, username, challengeId, points, System.currentTimeMillis()
                );
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("📡 Broadcasted activity: " + username + " solved " + challengeId);
                }
                conn.disconnect();
            } catch (Exception e) {
                // WebSocket server might not be running, ignore silently
            }
        }).start();
    }
    
    private void broadcastLeaderboardUpdate() {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:8083/broadcast/leaderboard-update");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                
                // Get top 10 players for leaderboard
                List<Map<String, Object>> leaderboard = getLeaderboard();
                String leaderboardJson = convertLeaderboardToJson(leaderboard);
                String json = String.format("{\"leaderboard\":%s}", leaderboardJson);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("📡 Broadcasted leaderboard update");
                }
                conn.disconnect();
            } catch (Exception e) {
                // WebSocket server might not be running, ignore silently
            }
        }).start();
    }
    
    private String convertLeaderboardToJson(List<Map<String, Object>> leaderboard) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < leaderboard.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> entry = leaderboard.get(i);
            sb.append(String.format(
                "{\"id\":\"%s\",\"username\":\"%s\",\"score\":%d,\"challengesSolved\":%d,\"rank\":%d,\"isOnline\":%b}",
                entry.get("id"), entry.get("username"), entry.get("score"), 
                entry.get("challengesSolved"), entry.get("rank"), entry.get("isOnline")
            ));
        }
        sb.append("]");
        return sb.toString();
    }
    
    public boolean hasPlayerSolved(String playerId, String challengeId) {
        Set<String> solved = solvedChallenges.get(playerId);
        return solved != null && solved.contains(challengeId);
    }
    
    public List<Map<String, Object>> getChallengesForPlayer(String playerId) {
        List<Map<String, Object>> challenges = new ArrayList<>();
        
        challenges.add(createChallenge("1", "Basic Arithmetic", 
            "Calculate: What is 7 × 6? Enter the answer as a number.", 
            "easy", 100, "algebra", hasPlayerSolved(playerId, "1")));
        
        challenges.add(createChallenge("2", "Fibonacci Sequence", 
            "Find the 12th number in the Fibonacci sequence (starting with 0, 1). Answer: ?", 
            "medium", 200, "sequences", hasPlayerSolved(playerId, "2")));
        
        challenges.add(createChallenge("3", "Prime Numbers", 
            "How many prime numbers are there between 1 and 50? Enter the count.", 
            "medium", 150, "number-theory", hasPlayerSolved(playerId, "3")));
        
        challenges.add(createChallenge("4", "Quadratic Equation", 
            "Solve for x: x² - 13x + 36 = 0. Enter the larger root (positive integer).", 
            "hard", 300, "algebra", hasPlayerSolved(playerId, "4")));
        
        challenges.add(createChallenge("5", "Circle Area", 
            "A circle has radius 1. What is π to 5 decimal places? Format: 3.14159", 
            "hard", 250, "geometry", hasPlayerSolved(playerId, "5")));
        
        return challenges;
    }
    
    private Map<String, Object> createChallenge(String id, String name, String desc, 
                                                 String difficulty, int points, String category, boolean solved) {
        Map<String, Object> challenge = new HashMap<>();
        challenge.put("id", id);
        challenge.put("name", name);
        challenge.put("description", desc);
        challenge.put("difficulty", difficulty);
        challenge.put("points", points);
        challenge.put("category", category);
        challenge.put("solved", solved);
        return challenge;
    }
    
    private boolean isPlayerOnline(String playerId) {
        Long loginTime = playerLoginTimes.get(playerId);
        if (loginTime == null) return false;
        
        long timeSinceLogin = System.currentTimeMillis() - loginTime;
        return timeSinceLogin < 30 * 60 * 1000;
    }
    
    private long calculatePlayTime(String playerId) {
        PlayerData player = players.get(playerId);
        if (player == null) return 0;
        
        return System.currentTimeMillis() - player.firstSeen;
    }
    
    private int calculatePlayerRank(String playerId) {
        PlayerData player = players.get(playerId);
        if (player == null) return players.size() + 1;
        
        long betterPlayers = players.values().stream()
            .filter(p -> p.totalScore > player.totalScore)
            .count();
        
        return (int) betterPlayers + 1;
    }
    
    public PlayerData getPlayer(String playerId) {
        return players.get(playerId);
    }
    
    public static class PlayerData {
        public final String playerId;
        public final String username;
        public int totalScore;
        public int challengesSolved;
        public final long firstSeen;
        public final List<ScoreEvent> scoreHistory;
        
        public PlayerData(String playerId, String username) {
            this.playerId = playerId;
            this.username = username;
            this.totalScore = 0;
            this.challengesSolved = 0;
            this.firstSeen = System.currentTimeMillis();
            this.scoreHistory = new ArrayList<>();
            this.scoreHistory.add(new ScoreEvent(0, System.currentTimeMillis()));
        }
        
        public void addScore(int points) {
            this.totalScore += points;
            this.scoreHistory.add(new ScoreEvent(this.totalScore, System.currentTimeMillis()));
        }
        
        public void incrementChallengesSolved() {
            this.challengesSolved++;
        }
    }
    
    public static class ScoreEvent {
        public final int score;
        public final long timestamp;
        
        public ScoreEvent(int score, long timestamp) {
            this.score = score;
            this.timestamp = timestamp;
        }
    }
}

