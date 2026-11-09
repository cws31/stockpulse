package com.stockpulse.inventory.service;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
