package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.AppUser;
import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private ReservationRepository reservations;

    @Mock
    private PaymentRepository payments;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(reservationService, reservations, payments);
    }

    @Test
    void regularOnlineBookingRecordsFullPayment() {
        Reservation reservation = mock(Reservation.class);

        when(reservationService.reserve(
                anyString(), anyLong(), any(), any(), any(), any()))
                .thenReturn(reservation);
        lenient().when(reservation.getTotalCost()).thenReturn(new BigDecimal("5000.00"));
        when(reservations.save(reservation)).thenReturn(reservation);

        service.createRegularOnlineBooking(
                "guest@example.com", 1L,
                LocalDate.now().plusDays(2), LocalTime.of(14, 0),
                LocalDate.now().plusDays(4), LocalTime.NOON
        );

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());

        Payment payment = captor.getValue();
        assertEquals(new BigDecimal("5000.00"), payment.getAmount());
        assertEquals("ONLINE_FULL_PAYMENT", payment.getPaymentType());
        assertEquals("PAID", payment.getStatus());
        assertEquals("NEXUS_SIMULATION", payment.getPaymentMethod());
        assertNotNull(payment.getTransactionReference());
        verify(reservation).setBookingType("ONLINE_REGULAR");
    }

    @Test
    void pencilBookingRecordsExactlyFiftyPercentDownPayment() {
        Reservation reservation = mock(Reservation.class);

        when(reservationService.reserve(
                anyString(), anyLong(), any(), any(), any(), any()))
                .thenReturn(reservation);
        when(reservation.getTotalCost()).thenReturn(new BigDecimal("5000.00"));
        when(reservations.save(reservation)).thenReturn(reservation);

        service.createPencilBooking(
                "guest@example.com", 1L,
                LocalDate.now().plusDays(5), LocalTime.of(14, 0),
                LocalDate.now().plusDays(7), LocalTime.NOON
        );

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());

        Payment payment = captor.getValue();
        assertEquals(new BigDecimal("2500.00"), payment.getAmount());
        assertEquals("PENCIL_DOWN_PAYMENT", payment.getPaymentType());
        assertEquals("PAID", payment.getStatus());
        verify(reservation).setBookingType("PENCIL");
        verify(reservation).setStatus("PENCIL_PENDING_REVIEW");
    }

    @Test
    void approvedPencilBookingCanPayRemainingBalance() {
        Reservation reservation = approvedPencilReservation();
        Payment downPayment = paidDownPayment(reservation, "2500.00");

        when(reservations.findById(10L)).thenReturn(Optional.of(reservation));
        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                10L, "PENCIL_BALANCE_PAYMENT", "PAID"))
                .thenReturn(Optional.empty());
        when(payments.findAllByReservationIdOrderByPaidAtAsc(10L))
                .thenReturn(List.of(downPayment));
        when(payments.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = service.payPencilBalance(10L, "guest@example.com");

        assertEquals(new BigDecimal("2500.00"), result.getAmount());
        assertEquals("PENCIL_BALANCE_PAYMENT", result.getPaymentType());
        assertEquals("PAID", result.getStatus());
        assertNotNull(result.getTransactionReference());
    }

    @Test
    void rejectsPencilBalanceWhenDownPaymentIsMissing() {
        Reservation reservation = approvedPencilReservation();

        when(reservations.findById(10L)).thenReturn(Optional.of(reservation));
        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                10L, "PENCIL_BALANCE_PAYMENT", "PAID"))
                .thenReturn(Optional.empty());
        when(payments.findAllByReservationIdOrderByPaidAtAsc(10L))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.payPencilBalance(10L, "guest@example.com"));

        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void rejectsIncorrectPencilDownPaymentAmount() {
        Reservation reservation = approvedPencilReservation();
        Payment incorrectPayment = paidDownPayment(reservation, "2000.00");

        when(reservations.findById(10L)).thenReturn(Optional.of(reservation));
        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                10L, "PENCIL_BALANCE_PAYMENT", "PAID"))
                .thenReturn(Optional.empty());
        when(payments.findAllByReservationIdOrderByPaidAtAsc(10L))
                .thenReturn(List.of(incorrectPayment));

        assertThrows(IllegalArgumentException.class,
                () -> service.payPencilBalance(10L, "guest@example.com"));

        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void rejectsDuplicatePencilBalancePayment() {
        Reservation reservation = approvedPencilReservation();

        when(reservations.findById(10L)).thenReturn(Optional.of(reservation));
        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                10L, "PENCIL_BALANCE_PAYMENT", "PAID"))
                .thenReturn(Optional.of(mock(Payment.class)));

        assertThrows(IllegalArgumentException.class,
                () -> service.payPencilBalance(10L, "guest@example.com"));

        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void rejectsPaymentsThatAlreadyExceedReservationTotal() {
        Reservation reservation = approvedPencilReservation();

        Payment downPayment = paidDownPayment(reservation, "2500.00");
        Payment extraPayment = new Payment(
                reservation,
                new BigDecimal("3000.00"),
                "OTHER_PAYMENT",
                "NEXUS_SIMULATION",
                "PAID",
                "TEST-EXTRA-PAYMENT"
        );

        when(reservations.findById(10L)).thenReturn(Optional.of(reservation));
        when(payments.findByReservationIdAndPaymentTypeAndStatus(
                10L, "PENCIL_BALANCE_PAYMENT", "PAID"))
                .thenReturn(Optional.empty());
        when(payments.findAllByReservationIdOrderByPaidAtAsc(10L))
                .thenReturn(List.of(downPayment, extraPayment));

        assertThrows(IllegalArgumentException.class,
                () -> service.payPencilBalance(10L, "guest@example.com"));

        verify(payments, never()).save(any(Payment.class));
    }

    private Reservation approvedPencilReservation() {
        AppUser customer = new AppUser(
                "guest@example.com",
                "password-hash",
                "Test Guest",
                "CUSTOMER",
                true
        );

        Reservation reservation = mock(Reservation.class);

        when(reservation.getCustomer()).thenReturn(customer);
        when(reservation.getBookingType()).thenReturn("PENCIL");
        when(reservation.getStatus()).thenReturn("APPROVED");
        when(reservation.getCheckIn()).thenReturn(LocalDate.now().plusDays(2));
        when(reservation.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        lenient().when(reservation.getTotalCost()).thenReturn(new BigDecimal("5000.00"));

        return reservation;
    }

    private Payment paidDownPayment(Reservation reservation, String amount) {
        return new Payment(
                reservation,
                new BigDecimal(amount),
                "PENCIL_DOWN_PAYMENT",
                "NEXUS_SIMULATION",
                "PAID",
                "TEST-DOWN-PAYMENT"
        );
    }
}
