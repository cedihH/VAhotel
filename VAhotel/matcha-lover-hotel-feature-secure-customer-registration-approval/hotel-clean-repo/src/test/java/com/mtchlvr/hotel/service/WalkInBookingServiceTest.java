package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.model.Room;
import com.mtchlvr.hotel.model.WalkInGuest;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import com.mtchlvr.hotel.repo.RoomRepository;
import com.mtchlvr.hotel.repo.WalkInGuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WalkInBookingServiceTest {
    private ReservationService reservationService;
    private RoomRepository rooms;
    private WalkInGuestRepository guests;
    private ReservationRepository reservations;
    private PaymentRepository payments;
    private WalkInBookingService service;
    private Room room;

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationService.class);
        rooms = mock(RoomRepository.class);
        guests = mock(WalkInGuestRepository.class);
        reservations = mock(ReservationRepository.class);
        payments = mock(PaymentRepository.class);
        service = new WalkInBookingService(
                reservationService, rooms, guests, reservations, payments);

        room = mock(Room.class);
        when(room.getId()).thenReturn(1L);
        when(room.getNightlyRate()).thenReturn(new BigDecimal("1800.00"));
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(reservationService.isAvailable(
                any(Room.class), any(), any(), any(), any())).thenReturn(true);
        when(guests.save(any(WalkInGuest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservations.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsApprovedWalkInReservationWithOneFullPayment() {
        Reservation result = service.createWalkInBooking(
                "  Walk-in Guest  ", " guest@example.com ", " 09170000000 ",
                1L, LocalDate.now().plusDays(2), LocalTime.of(14, 0),
                LocalDate.now().plusDays(4), LocalTime.NOON);

        assertEquals("WALK_IN", result.getBookingType());
        assertEquals("APPROVED", result.getStatus());
        assertNull(result.getCustomer());
        assertEquals("Walk-in Guest", result.getWalkInGuest().getFullName());
        assertEquals("guest@example.com", result.getWalkInGuest().getEmail());

        verify(guests).save(any(WalkInGuest.class));
        verify(reservations).save(result);
        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());
        assertEquals("WALK_IN_FULL_PAYMENT", captor.getValue().getPaymentType());
        assertEquals("PAID", captor.getValue().getStatus());
        assertEquals("NEXUS_SIMULATION", captor.getValue().getPaymentMethod());
        assertEquals(new BigDecimal("3600.00"), captor.getValue().getAmount());
    }

    @Test
    void rejectsBlankGuestNameBeforeSavingAnything() {
        assertThrows(IllegalArgumentException.class, () -> service.createWalkInBooking(
                "   ", null, null, 1L,
                LocalDate.now().plusDays(2), LocalTime.of(14, 0),
                LocalDate.now().plusDays(3), LocalTime.NOON));
        verifyNoInteractions(guests, reservations, payments);
    }

    @Test
    void rejectsMissingRoomIdBeforeSavingAnything() {
        assertThrows(IllegalArgumentException.class, () -> service.createWalkInBooking(
                "Guest", null, null, null,
                LocalDate.now().plusDays(2), LocalTime.of(14, 0),
                LocalDate.now().plusDays(3), LocalTime.NOON));
        verifyNoInteractions(guests, reservations, payments);
    }

    @Test
    void rejectsUnavailableRoomWithoutSavingGuestOrPayment() {
        when(reservationService.isAvailable(
                any(Room.class), any(), any(), any(), any())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.createWalkInBooking(
                "Guest", null, null, 1L,
                LocalDate.now().plusDays(2), LocalTime.of(14, 0),
                LocalDate.now().plusDays(3), LocalTime.NOON));
        verifyNoInteractions(guests, reservations, payments);
    }
}
