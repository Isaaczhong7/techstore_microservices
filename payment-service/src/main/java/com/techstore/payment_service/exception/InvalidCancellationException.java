package com.techstore.payment_service.exception;

public class InvalidCancellationException extends RuntimeException{
    public InvalidCancellationException(String message){
        super(message);
    }
}
