package com.stockpulse.inventory.entity;

import com.stockpulse.inventory.domain.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    @Test
    void reservationStartsActive() {
        Reservation reservation = newReservation();
        assertEquals(ReservationStatus.ACTIVE, reservation.getStatus());
    }

    @Test
    void activeReservationCanBeConfirmedOnlyOnce() {
        Reservation reservation = newReservation();
        reservation.confirm();
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertThrows(IllegalStateException.class, reservation::confirm);
        assertThrows(IllegalStateException.class, reservation::release);
        assertThrows(IllegalStateException.class, reservation::expire);
    }

    @Test
    void activeReservationCanBeReleasedOnlyOnce() {
        Reservation reservation = newReservation();
        reservation.release();
        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
        assertThrows(IllegalStateException.class, reservation::release);
        assertThrows(IllegalStateException.class, reservation::confirm);
    }

    @Test
    void activeReservationCanExpireOnlyOnce() {
        Reservation reservation = newReservation();
        reservation.expire();
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
        assertThrows(IllegalStateException.class, reservation::expire);
        assertThrows(IllegalStateException.class, reservation::confirm);
    }

    private Reservation newReservation() {
        return new Reservation("ORDER-1", UUID.randomUUID(), 2,
                "IDEMPOTENCY-1", Instant.now().plusSeconds(300));
    }
}
