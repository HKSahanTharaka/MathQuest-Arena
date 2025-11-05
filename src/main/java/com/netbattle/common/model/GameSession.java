// common/model/GameSession.java
package com.netbattle.common.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameSession {
    private String sessionId;
    private String sessionName;
    private Map<String, Player> players;
    private List<Challenge> challenges;
    private Map<String, Set<String>> solvedChallenges; // playerId -> challengeIds
    private long startTime;
    private long endTime;
    private int maxPlayers;
    
    public GameSession(String sessionId, String sessionName, int maxPlayers) {
        this.sessionId = sessionId;
        this.sessionName = sessionName;
        this.maxPlayers = maxPlayers;
        this.players = new ConcurrentHashMap<>();
        this.challenges = new ArrayList<>();
        this.solvedChallenges = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
    }
    
    public boolean addPlayer(Player player) {
        if (players.size() >= maxPlayers) {
            return false;
        }
        players.put(player.getPlayerId(), player);
        solvedChallenges.put(player.getPlayerId(), ConcurrentHashMap.newKeySet());
        return true;
    }
    
    public void removePlayer(String playerId) {
        players.remove(playerId);
    }
    
    public boolean submitFlag(String playerId, String challengeId, String flag) {
        Challenge challenge = challenges.stream()
            .filter(c -> c.getChallengeId().equals(challengeId))
            .findFirst()
            .orElse(null);
            
        if (challenge == null) return false;
        
        // Check if already solved
        if (solvedChallenges.get(playerId).contains(challengeId)) {
            return false;
        }
        
        if (challenge.checkFlag(flag)) {
            solvedChallenges.get(playerId).add(challengeId);
            Player player = players.get(playerId);
            player.addScore(challenge.getPoints());
            challenge.incrementSolveCount();
            return true;
        }
        
        return false;
    }
    
    public List<Player> getLeaderboard() {
        List<Player> leaderboard = new ArrayList<>(players.values());
        leaderboard.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        return leaderboard;
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public String getSessionName() { return sessionName; }
    public Map<String, Player> getPlayers() { return players; }
    public List<Challenge> getChallenges() { return challenges; }
    public void addChallenge(Challenge challenge) { challenges.add(challenge); }
}