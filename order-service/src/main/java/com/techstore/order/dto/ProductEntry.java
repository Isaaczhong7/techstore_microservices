package com.techstore.order.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntry {
    private Long productId;
    private Long quantity;
}
