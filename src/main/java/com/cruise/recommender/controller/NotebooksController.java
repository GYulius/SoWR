package com.cruise.recommender.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller to serve JSON recommendation files from notebooks folder
 */
@RestController
@RequestMapping("/notebooks")
@Slf4j
public class NotebooksController {
    
    /**
     * Serve JSON recommendation files from notebooks folder
     * Example: GET /api/v1/notebooks/recommendations_2_NZWLG.json
     */
    @GetMapping("/{filename:.+\\.json}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getRecommendationJson(@PathVariable String filename) {
        try {
            // Get notebooks directory (relative to project root)
            String notebooksDir = "notebooks";
            Path filePath = Paths.get(notebooksDir, filename);
            
            log.info("Attempting to serve JSON file: {}", filePath.toAbsolutePath());
            
            File file = filePath.toFile();
            if (!file.exists() || !file.isFile()) {
                log.warn("JSON file not found: {}", filePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            
            // Check if file is within notebooks directory (security check)
            Path notebooksPath = Paths.get(notebooksDir).toAbsolutePath().normalize();
            Path requestedPath = filePath.toAbsolutePath().normalize();
            if (!requestedPath.startsWith(notebooksPath)) {
                log.warn("Security check failed: File path outside notebooks directory");
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_JSON_VALUE;
            }
            
            log.info("Serving JSON file: {} ({} bytes)", filename, file.length());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("Error serving JSON file: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
