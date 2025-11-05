// common/model/ServerStats.java
package com.netbattle.common.model;

import java.io.Serializable;

public class ServerStats implements Serializable {
    private int totalPlayers;
    private int activePlayers;
    private int totalChallenges;
    private int totalFlagsCaptured;
    private long serverUptime;
    private int peakConcurrentPlayers;
    
    public ServerStats() {
        this.serverUptime = System.currentTimeMillis();
    }
    
    public long getUptime() {
        return System.currentTimeMillis() - serverUptime;
    }
    
    // Getters and setters
    public int getTotalPlayers() { return totalPlayers; }
    public void setTotalPlayers(int totalPlayers) { this.totalPlayers = totalPlayers; }
    public int getActivePlayers() { return activePlayers; }
    public void setActivePlayers(int activePlayers) { this.activePlayers = activePlayers; }
    public int getTotalChallenges() { return totalChallenges; }
    public void setTotalChallenges(int totalChallenges) { this.totalChallenges = totalChallenges; }
    public int getTotalFlagsCaptured() { return totalFlagsCaptured; }
    public void incrementFlagsCaptured() { this.totalFlagsCaptured++; }
    public int getPeakConcurrentPlayers() { return peakConcurrentPlayers; }
    public void updatePeak(int current) {
        if (current > peakConcurrentPlayers) {
            peakConcurrentPlayers = current;
        }
    }
}