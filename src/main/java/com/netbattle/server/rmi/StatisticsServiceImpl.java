// server/rmi/StatisticsServiceImpl.java
package com.netbattle.server.rmi;

import com.netbattle.common.model.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StatisticsServiceImpl extends UnicastRemoteObject 
                                   implements StatisticsService {
    
    private Map<String, PlayerStats> playerStats;
    private Map<String, ChallengeStats> challengeStats;
    private ServerStats serverStats;
    
    // Achievement thresholds
    private static final int FIRST_BLOOD_POINTS = 100;
    private static final int SPEEDRUNNER_TIME = 60000; // 1 minute
    private static final int VETERAN_CHALLENGES = 10;
    
    public StatisticsServiceImpl() throws RemoteException {
        super();
        this.playerStats = new ConcurrentHashMap<>();
        this.challengeStats = new ConcurrentHashMap<>();
        this.serverStats = new ServerStats();
        
        System.out.println("📊 RMI Statistics Service initialized");
    }
    
    @Override
    public List<Player> getGlobalLeaderboard() throws RemoteException {
        System.out.println("📊 RMI: Fetching global leaderboard");
        
        List<Player> leaderboard = playerStats.values().stream()
            .map(stats -> {
                Player p = new Player(stats.getPlayerId(), stats.getUsername());
                p.addScore(stats.getTotalScore());
                return p;
            })
            .sorted((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()))
            .limit(100)
            .collect(Collectors.toList());
        
        return leaderboard;
    }
    
    @Override
    public List<Player> getSessionLeaderboard(String sessionId) throws RemoteException {
        // Implementation depends on tracking session-specific scores
        return getGlobalLeaderboard(); // Simplified
    }
    
    @Override
    public int getPlayerRank(String playerId) throws RemoteException {
        List<Player> leaderboard = getGlobalLeaderboard();
        
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getPlayerId().equals(playerId)) {
                return i + 1;
            }
        }
        
        return -1; // Not ranked
    }
    
    @Override
    public PlayerStats getPlayerStats(String playerId) throws RemoteException {
        PlayerStats stats = playerStats.get(playerId);
        
        if (stats == null) {
            System.out.println("📊 Creating new stats for player: " + playerId);
            stats = new PlayerStats(playerId, "Player_" + playerId.substring(0, 8));
            playerStats.put(playerId, stats);
        }
        
        return stats;
    }
    
    @Override
    public void updatePlayerScore(String playerId, int points) throws RemoteException {
        PlayerStats stats = getPlayerStats(playerId);
        stats.addScore(points);
        stats.updateLastSeen();
        
        serverStats.incrementFlagsCaptured();
        
        System.out.println("📊 Updated score for " + playerId + ": +" + points + 
                          " (Total: " + stats.getTotalScore() + ")");
    }
    
    @Override
    public void recordFlagCapture(String playerId, String challengeId, long timeMillis) 
            throws RemoteException {
        
        PlayerStats stats = getPlayerStats(playerId);
        stats.recordSolve(challengeId, timeMillis);
        
        // Update challenge stats
        ChallengeStats chStats = challengeStats.get(challengeId);
        if (chStats == null) {
            chStats = new ChallengeStats(challengeId, "Challenge " + challengeId);
            challengeStats.put(challengeId, chStats);
        }
        chStats.recordAttempt(true, timeMillis, playerId);
        
        System.out.println("🚩 Flag capture recorded: " + playerId + " -> " + challengeId + 
                          " (" + timeMillis + "ms)");
    }
    
    @Override
    public ChallengeStats getChallengeStats(String challengeId) throws RemoteException {
        return challengeStats.getOrDefault(challengeId, 
            new ChallengeStats(challengeId, "Unknown"));
    }
    
    @Override
    public List<Challenge> getMostSolvedChallenges() throws RemoteException {
        // Return top 10 most solved challenges
        return challengeStats.values().stream()
            .sorted((c1, c2) -> Integer.compare(
                c2.getSuccessfulSolves(), c1.getSuccessfulSolves()))
            .limit(10)
            .map(cs -> new Challenge(cs.getChallengeId(), cs.getTitle(), 
                "", "mixed", 100, ""))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Challenge> getLeastSolvedChallenges() throws RemoteException {
        // Return hardest challenges (least solved)
        return challengeStats.values().stream()
            .filter(cs -> cs.getTotalAttempts() > 0)
            .sorted(Comparator.comparingInt(ChallengeStats::getSuccessfulSolves))
            .limit(10)
            .map(cs -> new Challenge(cs.getChallengeId(), cs.getTitle(), 
                "", "mixed", 200, ""))
            .collect(Collectors.toList());
    }
    
    @Override
    public ServerStats getServerStats() throws RemoteException {
        serverStats.setTotalPlayers(playerStats.size());
        serverStats.setActivePlayers((int) playerStats.values().stream()
            .filter(ps -> System.currentTimeMillis() - ps.getLastSeen().getTime() < 300000)
            .count());
        serverStats.setTotalChallenges(challengeStats.size());
        
        return serverStats;
    }
    
    @Override
    public List<String> checkAchievements(String playerId) throws RemoteException {
        List<String> achievements = new ArrayList<>();
        PlayerStats stats = getPlayerStats(playerId);
        
        // First Blood: First to solve a challenge
        if (stats.getChallengesSolved() > 0) {
            achievements.add("First Blood - Solved your first challenge!");
        }
        
        // Speedrunner: Solve a challenge in under 1 minute
        if (stats.getSolvedChallenges().stream()
                .anyMatch(id -> stats.getSolveTimeMap().get(id) < SPEEDRUNNER_TIME)) {
            achievements.add("Speedrunner - Solved a challenge in under 1 minute!");
        }
        
        // Veteran: Solve 10+ challenges
        if (stats.getChallengesSolved() >= VETERAN_CHALLENGES) {
            achievements.add("Veteran - Solved 10+ challenges!");
        }
        
        // Point milestones
        int score = stats.getTotalScore();
        if (score >= 1000) achievements.add("Rising Star - 1000+ points!");
        if (score >= 5000) achievements.add("CTF Master - 5000+ points!");
        if (score >= 10000) achievements.add("Legend - 10000+ points!");
        
        return achievements;
    }
}