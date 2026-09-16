package com.techstore.payment_service.dto;

import com.techstore.payment_service.entity.CurrencyType;
import com.techstore.payment_service.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPaymentRequest {
    private UUID orderId;
    private UUID paymentId;
    private CurrencyType currencyType;
    private UUID paymentMethodId;
}
