package com.techstore.lookup_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_lookup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductLookupEntity {

    @Id
    @Column(nullable = false)
    private UUID productId;

    private String productName;

    private String description;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private ItemCategory category;

    private BigDecimal price;

    private Long quantity;

    private Long itemSold;

    private Boolean active;

    private Long productVersion;
    private Long inventoryVersion;

    private LocalDateTime updatedAt;
}
