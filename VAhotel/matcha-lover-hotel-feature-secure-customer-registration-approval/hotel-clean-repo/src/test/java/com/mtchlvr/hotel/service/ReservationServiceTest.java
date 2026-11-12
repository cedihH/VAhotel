package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.AppUser;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.model.Room;
import com.mtchlvr.hotel.repo.ReservationRepository;
import com.mtchlvr.hotel.repo.RoomRepository;
import com.mtchlvr.hotel.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private ReservationRepository reservations;
    private RoomRepository rooms;
    private UserRepository users;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        rooms = mock(RoomRepository.class);
        users = mock(UserRepository.class);
        service = new ReservationService(reservations, rooms, users);
    }

    @Test
    void rejectsCheckoutOnOrBeforeCheckin() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.reserve("guest@example.com", 1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(1)));
        assertTrue(error.getMessage().contains("valid check-in"));
        verifyNoInteractions(users, rooms, reservations);
    }

    @Test
    void rejectsCheckInTimeInThePast() {
        LocalDate yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Manila")).minusDays(1);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.reserve("guest@example.com", 1L, yesterday, LocalTime.of(10, 0),
                yesterday, LocalTime.of(12, 0)));
        assertTrue(error.getMessage().contains("future check-in"));
        verifyNoInteractions(users, rooms, reservations);
    }

    @Test
    void appliesStandardCheckInAndCheckoutTimesAndLeavesReservationPending() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Manila")).plusDays(1);
        AppUser customer = mock(AppUser.class);
        Room room = mock(Room.class);

        when(users.findByEmailIgnoreCase("guest@example.com"))
            .thenReturn(Optional.of(customer));
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(1L);
        when(room.isAvailable()).thenReturn(true);
        when(room.isActive()).thenReturn(true);
        when(reservations.findAll()).thenReturn(List.of());
        when(reservations.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = service.reserve(
            "guest@example.com", 1L, today, LocalTime.of(10, 0),
            today.plusDays(2), LocalTime.of(18, 0));

        assertEquals(today, result.getCheckIn());
        assertEquals(today.plusDays(2), result.getCheckOut());
        assertEquals(LocalTime.of(14, 0), result.getCheckInTime());
        assertEquals(LocalTime.NOON, result.getCheckOutTime());
        assertEquals("PENDING", result.getStatus());
        verify(reservations).save(any(Reservation.class));
    }

    @Test
    void rejectsApprovalWhenAnotherActiveReservationOverlaps() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(7L);
        Reservation target = mock(Reservation.class);
        when(target.getId()).thenReturn(1L);
        when(target.getStatus()).thenReturn("PENDING");
        when(target.getRoom()).thenReturn(room);
        when(target.getCheckIn()).thenReturn(LocalDate.now().plusDays(4));
        when(target.getCheckOut()).thenReturn(LocalDate.now().plusDays(8));
        Reservation overlap = mock(Reservation.class);
        when(overlap.getId()).thenReturn(2L);
        when(overlap.getRoom()).thenReturn(room);
        when(overlap.getStatus()).thenReturn("PENDING");
        when(overlap.getCheckIn()).thenReturn(LocalDate.now().plusDays(6));
        when(overlap.getCheckOut()).thenReturn(LocalDate.now().plusDays(10));
        when(reservations.findById(1L)).thenReturn(Optional.of(target));
        when(reservations.findAll()).thenReturn(List.of(target, overlap));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.updateStatus(1L, "APPROVED"));
        assertTrue(error.getMessage().contains("overlaps"));
        verify(reservations, never()).save(any());
    }

    @Test
    void acceptsValidStatusAndPersistsIt() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("PENDING");
        when(reservations.findById(9L)).thenReturn(Optional.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);
        assertSame(reservation, service.updateStatus(9L, "REJECTED"));
        verify(reservation).setStatus("REJECTED");
        verify(reservations).save(reservation);
    }


    @Test
    void keepingPendingCancellationRequestRestoresPendingStatus() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(reservation.getStatusBeforeCancellation()).thenReturn("PENDING");
        // This test covers restoring a still-valid pending reservation.
        // Give it a future check-in so the expiry guard can evaluate it safely.
        when(reservation.getCheckIn()).thenReturn(
            LocalDate.now(java.time.ZoneId.of("Asia/Manila")).plusDays(2));
        when(reservation.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        when(reservations.findById(11L)).thenReturn(Optional.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);

        assertSame(reservation, service.updateStatus(11L, "APPROVED"));

        verify(reservation).setStatus("PENDING");
        verify(reservation).setStatusBeforeCancellation(null);
        verify(reservations).save(reservation);
    }

    @Test
    void keepingApprovedCancellationRequestRestoresApprovedStatus() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(7L);
        Reservation reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(12L);
        when(reservation.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(reservation.getStatusBeforeCancellation()).thenReturn("APPROVED");
        when(reservation.getRoom()).thenReturn(room);
        when(reservation.getCheckIn()).thenReturn(LocalDate.now().plusDays(3));
        when(reservation.getCheckOut()).thenReturn(LocalDate.now().plusDays(4));
        when(reservations.findById(12L)).thenReturn(Optional.of(reservation));
        when(reservations.findAll()).thenReturn(List.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);

        assertSame(reservation, service.updateStatus(12L, "APPROVED"));

        verify(reservation).setStatus("APPROVED");
        verify(reservation).setStatusBeforeCancellation(null);
        verify(reservations).save(reservation);
    }

    @Test
    void confirmingCancellationMarksRequestCancelledAndClearsPriorStatus() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(reservations.findById(13L)).thenReturn(Optional.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);

        assertSame(reservation, service.updateStatus(13L, "CANCELLED"));

        verify(reservation).setStatus("CANCELLED");
        verify(reservation).setStatusBeforeCancellation(null);
        verify(reservations).save(reservation);
    }

    @Test
    void rejectsReactivatingCompletedReservation() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("COMPLETED");
        when(reservations.findById(21L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalArgumentException.class,
            () -> service.updateStatus(21L, "APPROVED"));
        verify(reservations, never()).save(any(Reservation.class));
    }

    @Test
    void rejectsDirectCancellationOfPendingReservation() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("PENDING");
        when(reservations.findById(22L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalArgumentException.class,
            () -> service.updateStatus(22L, "CANCELLED"));
        verify(reservations, never()).save(any(Reservation.class));
    }

    @Test
    void rejectsChangingRejectedReservation() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("REJECTED");
        when(reservations.findById(23L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalArgumentException.class,
            () -> service.updateStatus(23L, "APPROVED"));
        verify(reservations, never()).save(any(Reservation.class));
    }

    @Test
    void rejectsUnknownStatus() {
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(9L, "PENDING"));
        verifyNoInteractions(reservations);
    }

    @Test
    void automaticallyVoidsExpiredPendingReservations() {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        Reservation expired = mock(Reservation.class);

        when(expired.getStatus()).thenReturn("PENDING");
        when(expired.getCheckIn()).thenReturn(LocalDate.now(zone).minusDays(1));
        when(expired.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        when(reservations.findAll()).thenReturn(List.of(expired));

        service.voidExpiredPendingReservations();

        verify(expired).setStatus("VOID");
        verify(reservations).saveAll(anyList());
    }

    @Test
    void automaticallyVoidsExpiredPendingCancellationRequests() {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        Reservation expired = mock(Reservation.class);

        when(expired.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(expired.getStatusBeforeCancellation()).thenReturn("PENDING");
        when(expired.getCheckIn()).thenReturn(LocalDate.now(zone).minusDays(1));
        when(expired.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        when(reservations.findAll()).thenReturn(List.of(expired));

        service.voidExpiredPendingReservations();

        verify(expired).setStatus("VOID");
        verify(expired).setStatusBeforeCancellation(null);
        verify(reservations).saveAll(anyList());
    }

    @Test
    void doesNotVoidCancellationRequestForPreviouslyApprovedReservation() {
        Reservation cancellation = mock(Reservation.class);

        when(cancellation.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(cancellation.getStatusBeforeCancellation()).thenReturn("APPROVED");
        when(reservations.findAll()).thenReturn(List.of(cancellation));

        service.voidExpiredPendingReservations();

        verify(cancellation, never()).setStatus("VOID");
        verify(reservations, never()).saveAll(anyList());
    }

    @Test
    void keepingExpiredPendingCancellationRequestChangesItToVoid() {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(reservation.getStatusBeforeCancellation()).thenReturn("PENDING");
        when(reservation.getCheckIn()).thenReturn(LocalDate.now(zone).minusDays(1));
        when(reservation.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        when(reservations.findById(14L)).thenReturn(Optional.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);

        assertSame(reservation, service.updateStatus(14L, "APPROVED"));

        verify(reservation).setStatus("VOID");
        verify(reservation).setStatusBeforeCancellation(null);
        verify(reservations).save(reservation);
    }

    @Test
    void doesNotVoidApprovedReservations() {
        Reservation approved = mock(Reservation.class);

        when(approved.getStatus()).thenReturn("APPROVED");
        when(reservations.findAll()).thenReturn(List.of(approved));

        service.voidExpiredPendingReservations();

        verify(approved, never()).setStatus("VOID");
        verify(reservations, never()).saveAll(anyList());
    }

    @Test
    void rejectsApprovalWhenPendingReservationCheckInTimeHasArrived() {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        Reservation expired = mock(Reservation.class);

        when(expired.getStatus()).thenReturn("PENDING");
        when(expired.getCheckIn()).thenReturn(LocalDate.now(zone).minusDays(1));
        when(expired.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        when(reservations.findById(20L)).thenReturn(Optional.of(expired));

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> service.updateStatus(20L, "APPROVED")
        );

        assertTrue(error.getMessage().contains("expired"));
        verify(reservations, never()).save(any(Reservation.class));
    }


    @Test
    void doesNotVoidPencilBookingBeforeItsCheckInTime() {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        Reservation pencil = mock(Reservation.class);

        // The review period may have passed, but check-in is still in the future.
        when(pencil.getStatus()).thenReturn("PENCIL_PENDING_REVIEW");
        when(pencil.getCheckIn()).thenReturn(LocalDate.now(zone).plusDays(2));
        when(pencil.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        when(reservations.findAll()).thenReturn(List.of(pencil));

        service.voidExpiredPendingReservations();

        verify(pencil, never()).setStatus("VOID");
        verify(reservations, never()).saveAll(anyList());
    }

    @Test
    void automaticallyVoidsPencilBookingWhenCheckInTimeHasArrived() {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        Reservation pencil = mock(Reservation.class);

        when(pencil.getStatus()).thenReturn("PENCIL_PENDING_REVIEW");
        when(pencil.getCheckIn()).thenReturn(LocalDate.now(zone).minusDays(1));
        when(pencil.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        when(reservations.findAll()).thenReturn(List.of(pencil));

        service.voidExpiredPendingReservations();

        verify(pencil).setStatus("VOID");
        verify(pencil).setStatusBeforeCancellation(null);
        verify(reservations).saveAll(anyList());
    }

}
