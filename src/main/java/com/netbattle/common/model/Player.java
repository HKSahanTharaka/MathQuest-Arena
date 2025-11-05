// common/model/Player.java
package com.netbattle.common.model;

import java.io.Serializable;

public class Player implements Serializable {
    private String playerId;
    private String username;
    private int score;
    private String teamId;
    private Position position;
    private boolean isOnline;
    
    public Player(String playerId, String username) {
        this.playerId = playerId;
        this.username = username;
        this.score = 0;
        this.isOnline = true;
        this.position = new Position(0, 0);
    }
    
    public void addScore(int points) {
        this.score += points;
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public String getUsername() { return username; }
    public int getScore() { return score; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
}