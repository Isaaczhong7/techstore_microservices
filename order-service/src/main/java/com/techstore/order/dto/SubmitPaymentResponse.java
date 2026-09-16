package com.techstore.order.dto;

import com.techstore.order.entity.OrderStatus;
import com.techstore.order.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPaymentResponse {
    private UUID paymentId;

    private PaymentStatus status;

    private UUID orderId;

    private UUID reservationId;
}
