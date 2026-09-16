package com.techstore.payment_service.exception;

public class DuplicatePaymentRequestException extends RuntimeException{
    public DuplicatePaymentRequestException(String message){
        super(message);
    }
}
