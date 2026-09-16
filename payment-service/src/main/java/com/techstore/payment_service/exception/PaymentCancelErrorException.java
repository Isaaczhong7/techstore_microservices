package com.techstore.payment_service.exception;

import java.util.UUID;

public class PaymentCancelErrorException extends RuntimeException{
    public PaymentCancelErrorException(String message) {
        super(message);
    }
}
