package com.cruise.recommender.controller;

import com.cruise.recommender.repository.elasticsearch.AisDataDocument;
import com.cruise.recommender.service.AisDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Controller for map visualization using Leaflet
 */
@Controller
@RequestMapping("/map")
@RequiredArgsConstructor
@Slf4j
public class MapController {
    
    private final AisDataService aisDataService;
    
    /**
     * Display interactive map with ship positions
     */
    @GetMapping("/ships")
    public String showShipMap(Model model) {
        log.info("Displaying ship map");
        
        // Get recent AIS data (last 30 minutes)
        List<AisDataDocument> recentShips = aisDataService.getRecentAisData(30);
        
        model.addAttribute("ships", recentShips);
        model.addAttribute("mapCenterLat", 25.7617); // Miami default
        model.addAttribute("mapCenterLng", -80.1918);
        model.addAttribute("mapZoom", 10);
        
        return "map/ships";
    }
    
    /**
     * Display map with ships near a specific port
     */
    @GetMapping("/port")
    public String showPortMap(Model model, Double latitude, Double longitude, Double radius) {
        log.info("Displaying port map for lat: {}, lng: {}, radius: {}", latitude, longitude, radius);
        
        if (latitude == null) latitude = 25.7617; // Miami default
        if (longitude == null) longitude = -80.1918;
        if (radius == null) radius = 50.0; // 50 nautical miles
        
        // Calculate bounding box
        double latRange = radius / 60.0;
        double lngRange = radius / (60.0 * Math.cos(Math.toRadians(latitude)));
        
        List<AisDataDocument> ships = aisDataService.findAisDataInArea(
            latitude - latRange,
            latitude + latRange,
            longitude - lngRange,
            longitude + lngRange,
            java.time.LocalDateTime.now().minusHours(1)
        );
        
        model.addAttribute("ships", ships);
        model.addAttribute("mapCenterLat", latitude);
        model.addAttribute("mapCenterLng", longitude);
        model.addAttribute("mapZoom", 12);
        model.addAttribute("portLat", latitude);
        model.addAttribute("portLng", longitude);
        model.addAttribute("radius", radius);
        
        return "map/port";
    }
}

