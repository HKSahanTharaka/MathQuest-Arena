package com.netbattle.discovery;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class ServiceDiscoveryServer {
    private static final int PORT = 8084;
    private HttpServer server;
    private ServiceRegistry registry;
    
    public ServiceDiscoveryServer() {
        this.registry = ServiceRegistry.getInstance();
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(5));
        
        server.createContext("/registry/register", this::handleRegister);
        server.createContext("/registry/deregister", this::handleDeregister);
        server.createContext("/registry/heartbeat", this::handleHeartbeat);
        server.createContext("/registry/services", this::handleGetServices);
        server.createContext("/registry/discover", this::handleDiscover);
        server.createContext("/registry/stats", this::handleStats);
        
        server.start();
        System.out.println("🔍 Service Discovery/Registry Server started on port " + PORT);
        System.out.println("   📋 Endpoints:");
        System.out.println("      POST   /registry/register   - Register a service");
        System.out.println("      POST   /registry/deregister - Deregister a service");
        System.out.println("      POST   /registry/heartbeat  - Send heartbeat");
        System.out.println("      GET    /registry/services   - List all services");
        System.out.println("      GET    /registry/discover   - Discover service by name");
        System.out.println("      GET    /registry/stats      - Registry statistics");
    }
    
    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        addCorsHeaders(exchange);
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            String serviceId = data.get("serviceId");
            String serviceName = data.get("serviceName");
            String host = data.getOrDefault("host", "localhost");
            int port = Integer.parseInt(data.get("port"));
            String protocol = data.getOrDefault("protocol", "http");
            
            ServiceInfo service = new ServiceInfo(serviceId, serviceName, host, port, protocol);
            boolean registered = registry.registerService(service);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", registered);
            response.put("message", registered ? "Service registered successfully" : "Service already registered");
            response.put("serviceId", serviceId);
            
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            sendError(exchange, 400, "Invalid request data");
        }
    }
    
    private void handleDeregister(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        addCorsHeaders(exchange);
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            String serviceId = data.get("serviceId");
            
            boolean deregistered = registry.deregisterService(serviceId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", deregistered);
            response.put("message", deregistered ? "Service deregistered" : "Service not found");
            
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid request data");
        }
    }
    
    private void handleHeartbeat(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        addCorsHeaders(exchange);
        
        try {
            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);
            
            String serviceId = data.get("serviceId");
            int activeConnections = Integer.parseInt(data.getOrDefault("activeConnections", "0"));
            
            registry.updateHeartbeat(serviceId, activeConnections);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Heartbeat received");
            
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid request data");
        }
    }
    
    private void handleGetServices(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        String query = exchange.getRequestURI().getQuery();
        boolean onlyHealthy = query != null && query.contains("healthy=true");
        
        List<ServiceInfo> services = onlyHealthy ? 
                registry.getHealthyServices() : 
                registry.getAllServices();
        
        List<Map<String, Object>> serviceList = new ArrayList<>();
        for (ServiceInfo service : services) {
            Map<String, Object> serviceData = new HashMap<>();
            serviceData.put("serviceId", service.getServiceId());
            serviceData.put("serviceName", service.getServiceName());
            serviceData.put("host", service.getHost());
            serviceData.put("port", service.getPort());
            serviceData.put("protocol", service.getProtocol());
            serviceData.put("status", service.getStatus());
            serviceData.put("url", service.getServiceUrl());
            serviceData.put("activeConnections", service.getActiveConnections());
            serviceData.put("uptime", System.currentTimeMillis() - service.getRegistrationTime());
            serviceList.add(serviceData);
        }
        
        sendJson(exchange, 200, serviceList);
    }
    
    private void handleDiscover(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.contains("name=")) {
            sendError(exchange, 400, "Missing 'name' parameter");
            return;
        }
        
        String serviceName = query.split("name=")[1].split("&")[0];
        serviceName = URLDecoder.decode(serviceName, StandardCharsets.UTF_8.name());
        
        ServiceInfo service = registry.discoverService(serviceName);
        
        if (service != null) {
            Map<String, Object> serviceData = new HashMap<>();
            serviceData.put("serviceId", service.getServiceId());
            serviceData.put("serviceName", service.getServiceName());
            serviceData.put("host", service.getHost());
            serviceData.put("port", service.getPort());
            serviceData.put("protocol", service.getProtocol());
            serviceData.put("url", service.getServiceUrl());
            serviceData.put("status", service.getStatus());
            
            sendJson(exchange, 200, serviceData);
        } else {
            sendError(exchange, 404, "Service not found or unhealthy");
        }
    }
    
    private void handleStats(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        
        Map<String, Object> stats = registry.getRegistryStats();
        sendJson(exchange, 200, stats);
    }
    
    private void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type");
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            try {
                exchange.sendResponseHeaders(204, -1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
    
    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) {
            json = json.substring(1, json.length() - 1);
        }
        
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }
        return map;
    }
    
    private void sendJson(HttpExchange exchange, int code, Object data) throws IOException {
        String json = convertToJson(data);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("success", false);
        sendJson(exchange, code, error);
    }
    
    private String convertToJson(Object obj) {
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(valueToJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(convertToJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return obj.toString();
    }
    
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof List) {
            return convertToJson(value);
        } else if (value instanceof Map) {
            return convertToJson(value);
        }
        return "\"" + value.toString() + "\"";
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        registry.shutdown();
    }
    
    public static void main(String[] args) {
        ServiceDiscoveryServer server = new ServiceDiscoveryServer();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down Service Discovery server...");
            server.stop();
        }));
        
        try {
            server.start();
            
            ServiceRegistry registry = ServiceRegistry.getInstance();
            registry.registerService(new ServiceInfo(
                "registry-1", "Service Registry", "localhost", 8084, "http"
            ));
            
        } catch (Exception e) {
            System.err.println("Failed to start Service Discovery server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

