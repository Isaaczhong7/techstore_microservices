package com.techstore.order.dto;

import com.techstore.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReservationStatusResponse {
    private UUID orderId;
    private OrderStatus status;
}
