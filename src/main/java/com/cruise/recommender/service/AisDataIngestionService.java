package com.cruise.recommender.service;

import com.cruise.recommender.config.RabbitMQConfig;
import com.cruise.recommender.entity.CruiseShip;
import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.CruiseShipRepository;
import com.cruise.recommender.repository.PortRepository;
import com.cruise.recommender.service.AisDataService.AisDataMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@SuppressWarnings("unchecked")

/**
 * Service for ingesting AIS (Automatic Identification System) data from external sources
 * Supports multiple data sources: APIs, webhooks, and simulated data for testing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AisDataIngestionService {
    
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final VesselFinderApiClient vesselFinderApiClient;
    private final CruiseShipRepository cruiseShipRepository;
    private final PortRepository portRepository;
    
    @Value("${ais.data.source.api.url:}")
    private String aisApiUrl;
    
    @Value("${ais.data.source.api.key:}")
    private String aisApiKey;
    
    @Value("${ais.data.source.api.provider:VESSELFINDER}")
    private String aisProvider;
    
    @Value("${ais.data.simulation.enabled:false}")
    private boolean simulationEnabled;
    
    @Value("${openais.enabled:false}")
    private boolean openAisEnabled;
    
    @Value("${ais.data.ingestion.interval:30000}")
    private long ingestionInterval;
    
    private final Random random = new Random();
    
    /**
     * Scheduled task to fetch AIS data from external sources
     * Runs every 30 seconds by default
     * Also generates historical waypoints for route calculation
     */
    @Scheduled(fixedRateString = "${ais.data.ingestion.interval:30000}")
    public void ingestAisData() {
        log.debug("Starting AIS data ingestion cycle");
        
        try {
            List<AisDataMessage> aisDataList = new ArrayList<>();
            
            if (simulationEnabled) {
                // Generate simulated AIS data for testing (includes current position)
                aisDataList = generateSimulatedAisData();
                
                // Generate historical waypoints for route calculation
                // This creates a trail of positions showing ship movement towards port
                List<AisDataMessage> historicalWaypoints = generateHistoricalWaypoints();
                aisDataList.addAll(historicalWaypoints);
            } else if (aisApiUrl != null && !aisApiUrl.isEmpty() && aisApiKey != null && !aisApiKey.isEmpty()) {
                // Fetch from external API (VesselFinder, MarineTraffic, etc.) - Priority 1
                log.debug("Fetching AIS data from {} API", aisProvider);
                aisDataList = fetchFromExternalApi();
            } else if (aisApiUrl != null && !aisApiUrl.isEmpty() && aisApiKey != null && !aisApiKey.isEmpty()) {
                // Fetch from VesselFinder API (default provider) - Priority 2
                log.debug("Fetching AIS data from VesselFinder");
                aisDataList = vesselFinderApiClient.fetchAisData();
            } else {
                log.warn("No AIS data source configured. Configure VesselFinder API (url and key), enable simulation, or enable Open-AIS.");
                return;
            }
            
            // Send each AIS data message to RabbitMQ
            for (AisDataMessage message : aisDataList) {
                // Validate message before sending
                if (message == null) {
                    log.warn("Skipping null AIS data message");
                    continue;
                }
                
                String mmsi = message.getMmsi();
                if (mmsi == null || mmsi.trim().isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                    log.error("CRITICAL: Attempting to send AIS message with invalid MMSI. " +
                            "Ship: {}, Message object: {}", message.getShipName(), message);
                    continue;
                }
                
                // Log before sending to help debug
                log.debug("Sending AIS data to queue - MMSI: {}, Ship: {}", mmsi, message.getShipName());
                
                try {
                    rabbitTemplate.convertAndSend(
                        RabbitMQConfig.AIS_EXCHANGE,
                        "ais.data.raw",
                        message
                    );
                    log.debug("Successfully sent AIS data to queue for MMSI: {}", mmsi);
                } catch (Exception e) {
                    log.error("Error sending AIS data to queue for MMSI: {}", mmsi, e);
                }
            }
            
            log.info("Ingested {} AIS data records", aisDataList.size());
            
        } catch (Exception e) {
            log.error("Error during AIS data ingestion", e);
        }
    }
    
    /**
     * Fetch AIS data from external API
     * Supports various AIS data providers (MarineTraffic, VesselFinder, AISHub, etc.)
     */
    private List<AisDataMessage> fetchFromExternalApi() {
        List<AisDataMessage> aisDataList = new ArrayList<>();
        
        if (aisApiUrl == null || aisApiUrl.isEmpty()) {
            log.warn("AIS API URL is not configured. Cannot fetch real AIS data.");
            return aisDataList;
        }
        
        if (aisApiKey == null || aisApiKey.isEmpty()) {
            log.warn("AIS API key is not configured. Cannot fetch real AIS data.");
            return aisDataList;
        }
        
        try {
            String url = buildApiUrl();
            log.debug("Fetching AIS data from: {}", url.replace(aisApiKey, "***"));
            
            // Make API call (adjust based on actual API response format)
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            // Parse response and convert to AisDataMessage
            // Different providers have different response formats
            if (response != null) {
                List<Map<String, Object>> vessels = parseApiResponse(response);
                
                for (Map<String, Object> vessel : vessels) {
                    AisDataMessage message = convertApiResponseToMessage(vessel);
                    if (message != null && message.getMmsi() != null && !message.getMmsi().trim().isEmpty()) {
                        aisDataList.add(message);
                        log.debug("Fetched AIS data for MMSI: {}, Ship: {}, Source: {}", 
                                message.getMmsi(), message.getShipName(), message.getDataSource());
                    }
                }
            }
            
            log.info("Fetched {} AIS data records from {} provider", aisDataList.size(), aisProvider);
            
        } catch (Exception e) {
            log.error("Error fetching AIS data from external API: {}", e.getMessage(), e);
        }
        
        return aisDataList;
    }
    
    /**
     * Build API URL based on provider type
     */
    private String buildApiUrl() {
        String provider = aisProvider.toUpperCase();
        
        switch (provider) {
            case "MARINETRAFFIC":
                // MarineTraffic API format
                return aisApiUrl + "?api_key=" + aisApiKey + "&timespan=10&protocol=jsono";
            case "VESSELFINDER":
                // VesselFinder API format for free account DEFAULT FLEET
                // Free account includes 10 ships in DEFAULT FLEET
                // API endpoint: https://www.vesselfinder.com/api
                // Parameters: api_key (required), fleet (optional, defaults to DEFAULT)
                return aisApiUrl + "?api_key=" + aisApiKey + "&fleet=DEFAULT";
            case "AISHUB":
                // AISHub API format
                return aisApiUrl + "?key=" + aisApiKey + "&format=json";
            case "OPENAIS":
                // Open-AIS uses PG_FeatureServ API (handled separately)
                return aisApiUrl;
            default:
                // Generic format
                return aisApiUrl + "?api_key=" + aisApiKey;
        }
    }
    
    /**
     * Parse API response based on provider format
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseApiResponse(Map<String, Object> response) {
        String provider = aisProvider.toUpperCase();
        List<Map<String, Object>> vessels = new ArrayList<>();
        
        switch (provider) {
            case "MARINETRAFFIC":
                // MarineTraffic returns data in "data" array
                if (response.containsKey("data")) {
                    Object dataObj = response.get("data");
                    if (dataObj instanceof List) {
                        vessels = (List<Map<String, Object>>) dataObj;
                    }
                }
                break;
            case "VESSELFINDER":
                // VesselFinder may return data directly or in "vessels" array
                if (response.containsKey("vessels")) {
                    Object vesselsObj = response.get("vessels");
                    if (vesselsObj instanceof List) {
                        vessels = (List<Map<String, Object>>) vesselsObj;
                    }
                } else if (response.containsKey("data")) {
                    Object dataObj = response.get("data");
                    if (dataObj instanceof List) {
                        vessels = (List<Map<String, Object>>) dataObj;
                    }
                }
                break;
            case "AISHUB":
                // AISHub returns data in "positions" array
                if (response.containsKey("positions")) {
                    Object positionsObj = response.get("positions");
                    if (positionsObj instanceof List) {
                        vessels = (List<Map<String, Object>>) positionsObj;
                    }
                }
                break;
            default:
                // Try common field names
                if (response.containsKey("data")) {
                    Object dataObj = response.get("data");
                    if (dataObj instanceof List) {
                        vessels = (List<Map<String, Object>>) dataObj;
                    }
                } else if (response.containsKey("vessels")) {
                    Object vesselsObj = response.get("vessels");
                    if (vesselsObj instanceof List) {
                        vessels = (List<Map<String, Object>>) vesselsObj;
                    }
                }
        }
        
        return vessels;
    }
    
    /**
     * Convert API response to AisDataMessage
     */
    private AisDataMessage convertApiResponseToMessage(Map<String, Object> vessel) {
        try {
            // Extract and validate MMSI - it's required
            String mmsi = String.valueOf(vessel.getOrDefault("mmsi", "")).trim();
            if (mmsi.isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                // Try alternative field names
                mmsi = String.valueOf(vessel.getOrDefault("MMSI", "")).trim();
                if (mmsi.isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                    mmsi = String.valueOf(vessel.getOrDefault("vessel_id", "")).trim();
                }
            }
            
            // MMSI is required - skip if not available
            if (mmsi.isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                log.warn("Skipping vessel data: MMSI is missing or invalid. Vessel data: {}", vessel);
                return null;
            }
            
            return AisDataMessage.builder()
                .mmsi(mmsi)
                .shipName(String.valueOf(vessel.getOrDefault("ship_name", vessel.getOrDefault("name", "Unknown"))).trim())
                .latitude(getDoubleValue(vessel, "latitude"))
                .longitude(getDoubleValue(vessel, "longitude"))
                .timestamp(LocalDateTime.now())
                .speed(getDoubleValue(vessel, "speed"))
                .course(getDoubleValue(vessel, "course"))
                .heading(getIntegerValue(vessel, "heading"))
                .shipType(String.valueOf(vessel.getOrDefault("ship_type", "")).trim())
                .destination(String.valueOf(vessel.getOrDefault("destination", "")).trim())
                .eta(String.valueOf(vessel.getOrDefault("eta", "")).trim())
                .imo(String.valueOf(vessel.getOrDefault("imo", "")).trim())
                .callSign(String.valueOf(vessel.getOrDefault("callsign", vessel.getOrDefault("call_sign", ""))).trim())
                .stationRange(getDoubleValue(vessel, "station_range"))
                .signalQuality(String.valueOf(vessel.getOrDefault("signal_quality", vessel.getOrDefault("signalQuality", "GOOD"))).trim())
                .dataSource(determineDataSource(vessel))
                .build();
        } catch (Exception e) {
            log.error("Error converting API response to AIS message", e);
            return null;
        }
    }
    
    /**
     * Generate simulated AIS data for testing and development
     * Uses real ships from cruise_ships table with MMSI, IMO, and callSign
     * Generates positions based on routes to actual ports for route calculation
     */
    private List<AisDataMessage> generateSimulatedAisData() {
        List<AisDataMessage> aisDataList = new ArrayList<>();
        
        try {
            // Get all cruise ships with AIS enabled from database
            List<CruiseShip> allShips = cruiseShipRepository.findByAisEnabledTrue();
            
            // Filter to only ships with valid MMSI
            List<CruiseShip> ships = allShips.stream()
                .filter(ship -> ship.getMmsi() != null && !ship.getMmsi().trim().isEmpty())
                .collect(java.util.stream.Collectors.toList());
            
            if (ships.isEmpty()) {
                log.warn("No cruise ships with AIS enabled and valid MMSI found in database. Cannot generate simulated AIS data.");
                if (!allShips.isEmpty()) {
                    log.warn("Found {} ships with AIS enabled but missing MMSI. Please update ships with MMSI values.", 
                            allShips.size() - ships.size());
                }
                return aisDataList;
            }
            
            // Get random ports from database for route destinations
            List<Port> ports = portRepository.findAll();
            if (ports.isEmpty()) {
                log.warn("No ports found in database. Cannot generate realistic routes.");
                return aisDataList;
            }
            
            // Select 3-5 ships randomly (or all if less than 5)
            int shipCount = Math.min(3 + random.nextInt(3), ships.size());
            List<CruiseShip> selectedShips = new ArrayList<>();
            for (int i = 0; i < shipCount; i++) {
                int index = random.nextInt(ships.size());
                CruiseShip ship = ships.get(index);
                if (!selectedShips.contains(ship)) {
                    selectedShips.add(ship);
                } else if (ships.size() > selectedShips.size()) {
                    // Try another ship if already selected
                    i--;
                }
            }
            
            // Generate AIS data for each selected ship
            for (CruiseShip ship : selectedShips) {
                // Double-check MMSI is valid (should already be filtered, but safety check)
                String shipMmsi = ship.getMmsi() != null ? ship.getMmsi().trim() : null;
                if (shipMmsi == null || shipMmsi.isEmpty()) {
                    log.warn("Skipping ship {} (ID: {}): MMSI is null or empty", ship.getName(), ship.getId());
                    continue;
                }
                
                // Select a random port as destination
                Port destinationPort = ports.get(random.nextInt(ports.size()));
                
                // Get ship's current position or generate starting position
                double startLat, startLng;
                if (ship.getCurrentLatitude() != null && ship.getCurrentLongitude() != null) {
                    // Use existing position
                    startLat = ship.getCurrentLatitude();
                    startLng = ship.getCurrentLongitude();
                } else {
                    // Generate starting position (50-100 nautical miles from destination)
                    double distanceNm = 50 + random.nextDouble() * 50; // 50-100 nm
                    double bearing = random.nextDouble() * 360; // Random bearing
                    double[] startPos = calculatePosition(destinationPort.getLatitude(), 
                                                          destinationPort.getLongitude(), 
                                                          distanceNm, bearing);
                    startLat = startPos[0];
                    startLng = startPos[1];
                }
                
                // Calculate course and heading towards destination port
                double course = calculateBearing(startLat, startLng, 
                                                destinationPort.getLatitude(), 
                                                destinationPort.getLongitude());
                
                // Calculate distance to port
                double distanceNm = calculateDistance(startLat, startLng,
                                                      destinationPort.getLatitude(),
                                                      destinationPort.getLongitude());
                
                // Generate realistic speed (cruise ships typically 15-25 knots)
                double speed = 15 + random.nextDouble() * 10;
                
                // Calculate ETA based on distance and speed
                double hoursToPort = distanceNm / speed;
                LocalDateTime eta = LocalDateTime.now().plusHours((long) hoursToPort)
                                                  .plusMinutes((long) ((hoursToPort % 1) * 60));
                
                // Build AIS message with real ship data
                AisDataMessage message = AisDataMessage.builder()
                    .mmsi(shipMmsi)
                    .shipName(ship.getName())
                    .latitude(startLat)
                    .longitude(startLng)
                    .timestamp(LocalDateTime.now())
                    .speed(speed)
                    .course(course)
                    .heading((int) Math.round(course))
                    .shipType("Passenger Ship")
                    .destination(destinationPort.getName() + ", " + destinationPort.getCountry())
                    .eta(eta.toString())
                    .imo(ship.getImo() != null && !ship.getImo().trim().isEmpty() ? ship.getImo().trim() : null)
                    .callSign(ship.getCallSign() != null && !ship.getCallSign().trim().isEmpty() ? ship.getCallSign().trim() : null)
                    .stationRange(5 + random.nextDouble() * 20)
                    .signalQuality(getRandomSignalQuality())
                    .dataSource("SIMULATION")
                    .build();
                
                // Final validation before adding
                if (message.getMmsi() == null || message.getMmsi().trim().isEmpty()) {
                    log.error("CRITICAL: MMSI is null after building message for ship: {}", ship.getName());
                    continue;
                }
                
                aisDataList.add(message);
                log.debug("Generated simulated AIS data for MMSI: {}, Ship: {}, Destination: {} ({} nm away)", 
                         message.getMmsi(), message.getShipName(), destinationPort.getName(), 
                         String.format("%.2f", distanceNm));
            }
            
            log.info("Generated {} simulated AIS data messages from {} ships", aisDataList.size(), selectedShips.size());
            
        } catch (Exception e) {
            log.error("Error generating simulated AIS data", e);
        }
        
        return aisDataList;
    }
    
    /**
     * Calculate position given starting point, distance (nautical miles), and bearing (degrees)
     */
    private double[] calculatePosition(double startLat, double startLng, double distanceNm, double bearingDeg) {
        double distanceKm = distanceNm * 1.852; // Convert to km
        double distanceRad = distanceKm / 6371.0; // Earth radius in km
        double bearingRad = Math.toRadians(bearingDeg);
        
        double lat1Rad = Math.toRadians(startLat);
        double lng1Rad = Math.toRadians(startLng);
        
        double lat2Rad = Math.asin(Math.sin(lat1Rad) * Math.cos(distanceRad) +
                                   Math.cos(lat1Rad) * Math.sin(distanceRad) * Math.cos(bearingRad));
        double lng2Rad = lng1Rad + Math.atan2(Math.sin(bearingRad) * Math.sin(distanceRad) * Math.cos(lat1Rad),
                                             Math.cos(distanceRad) - Math.sin(lat1Rad) * Math.sin(lat2Rad));
        
        return new double[]{Math.toDegrees(lat2Rad), Math.toDegrees(lng2Rad)};
    }
    
    /**
     * Calculate bearing (course) from point A to point B in degrees
     */
    private double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLng = Math.toRadians(lng2 - lng1);
        
        double y = Math.sin(deltaLng) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLng);
        
        double bearingRad = Math.atan2(y, x);
        double bearingDeg = Math.toDegrees(bearingRad);
        
        // Normalize to 0-360
        return (bearingDeg + 360) % 360;
    }
    
    /**
     * Calculate distance between two points in nautical miles using Haversine formula
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0;
        double earthRadiusNm = earthRadiusKm / 1.852; // Convert to nautical miles
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLng = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return earthRadiusNm * c;
    }
    
    private String getRandomDestination() {
        String[] destinations = {
            "Miami, FL", "Barcelona, Spain", "Venice, Italy",
            "New York, NY", "Los Angeles, CA", "Rome, Italy"
        };
        return destinations[random.nextInt(destinations.length)];
    }
    
    /**
     * Generate historical waypoints for ships to enable route calculation
     * Creates a trail of positions showing ship movement over the last few hours
     */
    private List<AisDataMessage> generateHistoricalWaypoints() {
        List<AisDataMessage> waypoints = new ArrayList<>();
        
        try {
            // Get ships with recent AIS data
            List<CruiseShip> ships = cruiseShipRepository.findByAisEnabledTrue();
            
            for (CruiseShip ship : ships) {
                // Validate ship has required identifiers
                if (ship.getMmsi() == null || ship.getMmsi().trim().isEmpty()) {
                    log.debug("Skipping ship {} (ID: {}): MMSI is null or empty for historical waypoints", 
                             ship.getName(), ship.getId());
                    continue;
                }
                
                // Only generate waypoints if ship has current position
                if (ship.getCurrentLatitude() == null || ship.getCurrentLongitude() == null) {
                    continue;
                }
                
                // Get ports for potential destinations
                List<Port> ports = portRepository.findAll();
                if (ports.isEmpty()) {
                    continue;
                }
                
                // Find nearest port (or use a random one)
                Port nearestPort = ports.get(random.nextInt(ports.size()));
                double distanceToPort = calculateDistance(
                    ship.getCurrentLatitude(), ship.getCurrentLongitude(),
                    nearestPort.getLatitude(), nearestPort.getLongitude()
                );
                
                // Only generate waypoints if ship is within 200 nm of a port
                if (distanceToPort > 200) {
                    continue;
                }
                
                // Generate 3-5 historical waypoints over the last 2-4 hours
                int waypointCount = 3 + random.nextInt(3);
                double hoursBack = 2 + random.nextDouble() * 2; // 2-4 hours ago
                
                for (int i = 0; i < waypointCount; i++) {
                    // Calculate position further from port (historical position)
                    double hoursAgo = hoursBack - (i * (hoursBack / waypointCount));
                    double historicalDistance = distanceToPort + (waypointCount - i) * 5; // 5nm per waypoint
                    
                    // Calculate bearing to port
                    double bearingToPort = calculateBearing(
                        ship.getCurrentLatitude(), ship.getCurrentLongitude(),
                        nearestPort.getLatitude(), nearestPort.getLongitude()
                    );
                    
                    // Calculate historical position (further from port)
                    double[] historicalPos = calculatePosition(
                        nearestPort.getLatitude(), nearestPort.getLongitude(),
                        historicalDistance, bearingToPort + 180 // Opposite direction
                    );
                    
                    // Calculate speed based on distance covered
                    double speed = 15 + random.nextDouble() * 10;
                    
                    // Validate MMSI before building waypoint
                    String shipMmsi = ship.getMmsi() != null ? ship.getMmsi().trim() : null;
                    if (shipMmsi == null || shipMmsi.isEmpty()) {
                        log.warn("Skipping historical waypoint for ship {}: MMSI is null or empty", ship.getName());
                        continue;
                    }
                    
                    // Build historical waypoint
                    AisDataMessage waypoint = AisDataMessage.builder()
                        .mmsi(shipMmsi)
                        .shipName(ship.getName())
                        .latitude(historicalPos[0])
                        .longitude(historicalPos[1])
                        .timestamp(LocalDateTime.now().minusHours((long) hoursAgo)
                                              .minusMinutes((long) ((hoursAgo % 1) * 60)))
                        .speed(speed)
                        .course(bearingToPort)
                        .heading((int) Math.round(bearingToPort))
                        .shipType("Passenger Ship")
                        .destination(nearestPort.getName() + ", " + nearestPort.getCountry())
                        .imo(ship.getImo() != null ? ship.getImo().trim() : null)
                        .callSign(ship.getCallSign() != null ? ship.getCallSign().trim() : null)
                        .stationRange(5 + random.nextDouble() * 20)
                        .signalQuality(getRandomSignalQuality())
                        .dataSource("SIMULATION_HISTORICAL")
                        .build();
                    
                    // Double-check MMSI is set
                    if (waypoint.getMmsi() == null || waypoint.getMmsi().trim().isEmpty()) {
                        log.error("CRITICAL: Historical waypoint MMSI is null after building for ship: {}", ship.getName());
                        continue;
                    }
                    
                    waypoints.add(waypoint);
                }
            }
            
            log.debug("Generated {} historical waypoints for route calculation", waypoints.size());
            
        } catch (Exception e) {
            log.error("Error generating historical waypoints", e);
        }
        
        return waypoints;
    }
    
    private String getRandomSignalQuality() {
        String[] qualities = {"GOOD", "FAIR", "POOR"};
        return qualities[random.nextInt(qualities.length)];
    }
    
    private String getRandomDataSource() {
        String[] sources = {"SATELLITE", "TERRESTRIAL", "BOTH"};
        return sources[random.nextInt(sources.length)];
    }
    
    /**
     * Determine data source from vessel data
     * Real AIS data providers indicate source (terrestrial, satellite, or both)
     */
    private String determineDataSource(Map<String, Object> vessel) {
        // Check for explicit data source field
        Object sourceObj = vessel.getOrDefault("data_source", vessel.getOrDefault("dataSource", vessel.getOrDefault("source", null)));
        if (sourceObj != null) {
            String source = String.valueOf(sourceObj).trim().toUpperCase();
            if (source.contains("SATELLITE") || source.contains("SAT")) {
                return "SATELLITE";
            } else if (source.contains("TERRESTRIAL") || source.contains("TERR")) {
                return "TERRESTRIAL";
            } else if (source.contains("BOTH") || source.contains("HYBRID")) {
                return "BOTH";
            }
        }
        
        // Check station range - satellite typically has longer range
        Object rangeObj = vessel.get("station_range");
        if (rangeObj != null) {
            try {
                double range = Double.parseDouble(String.valueOf(rangeObj));
                if (range > 50) {
                    return "SATELLITE"; // Long range typically indicates satellite
                } else {
                    return "TERRESTRIAL"; // Short range typically indicates terrestrial
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        // Default based on provider
        String provider = aisProvider.toUpperCase();
        if (provider.contains("SATELLITE") || provider.contains("SAT")) {
            return "SATELLITE";
        } else {
            return "TERRESTRIAL"; // Most providers are terrestrial-based
        }
    }
    
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Webhook endpoint for receiving AIS data from external systems
     * Can be called by external AIS transceivers or data providers
     */
    public void receiveAisWebhook(Map<String, Object> webhookData) {
        log.info("Received AIS data via webhook");
        
        try {
            AisDataMessage message = convertApiResponseToMessage(webhookData);
            if (message != null && message.getMmsi() != null && !message.getMmsi().trim().isEmpty()) {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AIS_EXCHANGE,
                    "ais.data.raw",
                    message
                );
                log.info("Processed AIS webhook data for MMSI: {}", message.getMmsi());
            } else {
                log.warn("Skipping AIS webhook data: invalid message or missing MMSI");
            }
        } catch (Exception e) {
            log.error("Error processing AIS webhook", e);
        }
    }
}

