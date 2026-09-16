package com.techstore.payment_service.exception;

import java.util.UUID;

public class PaymentIdNotFoundException extends RuntimeException {
    public PaymentIdNotFoundException(UUID paymentId) {
        super("payment not found: " + paymentId);
    }

}
