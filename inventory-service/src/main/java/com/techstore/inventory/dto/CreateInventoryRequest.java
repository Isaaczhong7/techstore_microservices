package com.techstore.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateInventoryRequest {

    @NotNull
    private Long productId;

    @NotNull
    @Min(0)
    private Long quantity;

}
