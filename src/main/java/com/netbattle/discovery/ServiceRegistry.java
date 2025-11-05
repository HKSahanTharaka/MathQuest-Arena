package com.netbattle.discovery;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ServiceRegistry {
    private static ServiceRegistry instance;
    private final Map<String, ServiceInfo> services;
    private final ScheduledExecutorService healthChecker;
    
    private ServiceRegistry() {
        this.services = new ConcurrentHashMap<>();
        this.healthChecker = Executors.newScheduledThreadPool(1);
        startHealthCheck();
    }
    
    public static synchronized ServiceRegistry getInstance() {
        if (instance == null) {
            instance = new ServiceRegistry();
        }
        return instance;
    }
    
    public synchronized boolean registerService(ServiceInfo service) {
        if (services.containsKey(service.getServiceId())) {
            System.out.println("⚠️  Service already registered: " + service.getServiceName());
            return false;
        }
        
        services.put(service.getServiceId(), service);
        System.out.println("✅ Service registered: " + service.getServiceName() + 
                          " at " + service.getServiceUrl());
        return true;
    }
    
    public synchronized boolean deregisterService(String serviceId) {
        ServiceInfo removed = services.remove(serviceId);
        if (removed != null) {
            System.out.println("❌ Service deregistered: " + removed.getServiceName());
            return true;
        }
        return false;
    }
    
    public synchronized void updateHeartbeat(String serviceId, int activeConnections) {
        ServiceInfo service = services.get(serviceId);
        if (service != null) {
            service.updateHeartbeat();
            service.setActiveConnections(activeConnections);
        }
    }
    
    public List<ServiceInfo> getAllServices() {
        return new ArrayList<>(services.values());
    }
    
    public List<ServiceInfo> getHealthyServices() {
        return services.values().stream()
                .filter(ServiceInfo::isHealthy)
                .collect(Collectors.toList());
    }
    
    public ServiceInfo getService(String serviceId) {
        return services.get(serviceId);
    }
    
    public List<ServiceInfo> getServicesByName(String serviceName) {
        return services.values().stream()
                .filter(s -> s.getServiceName().equalsIgnoreCase(serviceName))
                .collect(Collectors.toList());
    }
    
    public ServiceInfo discoverService(String serviceName) {
        return services.values().stream()
                .filter(s -> s.getServiceName().equalsIgnoreCase(serviceName))
                .filter(ServiceInfo::isHealthy)
                .findFirst()
                .orElse(null);
    }
    
    private void startHealthCheck() {
        healthChecker.scheduleAtFixedRate(() -> {
            List<String> unhealthyServices = new ArrayList<>();
            
            for (ServiceInfo service : services.values()) {
                if (!service.isHealthy()) {
                    service.markDown();
                    unhealthyServices.add(service.getServiceName());
                }
            }
            
            if (!unhealthyServices.isEmpty()) {
                System.out.println("⚠️  Unhealthy services detected: " + 
                                 String.join(", ", unhealthyServices));
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
    
    public Map<String, Object> getRegistryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalServices", services.size());
        stats.put("healthyServices", getHealthyServices().size());
        stats.put("unhealthyServices", services.size() - getHealthyServices().size());
        stats.put("registryUptime", System.currentTimeMillis());
        return stats;
    }
    
    public void shutdown() {
        healthChecker.shutdown();
    }
}

