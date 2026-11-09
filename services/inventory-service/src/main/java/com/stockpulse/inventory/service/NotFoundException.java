package com.stockpulse.inventory.service;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
