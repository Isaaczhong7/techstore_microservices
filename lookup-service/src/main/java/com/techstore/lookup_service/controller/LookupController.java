package com.techstore.lookup_service.controller;

import com.techstore.lookup_service.service.LookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.techstore.lookup_service.dto.ProductItem;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;


@RestController
@RequestMapping("/api/lookup")
@RequiredArgsConstructor
@Slf4j
public class LookupController {

    private final LookupService lookupService;

    @GetMapping
    public ResponseEntity<Page<ProductItem>> fetchItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        return ResponseEntity.ok(
                lookupService.fetchItems(page, size)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductItem> fetchSpecificItem(
            @PathVariable UUID id
    ) {

        return ResponseEntity.ok(
                lookupService.getProduct(id)
        );
    }
}
