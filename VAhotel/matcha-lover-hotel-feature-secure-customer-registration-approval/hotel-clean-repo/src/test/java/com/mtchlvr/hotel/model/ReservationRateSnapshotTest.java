package com.mtchlvr.hotel.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ReservationRateSnapshotTest {
    @Test void keepsTheRateAtBookingWhenTheRoomPriceChanges() {
        Room room = new Room("101", "Standard", new BigDecimal("1000.00"), 2);
        AppUser user = new AppUser("guest@example.com", "hash", "Guest", "CUSTOMER", true);
        Reservation reservation = new Reservation(user, room, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));
        room.setNightlyRate(new BigDecimal("1500.00"));
        assertEquals(new BigDecimal("2000.00"), reservation.getTotalCost());
    }
}
