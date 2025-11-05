package com.netbattle.api;

import java.util.*;
import java.util.concurrent.*;

public class GameDataManager {
    private static GameDataManager instance;
    
    private Map<String, PlayerData> players;
    private Map<String, Set<String>> solvedChallenges;
    private Map<String, Long> playerLoginTimes;
    private Map<String, Map<String, Integer>> dailyActivity;
    private long serverStartTime;
    
    private GameDataManager() {
        this.players = new ConcurrentHashMap<>();
        this.solvedChallenges = new ConcurrentHashMap<>();
        this.playerLoginTimes = new ConcurrentHashMap<>();
        this.dailyActivity = new ConcurrentHashMap<>();
        this.serverStartTime = System.currentTimeMillis();
    }
    
    public static synchronized GameDataManager getInstance() {
        if (instance == null) {
            instance = new GameDataManager();
        }
        return instance;
    }
    
    public void registerPlayer(String playerId, String username) {
        if (!players.containsKey(playerId)) {
            players.put(playerId, new PlayerData(playerId, username));
            solvedChallenges.put(playerId, ConcurrentHashMap.newKeySet());
            playerLoginTimes.put(playerId, System.currentTimeMillis());
            System.out.println("📝 Registered player: " + username + " (ID: " + playerId + ")");
        } else {
            playerLoginTimes.put(playerId, System.currentTimeMillis());
            System.out.println("👤 Player logged in: " + username);
        }
    }
    
    public boolean submitFlag(String playerId, String challengeId, int points) {
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
        return true;
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
        return date.getDayOfWeek().toString().substring(0, 3);
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
        if (player == null) {
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
        
        return stats;
    }
    
    private List<Map<String, Object>> getWeeklyActivity(String playerId) {
        List<Map<String, Object>> weeklyData = new ArrayList<>();
        String[] daysOfWeek = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        
        Map<String, Integer> playerActivity = dailyActivity.getOrDefault(playerId, new HashMap<>());
        
        for (String day : daysOfWeek) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("day", day);
            dayData.put("solves", playerActivity.getOrDefault(day.toUpperCase(), 0));
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
        
        long activeCount = players.values().stream()
            .filter(p -> isPlayerOnline(p.playerId))
            .count();
        
        stats.put("activePlayers", (int) activeCount);
        stats.put("totalPlayers", players.size());
        stats.put("totalChallenges", 5);
        stats.put("uptime", System.currentTimeMillis() - serverStartTime);
        
        return stats;
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

