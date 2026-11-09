package com.stockpulse.inventory.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryJob {

    private final InventoryService inventoryService;

    public ReservationExpiryJob(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Scheduled(fixedDelayString = "${stockpulse.reservation.expiry-scan-interval-ms:10000}")
    public void expireReservations() {
        inventoryService.expireReservations();
    }
}
