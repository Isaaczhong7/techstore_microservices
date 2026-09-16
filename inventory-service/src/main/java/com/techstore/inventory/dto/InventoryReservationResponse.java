package com.techstore.inventory.dto;

import com.techstore.inventory.entity.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationResponse {

    private UUID reservationId;
    private UUID orderId;

    private ReservationStatus status;

    private List<ReservationItemResponse> items;
}