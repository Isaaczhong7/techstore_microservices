package com.techstore.order.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntry {
    private UUID productId;
    private Long quantity;
}
