package com.cruise.recommender.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Admin Web Controller for serving admin maintenance pages
 * Security is handled by SecurityConfig - these endpoints require ADMIN role
 * 
 * Note: Individual entity pages are handled by the maintenance page with tabs.
 * API endpoints are in AdminController with @RestController annotation.
 */
@Controller
public class AdminWebController {
    
    @GetMapping("/admin")
    public String adminDashboard() {
        return "admin/dashboard";
    }
    
    @GetMapping("/admin/maintenance")
    public String maintenance() {
        return "admin/maintenance";
    }
    
    // Removed individual entity page mappings to avoid conflicts with AdminController API endpoints
    // All CRUD operations are handled through the maintenance page tabs and AdminController REST APIs
}

