package com.techstore.order.exception;

public class PaymentIdNotFoundException extends RuntimeException {
    public PaymentIdNotFoundException(String message){
        super(message);
    }
}
