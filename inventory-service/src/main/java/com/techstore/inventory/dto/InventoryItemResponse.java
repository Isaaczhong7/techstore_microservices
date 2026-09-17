package com.techstore.inventory.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryItemResponse {
    private UUID productId;
    private Long itemSold;
    private Long quantity;
    private Long version;
}
