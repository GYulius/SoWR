package com.cruise.recommender.controller;

import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.PortRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Web Controller for serving the main application page
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {
    
    private final PortRepository portRepository;
    
    @GetMapping("/")
    public String index(Model model) {
        log.info("Serving main application page");
        
        // Load featured ports for display
        List<Port> ports = portRepository.findAll();
        model.addAttribute("ports", ports);
        
        return "index";
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
