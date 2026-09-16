package com.techstore.inventory.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelReservationRequest {
    private UUID orderId;
    private UUID reservationId;

}
