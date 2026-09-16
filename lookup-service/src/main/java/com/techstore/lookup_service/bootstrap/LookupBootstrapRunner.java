package com.techstore.lookup_service.bootstrap;

import com.techstore.lookup_service.repository.ProductLookupRepository;
import com.techstore.lookup_service.service.LookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LookupBootstrapRunner implements ApplicationRunner {

    private final LookupService lookupService;
    private final ProductLookupRepository productLookupRepository;

    @Override
    public void run(ApplicationArguments args) {

        if (productLookupRepository.count() == 0) {

            log.info("Lookup database is empty. Starting bootstrap...");

            lookupService.fetchAllProducts();

            log.info("Lookup bootstrap completed.");
        }
    }
}