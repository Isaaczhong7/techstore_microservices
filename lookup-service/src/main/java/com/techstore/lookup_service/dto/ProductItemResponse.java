package com.techstore.lookup_service.dto;

import com.techstore.lookup_service.entity.ItemCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductItemResponse {
    private UUID productId;
    private String productName;
    private String description;
    private ItemCategory category;
    private BigDecimal price;
    private Boolean active;
    private Long version;
}
