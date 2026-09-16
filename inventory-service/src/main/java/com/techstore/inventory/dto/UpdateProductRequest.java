package com.techstore.inventory.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UpdateProductRequest {
    private Long quantity;
}
