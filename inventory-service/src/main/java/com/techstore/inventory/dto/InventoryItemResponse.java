package com.techstore.inventory.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryItemResponse {
    private Long productId;
    private Long itemSold;
    private Long quantity;
    private Long version;
}
