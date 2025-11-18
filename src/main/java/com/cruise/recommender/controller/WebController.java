package com.cruise.recommender.controller;

import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.PortRepository;
import com.cruise.recommender.service.PortDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Web Controller for serving the main application page
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {
    
    private final PortRepository portRepository;
    private final PortDataService portDataService;
    
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    @GetMapping("/")
    public String index(Model model) {
        log.info("Serving main application page");
        
        // Load ALL ports from JSON for map display (filter out ports without coordinates)
        List<PortDataService.PortData> allPorts = portDataService.getAllPorts();
        log.info("Total ports loaded from JSON: {}", allPorts.size());
        
        List<PortDataService.PortData> portsWithCoordinates = allPorts.stream()
            .filter(p -> {
                // Check if port has coordinates (either in JSON or can be parsed)
                if (p.getLatitude() != null && !p.getLatitude().isEmpty() && 
                    p.getLongitude() != null && !p.getLongitude().isEmpty()) {
                    double lat = p.getLatitudeDecimal();
                    double lng = p.getLongitudeDecimal();
                    // Filter out invalid coordinates (0,0) - but allow if one is 0 and other is not
                    // Also filter out obviously invalid coordinates (outside valid ranges)
                    boolean validLat = lat >= -90 && lat <= 90 && (lat != 0.0 || lng != 0.0);
                    boolean validLng = lng >= -180 && lng <= 180 && (lat != 0.0 || lng != 0.0);
                    return validLat && validLng;
                }
                return false;
            })
            .collect(java.util.stream.Collectors.toList());
        
        log.info("Found {} ports with valid coordinates out of {} total ports", 
                portsWithCoordinates.size(), allPorts.size());
        
        if (portsWithCoordinates.isEmpty()) {
            log.warn("No ports with valid coordinates found! Check JSON file and coordinate parsing.");
        }
        
        model.addAttribute("featuredPorts", portsWithCoordinates);
        
        // Also load ports from database for card display
        List<Port> ports = portRepository.findAll();
        model.addAttribute("ports", ports);
        
        return "index";
    }
    
    /**
     * API endpoint to get all ports for dropdown (from database)
     * Accessible at /api/v1/ports (context path + mapping)
     */
    @GetMapping("/ports")
    @ResponseBody
    @CrossOrigin(origins = "*")
    public List<Port> getAllPorts() {
        log.info("=== GET /ports endpoint called ===");
        log.info("Fetching all ports from database");
        try {
            List<Port> ports = portRepository.findAll();
            log.info("Found {} ports in database", ports.size());
            if (!ports.isEmpty()) {
                log.info("First port sample: id={}, name={}, portCode={}, lat={}, lng={}", 
                        ports.get(0).getId(), ports.get(0).getName(), 
                        ports.get(0).getPortCode(), ports.get(0).getLatitude(), 
                        ports.get(0).getLongitude());
            } else {
                log.warn("No ports found in database! Check if data was inserted.");
            }
            log.info("=== Returning {} ports ===", ports.size());
            return ports;
        } catch (Exception e) {
            log.error("Error fetching ports from database", e);
            throw e;
        }
    }
    
    /**
     * API endpoint to get a single port by ID (for Foodie section)
     * Accessible at /api/v1/ports/{id} (context path + mapping)
     */
    @GetMapping("/ports/{id}")
    @ResponseBody
    @CrossOrigin(origins = "*")
    public Port getPortById(@PathVariable Long id) {
        log.info("Fetching port with id: {}", id);
        Port port = portRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Port not found with id: " + id));
        log.debug("Found port: id={}, name={}, portCode={}", port.getId(), port.getName(), port.getPortCode());
        return port;
    }
    
    /**
     * API endpoint to get featured ports (from JSON for backward compatibility)
     * Accessible at /api/v1/ports/featured (context path + mapping)
     */
    @GetMapping("/ports/featured")
    @ResponseBody
    public List<PortDataService.PortData> getFeaturedPorts() {
        return portDataService.getFeaturedPorts();
    }
    
    @GetMapping("/health")
    public String health() {
        return "redirect:/actuator/health";
    }
    
    @GetMapping("/docs")
    public String docs() {
        return "redirect:/swagger-ui.html";
    }
}
