// common/model/ChallengeStats.java
package com.netbattle.common.model;

import java.io.Serializable;

public class ChallengeStats implements Serializable {
    private String challengeId;
    private String title;
    private int totalAttempts;
    private int successfulSolves;
    private double averageSolveTime;
    private String fastestSolver;
    private long fastestTime;
    
    public ChallengeStats(String challengeId, String title) {
        this.challengeId = challengeId;
        this.title = title;
        this.totalAttempts = 0;
        this.successfulSolves = 0;
        this.averageSolveTime = 0;
    }
    
    public void recordAttempt(boolean success, long solveTime, String player) {
        totalAttempts++;
        if (success) {
            successfulSolves++;
            
            // Update fastest solver
            if (fastestSolver == null || solveTime < fastestTime) {
                fastestSolver = player;
                fastestTime = solveTime;
            }
        }
    }
    
    public double getSuccessRate() {
        return totalAttempts > 0 ? (double) successfulSolves / totalAttempts * 100 : 0;
    }
    
    // Getters
    public String getChallengeId() { return challengeId; }
    public String getTitle() { return title; }
    public int getTotalAttempts() { return totalAttempts; }
    public int getSuccessfulSolves() { return successfulSolves; }
    public String getFastestSolver() { return fastestSolver; }
    public long getFastestTime() { return fastestTime; }
}