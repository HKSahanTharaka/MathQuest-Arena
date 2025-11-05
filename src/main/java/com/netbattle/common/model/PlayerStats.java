// common/model/PlayerStats.java
package com.netbattle.common.model;

import java.io.Serializable;
import java.util.*;

public class PlayerStats implements Serializable {
    private String playerId;
    private String username;
    private int totalScore;
    private int challengesSolved;
    private int gamesPlayed;
    private long totalPlayTime;
    private List<String> solvedChallenges;
    private Map<String, Long> solveTimeMap; // challengeId -> time taken
    private Date firstSeen;
    private Date lastSeen;
    
    public PlayerStats(String playerId, String username) {
        this.playerId = playerId;
        this.username = username;
        this.totalScore = 0;
        this.challengesSolved = 0;
        this.gamesPlayed = 0;
        this.totalPlayTime = 0;
        this.solvedChallenges = new ArrayList<>();
        this.solveTimeMap = new HashMap<>();
        this.firstSeen = new Date();
        this.lastSeen = new Date();
    }
    
    public void addScore(int points) {
        this.totalScore += points;
    }
    
    public void recordSolve(String challengeId, long timeMillis) {
        if (!solvedChallenges.contains(challengeId)) {
            solvedChallenges.add(challengeId);
            challengesSolved++;
        }
        solveTimeMap.put(challengeId, timeMillis);
    }
    
    public void updateLastSeen() {
        this.lastSeen = new Date();
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public String getUsername() { return username; }
    public int getTotalScore() { return totalScore; }
    public int getChallengesSolved() { return challengesSolved; }
    public int getGamesPlayed() { return gamesPlayed; }
    public long getTotalPlayTime() { return totalPlayTime; }
    public List<String> getSolvedChallenges() { return solvedChallenges; }
    public double getAverageSolveTime() {
        if (solveTimeMap.isEmpty()) return 0;
        return solveTimeMap.values().stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
    }
    public Map<String, Long> getSolveTimeMap() { return solveTimeMap; }
    public Date getFirstSeen() { return firstSeen; }
    public Date getLastSeen() { return lastSeen; }
    public void setGamesPlayed(int games) { this.gamesPlayed = games; }
    public void addPlayTime(long millis) { this.totalPlayTime += millis; }
}