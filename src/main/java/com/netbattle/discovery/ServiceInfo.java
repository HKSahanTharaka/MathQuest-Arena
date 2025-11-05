package com.netbattle.discovery;

import java.io.Serializable;

public class ServiceInfo implements Serializable {
    private String serviceId;
    private String serviceName;
    private String host;
    private int port;
    private String status;
    private long registrationTime;
    private long lastHeartbeat;
    private int activeConnections;
    private String protocol;
    
    public ServiceInfo(String serviceId, String serviceName, String host, int port, String protocol) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.status = "UP";
        this.registrationTime = System.currentTimeMillis();
        this.lastHeartbeat = System.currentTimeMillis();
        this.activeConnections = 0;
    }
    
    public boolean isHealthy() {
        long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeat;
        return timeSinceLastHeartbeat < 30000;
    }
    
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
        this.status = "UP";
    }
    
    public void markDown() {
        this.status = "DOWN";
    }
    
    public String getServiceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getStatus() { return status; }
    public long getRegistrationTime() { return registrationTime; }
    public long getLastHeartbeat() { return lastHeartbeat; }
    public int getActiveConnections() { return activeConnections; }
    public String getProtocol() { return protocol; }
    
    public void setActiveConnections(int count) { 
        this.activeConnections = count; 
    }
    
    public String getServiceUrl() {
        return protocol + "://" + host + ":" + port;
    }
}

