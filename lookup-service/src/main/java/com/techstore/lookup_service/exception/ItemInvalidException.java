package com.techstore.lookup_service.exception;

public class ItemInvalidException extends RuntimeException{
    public ItemInvalidException(String message){
        super(message);
    }
}
