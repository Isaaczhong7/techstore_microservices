package com.techstore.inventory.dto;

import com.techstore.inventory.entity.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmReservationResponse {
    private ReservationStatus status;
    private UUID reservationId;
    private UUID orderId;
}
