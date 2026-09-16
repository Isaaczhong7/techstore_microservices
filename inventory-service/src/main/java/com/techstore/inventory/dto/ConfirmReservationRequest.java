package com.techstore.inventory.dto;

import com.techstore.inventory.entity.ReservationStatus;
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
