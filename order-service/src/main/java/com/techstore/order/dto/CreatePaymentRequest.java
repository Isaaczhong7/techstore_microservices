package com.techstore.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatePaymentRequest {
    private UUID orderId;

    private UUID reservationId;

    private BigDecimal amount;

    private UUID paymentMethodId;
}
