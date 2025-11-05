// common/model/Challenge.java
package com.netbattle.common.model;

import java.io.Serializable;

public class Challenge implements Serializable {
    private String challengeId;
    private String title;
    private String description;
    private String category;  // crypto, web, forensics, etc.
    private int points;
    private String flag;
    private int solveCount;
    
    public Challenge(String challengeId, String title, String description, 
                     String category, int points, String flag) {
        this.challengeId = challengeId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.points = points;
        this.flag = flag;
        this.solveCount = 0;
    }
    
    public boolean checkFlag(String submittedFlag) {
        return this.flag.equals(submittedFlag);
    }
    
    public void incrementSolveCount() {
        this.solveCount++;
    }
    
    // Getters
    public String getChallengeId() { return challengeId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public int getPoints() { return points; }
    public int getSolveCount() { return solveCount; }
}