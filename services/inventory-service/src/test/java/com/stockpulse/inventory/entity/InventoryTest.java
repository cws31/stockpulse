package com.stockpulse.inventory.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    @Test
    void newInventoryStartsWithAllStockAvailable() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 10);
        assertAll(
                () -> assertEquals(10, inventory.getTotalStock()),
                () -> assertEquals(10, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void zeroInitialStockIsAllowed() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 0);
        assertAll(
                () -> assertEquals(0, inventory.getTotalStock()),
                () -> assertEquals(0, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()));
    }

    @Test
    void constructorRejectsNullProductIdAndNegativeStock() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Inventory(null, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Inventory(UUID.randomUUID(), -1)));
    }

    @Test
    void reserveMovesQuantityFromAvailableToReserved() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 10);
        inventory.reserve(3);
        assertAll(
                () -> assertEquals(10, inventory.getTotalStock()),
                () -> assertEquals(7, inventory.getAvailableStock()),
                () -> assertEquals(3, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void reserveRejectsZeroNegativeAndInsufficientQuantitiesWithoutMutation() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 2);
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> inventory.reserve(0)),
                () -> assertThrows(IllegalArgumentException.class, () -> inventory.reserve(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> inventory.reserve(3)),
                () -> assertEquals(2, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void confirmConsumesReservedAndTotalStock() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 10);
        inventory.reserve(3);
        inventory.confirm(3);
        assertAll(
                () -> assertEquals(7, inventory.getTotalStock()),
                () -> assertEquals(7, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void confirmRejectsZeroNegativeOrMoreThanReserved() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 10);
        inventory.reserve(2);
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> inventory.confirm(0)),
                () -> assertThrows(IllegalStateException.class, () -> inventory.confirm(-1)),
                () -> assertThrows(IllegalStateException.class, () -> inventory.confirm(3)),
                () -> assertEquals(10, inventory.getTotalStock()),
                () -> assertEquals(8, inventory.getAvailableStock()),
                () -> assertEquals(2, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void releaseReturnsReservedQuantityToAvailable() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 10);
        inventory.reserve(3);
        inventory.release(3);
        assertAll(
                () -> assertEquals(10, inventory.getTotalStock()),
                () -> assertEquals(10, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void releaseRejectsZeroNegativeOrMoreThanReserved() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 10);
        inventory.reserve(2);
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> inventory.release(0)),
                () -> assertThrows(IllegalStateException.class, () -> inventory.release(-1)),
                () -> assertThrows(IllegalStateException.class, () -> inventory.release(3)),
                () -> assertEquals(8, inventory.getAvailableStock()),
                () -> assertEquals(2, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void positiveAdjustmentAddsToTotalAndAvailableStock() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 5);
        inventory.reserve(2);
        inventory.adjust(4);
        assertAll(
                () -> assertEquals(9, inventory.getTotalStock()),
                () -> assertEquals(7, inventory.getAvailableStock()),
                () -> assertEquals(2, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void negativeAdjustmentRemovesOnlyAvailableStock() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 5);
        inventory.reserve(3);
        inventory.adjust(-2);
        assertAll(
                () -> assertEquals(3, inventory.getTotalStock()),
                () -> assertEquals(0, inventory.getAvailableStock()),
                () -> assertEquals(3, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void adjustmentRejectsZeroAndCannotConsumeReservedStock() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 5);
        inventory.reserve(3);
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> inventory.adjust(0)),
                () -> assertThrows(IllegalArgumentException.class, () -> inventory.adjust(-3)),
                () -> assertEquals(5, inventory.getTotalStock()),
                () -> assertEquals(2, inventory.getAvailableStock()),
                () -> assertEquals(3, inventory.getReservedStock()),
                () -> assertInvariant(inventory));
    }

    @Test
    void adjustmentRejectsLongMinValueAndOverflowWithoutMutation() {
        Inventory inventory = new Inventory(UUID.randomUUID(), Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> inventory.adjust(1));
        assertThrows(IllegalArgumentException.class, () -> inventory.adjust(Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, inventory.getTotalStock());
        assertEquals(Long.MAX_VALUE, inventory.getAvailableStock());
        assertInvariant(inventory);
    }

    @Test
    void stockInvariantHoldsAfterMultipleOperations() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 20);
        inventory.reserve(8);
        inventory.release(3);
        inventory.confirm(2);
        inventory.adjust(4);
        assertInvariant(inventory);
    }

    private static void assertInvariant(Inventory inventory) {
        assertEquals(inventory.getTotalStock(),
                inventory.getAvailableStock() + inventory.getReservedStock());
        assertTrue(inventory.getAvailableStock() >= 0);
        assertTrue(inventory.getReservedStock() >= 0);
    }
}
