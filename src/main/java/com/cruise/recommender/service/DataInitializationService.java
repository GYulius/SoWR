package com.cruise.recommender.service;

import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.PortRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Service for initializing sample data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializationService implements CommandLineRunner {
    
    private final PortRepository portRepository;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing sample data...");
        
        if (portRepository.count() == 0) {
            initializePorts();
            log.info("Sample ports data initialized successfully");
        } else {
            log.info("Ports data already exists, skipping initialization");
        }
    }
    
    private void initializePorts() {
        List<Port> ports = Arrays.asList(
            Port.builder()
                .portCode("BCN")
                .name("Port of Barcelona")
                .country("Spain")
                .region("Catalonia")
                .city("Barcelona")
                .latitude(41.3851)
                .longitude(2.1734)
                .capacity(15000)
                .facilities("{\"terminals\": 7, \"docking_berths\": 12, \"tender_boats\": 8}")
                .amenities("[\"free_wifi\", \"currency_exchange\", \"atm\", \"luggage_storage\"]")
                .dockingFees(new BigDecimal("2500.00"))
                .currency("EUR")
                .timezone("Europe/Madrid")
                .language("Spanish, Catalan, English")
                .build(),
                
            Port.builder()
                .portCode("ROM")
                .name("Port of Civitavecchia")
                .country("Italy")
                .region("Lazio")
                .city("Rome")
                .latitude(42.0935)
                .longitude(11.7964)
                .capacity(12000)
                .facilities("{\"terminals\": 4, \"docking_berths\": 8, \"tender_boats\": 6}")
                .amenities("[\"free_wifi\", \"currency_exchange\", \"atm\", \"luggage_storage\"]")
                .dockingFees(new BigDecimal("2200.00"))
                .currency("EUR")
                .timezone("Europe/Rome")
                .language("Italian, English")
                .build(),
                
            Port.builder()
                .portCode("ATH")
                .name("Port of Piraeus")
                .country("Greece")
                .region("Attica")
                .city("Athens")
                .latitude(37.9755)
                .longitude(23.7348)
                .capacity(18000)
                .facilities("{\"terminals\": 6, \"docking_berths\": 10, \"tender_boats\": 12}")
                .amenities("[\"free_wifi\", \"currency_exchange\", \"atm\", \"luggage_storage\"]")
                .dockingFees(new BigDecimal("1800.00"))
                .currency("EUR")
                .timezone("Europe/Athens")
                .language("Greek, English")
                .build()
        );
        
        portRepository.saveAll(ports);
    }
}
