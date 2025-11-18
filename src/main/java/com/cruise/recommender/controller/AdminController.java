package com.cruise.recommender.controller;

import com.cruise.recommender.entity.*;
import com.cruise.recommender.repository.*;
import com.cruise.recommender.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Admin Controller for maintenance operations
 * Provides CRUD endpoints for ports, cruise ships, meal venues, and restaurants
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    private final PortRepository portRepository;
    private final CruiseShipRepository cruiseShipRepository;
    private final MealVenueRepository mealVenueRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    
    // ========== Ports Management ==========
    
    @GetMapping("/ports")
    public ResponseEntity<Page<Port>> getAllPorts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        
        Pageable pageable;
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort sort = sortDir.equalsIgnoreCase("DESC") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            pageable = PageRequest.of(page, size, sort);
        } else {
            pageable = PageRequest.of(page, size);
        }
        
        Page<Port> ports;
        if (search != null && !search.isEmpty()) {
            // Simple search - you can enhance this with more sophisticated queries
            ports = portRepository.findAll(pageable);
            // Filter in memory for now (can be optimized with JPA Specifications)
            List<Port> filtered = ports.getContent().stream()
                    .filter(p -> p.getName().toLowerCase().contains(search.toLowerCase()) ||
                                p.getPortCode().toLowerCase().contains(search.toLowerCase()) ||
                                p.getCity().toLowerCase().contains(search.toLowerCase()) ||
                                p.getCountry().toLowerCase().contains(search.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
            ports = new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        } else {
            ports = portRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(ports);
    }
    
    @GetMapping("/ports/{id}")
    public ResponseEntity<Port> getPort(@PathVariable Long id) {
        return portRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/ports")
    public ResponseEntity<Port> createPort(@RequestBody Port port) {
        try {
            Port saved = portRepository.save(port);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Error creating port: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PutMapping("/ports/{id}")
    public ResponseEntity<Port> updatePort(@PathVariable Long id, @RequestBody Port port) {
        return portRepository.findById(id)
                .map(existing -> {
                    port.setId(id);
                    Port updated = portRepository.save(port);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/ports/{id}")
    public ResponseEntity<Void> deletePort(@PathVariable Long id) {
        if (portRepository.existsById(id)) {
            portRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    // ========== Cruise Ships Management ==========
    
    @GetMapping("/ships")
    public ResponseEntity<Page<CruiseShip>> getAllShips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        
        Pageable pageable;
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort sort = sortDir.equalsIgnoreCase("DESC") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            pageable = PageRequest.of(page, size, sort);
        } else {
            pageable = PageRequest.of(page, size);
        }
        
        Page<CruiseShip> ships;
        if (search != null && !search.isEmpty()) {
            ships = cruiseShipRepository.findAll(pageable);
            List<CruiseShip> filtered = ships.getContent().stream()
                    .filter(s -> s.getName().toLowerCase().contains(search.toLowerCase()) ||
                                (s.getCruiseLine() != null && s.getCruiseLine().toLowerCase().contains(search.toLowerCase())) ||
                                (s.getMmsi() != null && s.getMmsi().toLowerCase().contains(search.toLowerCase())))
                    .collect(java.util.stream.Collectors.toList());
            ships = new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        } else {
            ships = cruiseShipRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(ships);
    }
    
    @GetMapping("/ships/{id}")
    public ResponseEntity<CruiseShip> getShip(@PathVariable Long id) {
        return cruiseShipRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/ships")
    public ResponseEntity<CruiseShip> createShip(@RequestBody CruiseShip ship) {
        try {
            CruiseShip saved = cruiseShipRepository.save(ship);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Error creating ship: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PutMapping("/ships/{id}")
    public ResponseEntity<CruiseShip> updateShip(@PathVariable Long id, @RequestBody CruiseShip ship) {
        return cruiseShipRepository.findById(id)
                .map(existing -> {
                    ship.setId(id);
                    CruiseShip updated = cruiseShipRepository.save(ship);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/ships/{id}")
    public ResponseEntity<Void> deleteShip(@PathVariable Long id) {
        if (cruiseShipRepository.existsById(id)) {
            cruiseShipRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    // ========== Meal Venues Management ==========
    
    @GetMapping("/meal-venues")
    public ResponseEntity<Page<MealVenue>> getAllMealVenues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        
        Pageable pageable;
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort sort = sortDir.equalsIgnoreCase("DESC") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            pageable = PageRequest.of(page, size, sort);
        } else {
            pageable = PageRequest.of(page, size);
        }
        
        Page<MealVenue> venues;
        if (search != null && !search.isEmpty()) {
            venues = mealVenueRepository.findAll(pageable);
            List<MealVenue> filtered = venues.getContent().stream()
                    .filter(v -> v.getName().toLowerCase().contains(search.toLowerCase()) ||
                                (v.getCuisineType() != null && v.getCuisineType().toLowerCase().contains(search.toLowerCase())) ||
                                (v.getPort() != null && v.getPort().getName().toLowerCase().contains(search.toLowerCase())))
                    .collect(java.util.stream.Collectors.toList());
            venues = new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        } else {
            venues = mealVenueRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(venues);
    }
    
    @GetMapping("/meal-venues/{id}")
    public ResponseEntity<MealVenue> getMealVenue(@PathVariable Long id) {
        return mealVenueRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/meal-venues/port/{portId}")
    public ResponseEntity<List<MealVenue>> getMealVenuesByPort(@PathVariable Long portId) {
        return ResponseEntity.ok(mealVenueRepository.findByPortId(portId));
    }
    
    @PostMapping("/meal-venues")
    public ResponseEntity<MealVenue> createMealVenue(@RequestBody MealVenue mealVenue) {
        try {
            // Ensure port relationship is loaded
            if (mealVenue.getPort() != null && mealVenue.getPort().getId() != null) {
                Port port = portRepository.findById(mealVenue.getPort().getId())
                        .orElseThrow(() -> new RuntimeException("Port not found"));
                mealVenue.setPort(port);
            }
            MealVenue saved = mealVenueRepository.save(mealVenue);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Error creating meal venue: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PutMapping("/meal-venues/{id}")
    public ResponseEntity<MealVenue> updateMealVenue(@PathVariable Long id, @RequestBody MealVenue mealVenue) {
        return mealVenueRepository.findById(id)
                .map(existing -> {
                    mealVenue.setId(id);
                    MealVenue updated = mealVenueRepository.save(mealVenue);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/meal-venues/{id}")
    public ResponseEntity<Void> deleteMealVenue(@PathVariable Long id) {
        if (mealVenueRepository.existsById(id)) {
            mealVenueRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    // ========== Restaurants Management ==========
    
    @GetMapping("/restaurants")
    public ResponseEntity<Page<Restaurant>> getAllRestaurants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        
        Pageable pageable;
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort sort = sortDir.equalsIgnoreCase("DESC") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            pageable = PageRequest.of(page, size, sort);
        } else {
            pageable = PageRequest.of(page, size);
        }
        
        Page<Restaurant> restaurants;
        if (search != null && !search.isEmpty()) {
            restaurants = restaurantRepository.findAll(pageable);
            List<Restaurant> filtered = restaurants.getContent().stream()
                    .filter(r -> r.getName().toLowerCase().contains(search.toLowerCase()) ||
                                (r.getCuisineType() != null && r.getCuisineType().toLowerCase().contains(search.toLowerCase())) ||
                                (r.getPort() != null && r.getPort().getName().toLowerCase().contains(search.toLowerCase())))
                    .collect(java.util.stream.Collectors.toList());
            restaurants = new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        } else {
            restaurants = restaurantRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(restaurants);
    }
    
    @GetMapping("/restaurants/{id}")
    public ResponseEntity<Restaurant> getRestaurant(@PathVariable Long id) {
        return restaurantRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/restaurants/port/{portId}")
    public ResponseEntity<List<Restaurant>> getRestaurantsByPort(@PathVariable Long portId) {
        return ResponseEntity.ok(restaurantRepository.findByPortId(portId));
    }
    
    @PostMapping("/restaurants")
    public ResponseEntity<Restaurant> createRestaurant(@RequestBody Restaurant restaurant) {
        try {
            // Ensure port relationship is loaded
            if (restaurant.getPort() != null && restaurant.getPort().getId() != null) {
                Port port = portRepository.findById(restaurant.getPort().getId())
                        .orElseThrow(() -> new RuntimeException("Port not found"));
                restaurant.setPort(port);
            }
            // Ensure category relationship is loaded
            if (restaurant.getCategory() != null && restaurant.getCategory().getId() != null) {
                Category category = categoryRepository.findById(restaurant.getCategory().getId())
                        .orElseThrow(() -> new RuntimeException("Category not found"));
                restaurant.setCategory(category);
            }
            Restaurant saved = restaurantRepository.save(restaurant);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Error creating restaurant: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PutMapping("/restaurants/{id}")
    public ResponseEntity<Restaurant> updateRestaurant(@PathVariable Long id, @RequestBody Restaurant restaurant) {
        return restaurantRepository.findById(id)
                .map(existing -> {
                    restaurant.setId(id);
                    Restaurant updated = restaurantRepository.save(restaurant);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/restaurants/{id}")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable Long id) {
        if (restaurantRepository.existsById(id)) {
            restaurantRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    // ========== Helper endpoints ==========
    
    @GetMapping("/ports/list")
    public ResponseEntity<List<Port>> getAllPortsList() {
        return ResponseEntity.ok(portRepository.findAll());
    }
    
    @GetMapping("/categories/list")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }
}

