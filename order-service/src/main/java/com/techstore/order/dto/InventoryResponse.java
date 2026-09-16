package com.techstore.order.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryResponse {
    private Long id;
    private Long productId;

    private Long quantity;
    private Long reservedQuantity;
    private Long availableQuantity;

}
