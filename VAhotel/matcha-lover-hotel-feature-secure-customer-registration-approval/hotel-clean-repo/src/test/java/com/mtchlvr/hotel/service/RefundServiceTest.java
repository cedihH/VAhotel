package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.AppUser;
import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.RefundRequest;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.RefundRequestRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefundServiceTest {
    private ReservationRepository reservations;
    private PaymentRepository payments;
    private RefundRequestRepository requests;
    private RefundService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        payments = mock(PaymentRepository.class);
        requests = mock(RefundRequestRepository.class);
        service = new RefundService(reservations, payments, requests);
    }

    private Reservation voidPencilReservation() {
        AppUser customer = new AppUser(
                "guest@example.com", "hash", "Test Guest", "CUSTOMER", true);
        Reservation reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(9L);
        when(reservation.getCustomer()).thenReturn(customer);
        when(reservation.getBookingType()).thenReturn("PENCIL");
        when(reservation.getStatus()).thenReturn("VOID");
        return reservation;
    }

    @Test
    void customerCanRequestRefundForVoidedPencilBooking() {
        Reservation reservation = voidPencilReservation();
        Payment downPayment = new Payment(
                reservation, new BigDecimal("500.00"), "PENCIL_DOWN_PAYMENT",
                "NEXUS_SIMULATION", "PAID", "TEST-DOWN");
        when(reservations.findById(9L)).thenReturn(Optional.of(reservation));
        when(requests.findByReservationId(9L)).thenReturn(Optional.empty());
        when(payments.findAllByReservationIdOrderByPaidAtAsc(9L))
                .thenReturn(List.of(downPayment));
        when(requests.save(any(RefundRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundRequest result = service.requestPencilRefund(9L, "guest@example.com");

        assertEquals(new BigDecimal("500.00"), result.getAmount());
        assertEquals("REQUESTED", result.getStatus());
        verify(requests).save(any(RefundRequest.class));
    }

    @Test
    void preventsDuplicateRefundRequests() {
        Reservation reservation = voidPencilReservation();
        when(reservations.findById(9L)).thenReturn(Optional.of(reservation));
        when(requests.findByReservationId(9L))
                .thenReturn(Optional.of(mock(RefundRequest.class)));

        assertThrows(IllegalArgumentException.class,
                () -> service.requestPencilRefund(9L, "guest@example.com"));
        verify(payments, never()).findAllByReservationIdOrderByPaidAtAsc(any());
        verify(requests, never()).save(any());
    }

    @Test
    void rejectsRefundRequestForNonOwner() {
        Reservation reservation = voidPencilReservation();
        when(reservations.findById(9L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalArgumentException.class,
                () -> service.requestPencilRefund(9L, "other@example.com"));
        verifyNoInteractions(requests, payments);
    }

    @Test
    void onlyApprovedRefundCanBeProcessedAndPaymentsBecomeRefunded() {
        Reservation reservation = voidPencilReservation();
        RefundRequest request = mock(RefundRequest.class);
        Payment payment = new Payment(
                reservation, new BigDecimal("500.00"), "PENCIL_DOWN_PAYMENT",
                "NEXUS_SIMULATION", "PAID", "TEST-DOWN");
        when(requests.findById(3L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn("APPROVED");
        when(request.getReservation()).thenReturn(reservation);
        when(request.getAmount()).thenReturn(new BigDecimal("500.00"));
        when(payments.findAllByReservationIdOrderByPaidAtAsc(9L))
                .thenReturn(List.of(payment));
        when(requests.save(request)).thenReturn(request);

        service.processApprovedRefund(3L);

        assertEquals("REFUNDED", payment.getStatus());
        verify(payments).saveAll(List.of(payment));
        verify(request).setStatus("PROCESSED");
        verify(request).setProcessedAt(any());
        verify(requests).save(request);
    }

    @Test
    void refusesToProcessRefundTwice() {
        RefundRequest request = mock(RefundRequest.class);
        when(requests.findById(3L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn("PROCESSED");

        assertThrows(IllegalArgumentException.class,
                () -> service.processApprovedRefund(3L));
        verifyNoInteractions(payments);
    }
}
