package com.techstore.product.dto;

import com.techstore.product.entity.ItemCategory;
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
    private ItemCategory category;
    private String description;
    private BigDecimal price;
    private Boolean active;
    private Long version;
}
