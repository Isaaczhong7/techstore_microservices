package com.techstore.order.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmReservationRequest {
    private UUID orderId;
    private UUID reservationId;

}
