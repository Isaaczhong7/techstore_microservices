package com.techstore.product.dto;

import com.techstore.product.entity.ItemCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntry {
    private String productName;
    private ItemCategory category;
    private String description;
    private BigDecimal price;
    private Boolean active;
}
