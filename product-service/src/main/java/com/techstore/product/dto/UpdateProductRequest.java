package com.techstore.product.dto;

import com.techstore.product.entity.ItemCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    private String productName;

    private String description;

    private ItemCategory category;

    private BigDecimal price;

    private Boolean active;
}
