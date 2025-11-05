// server/rmi/StatisticsService.java
package com.netbattle.server.rmi;

import com.netbattle.common.model.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface StatisticsService extends Remote {
    
    // Leaderboard operations
    List<Player> getGlobalLeaderboard() throws RemoteException;
    List<Player> getSessionLeaderboard(String sessionId) throws RemoteException;
    int getPlayerRank(String playerId) throws RemoteException;
    
    // Player statistics
    PlayerStats getPlayerStats(String playerId) throws RemoteException;
    void updatePlayerScore(String playerId, int points) throws RemoteException;
    void recordFlagCapture(String playerId, String challengeId, long time) throws RemoteException;
    
    // Challenge statistics
    ChallengeStats getChallengeStats(String challengeId) throws RemoteException;
    List<Challenge> getMostSolvedChallenges() throws RemoteException;
    List<Challenge> getLeastSolvedChallenges() throws RemoteException;
    
    // Server statistics
    ServerStats getServerStats() throws RemoteException;
    
    // Achievement system
    List<String> checkAchievements(String playerId) throws RemoteException;
}