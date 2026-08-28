package com.xinyu.esportsticketing.exception;

public class DuplicateUserException extends IllegalArgumentException {

    public DuplicateUserException(String message) {
        super(message);
    }
}