package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PencilBookingServiceTest {

    private ReservationRepository reservations;
    private PaymentRepository payments;
    private PencilBookingService service;

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        payments = mock(PaymentRepository.class);

        service = new PencilBookingService(reservations, payments);

        reservation = mock(Reservation.class);

        when(reservations.findById(1L))
                .thenReturn(Optional.of(reservation));

        when(reservation.getBookingType()).thenReturn("PENCIL");
        when(reservation.getStatus()).thenReturn("PENCIL_PENDING_REVIEW");
        when(reservation.getTotalCost()).thenReturn(new BigDecimal("1000.00"));
        when(reservation.getCheckIn()).thenReturn(
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila")).plusDays(5));
        when(reservation.getCheckOut()).thenReturn(
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila")).plusDays(7));
        when(reservation.getCheckInTime()).thenReturn(java.time.LocalTime.of(14, 0));

        when(reservations.save(reservation)).thenReturn(reservation);
    }

    private Payment createDownPayment(long hoursAgo, String amount) {
        return new Payment(
                reservation,
                new BigDecimal(amount),
                "PENCIL_DOWN_PAYMENT",
                "NEXUS_SIMULATION",
                "PAID",
                "TEST-TRANSACTION",
                Instant.now().minusSeconds(hoursAgo * 60 * 60)
        );
    }

    @Test
    void approvesPencilBookingWhenReviewedWithin72Hours() {
        Payment payment = createDownPayment(71, "500.00");

        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                1L, "PENCIL_DOWN_PAYMENT", "PAID"))
                .thenReturn(Optional.of(payment));

        Reservation result = service.reviewBooking(1L, "APPROVED");

        assertSame(reservation, result);
        verify(reservation).setStatus("APPROVED");
        verify(reservations).save(reservation);
    }

    @Test
    void rejectsPencilBookingWhenReviewedWithin72Hours() {
        Payment payment = createDownPayment(71, "500.00");

        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                1L, "PENCIL_DOWN_PAYMENT", "PAID"))
                .thenReturn(Optional.of(payment));

        Reservation result = service.reviewBooking(1L, "REJECTED");

        assertSame(reservation, result);
        verify(reservation).setStatus("REJECTED");
        verify(reservations).save(reservation);
    }

    @Test
    void refusesReviewAfter72HourDeadline() {
        Payment payment = createDownPayment(73, "500.00");

        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                1L, "PENCIL_DOWN_PAYMENT", "PAID"))
                .thenReturn(Optional.of(payment));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewBooking(1L, "APPROVED")
        );

        assertTrue(error.getMessage().contains("deadline"));
        verify(reservation, never()).setStatus(anyString());
        verify(reservations, never()).save(any());
    }

    @Test
    void refusesReviewWhenCheckInTimeHasArrivedEvenIfSchedulerHasNotRun() {
        when(reservation.getCheckIn()).thenReturn(
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila")).minusDays(1));
        Payment payment = createDownPayment(1, "500.00");
        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                1L, "PENCIL_DOWN_PAYMENT", "PAID"))
                .thenReturn(Optional.of(payment));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewBooking(1L, "APPROVED"));

        assertTrue(error.getMessage().contains("check-in"));
        verify(reservation, never()).setStatus(anyString());
        verify(reservations, never()).save(any());
    }

    @Test
    void refusesUnsupportedManagerDecision() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewBooking(1L, "CANCELLED")
        );

        verifyNoInteractions(reservations, payments);
    }

    @Test
    void refusesRegularReservation() {
        when(reservation.getBookingType()).thenReturn("ONLINE_REGULAR");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewBooking(1L, "APPROVED")
        );

        verifyNoInteractions(payments);
        verify(reservations, never()).save(any());
    }

    @Test
    void refusesReservationThatIsNoLongerPendingReview() {
        when(reservation.getStatus()).thenReturn("APPROVED");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewBooking(1L, "REJECTED")
        );

        verifyNoInteractions(payments);
        verify(reservations, never()).save(any());
    }

    @Test
    void refusesPencilBookingWithoutSuccessfulDownPayment() {
        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                1L, "PENCIL_DOWN_PAYMENT", "PAID"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewBooking(1L, "APPROVED")
        );

        verify(reservations, never()).save(any());
    }

    @Test
    void refusesDownPaymentThatIsNotExactly50Percent() {
        Payment payment = createDownPayment(24, "400.00");

        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                1L, "PENCIL_DOWN_PAYMENT", "PAID"))
                .thenReturn(Optional.of(payment));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewBooking(1L, "APPROVED")
        );

        verify(reservation, never()).setStatus(anyString());
        verify(reservations, never()).save(any());
    }
}
