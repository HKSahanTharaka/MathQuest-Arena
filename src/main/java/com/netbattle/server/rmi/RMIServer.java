// server/rmi/RMIServer.java
package com.netbattle.server.rmi;

import java.rmi.registry.*;
import java.rmi.RemoteException;

public class RMIServer {
    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "NetBattleStats";
    
    public static void main(String[] args) {
        try {
            // Create RMI registry
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            
            // Create service instance
            StatisticsService statsService = new StatisticsServiceImpl();
            
            // Bind service to registry
            registry.rebind(SERVICE_NAME, statsService);
            
            System.out.println("📊 RMI Statistics Server started");
            System.out.println("   Port: " + RMI_PORT);
            System.out.println("   Service: " + SERVICE_NAME);
            System.out.println("   URL: rmi://localhost:" + RMI_PORT + "/" + SERVICE_NAME);
            System.out.println("\n✅ Ready to accept remote calls");
            
            // Keep server running
            Thread.currentThread().join();
            
        } catch (RemoteException e) {
            System.err.println("❌ RMI Server error: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("\n📊 RMI Server shutting down...");
        }
    }
}