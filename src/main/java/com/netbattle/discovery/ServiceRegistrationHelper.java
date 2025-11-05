package com.netbattle.discovery;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class ServiceRegistrationHelper {
    private static final String REGISTRY_URL = "http://localhost:8084/registry";
    private final String serviceId;
    private final String serviceName;
    private final int port;
    private final String protocol;
    private ScheduledExecutorService heartbeatScheduler;
    
    public ServiceRegistrationHelper(String serviceId, String serviceName, int port, String protocol) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.port = port;
        this.protocol = protocol;
    }
    
    public boolean register() {
        try {
            String json = String.format(
                "{\"serviceId\":\"%s\",\"serviceName\":\"%s\",\"host\":\"localhost\",\"port\":%d,\"protocol\":\"%s\"}",
                serviceId, serviceName, port, protocol
            );
            
            String response = sendRequest(REGISTRY_URL + "/register", "POST", json);
            System.out.println("📝 Registration response: " + response);
            
            startHeartbeat();
            return true;
        } catch (Exception e) {
            System.err.println("Failed to register with service discovery: " + e.getMessage());
            return false;
        }
    }
    
    public void deregister() {
        try {
            stopHeartbeat();
            
            String json = String.format("{\"serviceId\":\"%s\"}", serviceId);
            sendRequest(REGISTRY_URL + "/deregister", "POST", json);
            
            System.out.println("📝 Service deregistered from discovery");
        } catch (Exception e) {
            System.err.println("Failed to deregister: " + e.getMessage());
        }
    }
    
    private void startHeartbeat() {
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                String json = String.format(
                    "{\"serviceId\":\"%s\",\"activeConnections\":0}",
                    serviceId
                );
                sendRequest(REGISTRY_URL + "/heartbeat", "POST", json);
            } catch (Exception e) {
            }
        }, 5, 10, TimeUnit.SECONDS);
    }
    
    private void stopHeartbeat() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
    }
    
    private String sendRequest(String urlString, String method, String jsonData) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                return response.toString();
            }
        } else {
            throw new IOException("HTTP error code: " + responseCode);
        }
    }
}

