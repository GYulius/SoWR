package com.cruise.recommender.controller;

import com.cruise.recommender.entity.CruiseShip;
import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.CruiseShipRepository;
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
    private final CruiseShipRepository cruiseShipRepository;
    
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    @GetMapping("/")
    public String index(Model model) {
        log.info("Serving main application page");
        
        // Load ports from database (ports are now persisted in database)
        List<Port> ports = portRepository.findAll();
        log.info("Total ports loaded from database: {}", ports.size());
        
        // Filter ports with valid coordinates for map display
        List<Port> portsWithCoordinates = ports.stream()
            .filter(p -> {
                // Check if port has valid coordinates
                if (p.getLatitude() != null && p.getLongitude() != null) {
                    try {
                        double lat = p.getLatitude();
                        double lng = p.getLongitude();
                        // Filter out invalid coordinates (0,0) and out of range
                        boolean validLat = lat >= -90 && lat <= 90 && (lat != 0.0 || lng != 0.0);
                        boolean validLng = lng >= -180 && lng <= 180 && (lat != 0.0 || lng != 0.0);
                        return validLat && validLng;
                    } catch (Exception e) {
                        log.debug("Invalid coordinates for port {}: {}", p.getId(), e.getMessage());
                        return false;
                    }
                }
                return false;
            })
            .collect(java.util.stream.Collectors.toList());
        
        log.info("Found {} ports with valid coordinates out of {} total ports", 
                portsWithCoordinates.size(), ports.size());
        
        if (portsWithCoordinates.isEmpty()) {
            log.warn("No ports with valid coordinates found! Check database port data.");
        }
        
        // Pass ports to template (both for map and cards)
        model.addAttribute("featuredPorts", portsWithCoordinates);
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
    
    /**
     * API endpoint to get all cruise ships for dropdown (from database)
     * Accessible at /api/v1/ships (context path + mapping)
     * Public endpoint - no authentication required
     */
    @GetMapping("/ships")
    @ResponseBody
    @CrossOrigin(origins = "*")
    public List<CruiseShip> getAllShips() {
        log.info("=== GET /ships endpoint called ===");
        log.info("Fetching all cruise ships from database");
        try {
            List<CruiseShip> ships = cruiseShipRepository.findAll();
            log.info("Found {} ships in database", ships.size());
            if (!ships.isEmpty()) {
                log.info("First ship sample: id={}, name={}, cruiseLine={}, mmsi={}", 
                        ships.get(0).getId(), ships.get(0).getName(), 
                        ships.get(0).getCruiseLine(), ships.get(0).getMmsi());
            } else {
                log.warn("No ships found in database! Check if data was inserted.");
            }
            log.info("=== Returning {} ships ===", ships.size());
            return ships;
        } catch (Exception e) {
            log.error("Error fetching ships from database", e);
            throw e;
        }
    }
}
