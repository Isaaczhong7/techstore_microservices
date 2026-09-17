package com.techstore.inventory.dto;

import com.techstore.inventory.entity.ReservationItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReservationItemResponse {
    private UUID productId;
    private Long quantity;
    private ReservationItemStatus status;
}
