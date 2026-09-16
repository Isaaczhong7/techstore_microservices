package com.techstore.payment_service.exception;

public class UnmatchedPaymentMethodException extends RuntimeException{
    public UnmatchedPaymentMethodException(String message){
        super(message);
    }
}
