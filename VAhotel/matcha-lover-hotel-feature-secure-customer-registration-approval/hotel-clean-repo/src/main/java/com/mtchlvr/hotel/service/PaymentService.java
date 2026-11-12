package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Handles simulated Nexus payments for online hotel reservations.
 *
 * Regular online reservations require full payment.
 * Pencil Bookings require a 50% down payment and a 50% balance payment.
 *
 * All reservation and payment changes are transactional so a failed
 * payment-record operation cannot leave an unpaid reservation behind.
 *
 * This service does not connect to a real bank, card, or payment gateway.
 */
@Service
public class PaymentService {

    private static final String PAYMENT_METHOD = "NEXUS_SIMULATION";

    private final ReservationService reservationService;
    private final ReservationRepository reservations;
    private final PaymentRepository payments;

    public PaymentService(
            ReservationService reservationService,
            ReservationRepository reservations,
            PaymentRepository payments) {

        this.reservationService = reservationService;
        this.reservations = reservations;
        this.payments = payments;
    }

    /**
     * Creates a regular online reservation after simulated full payment.
     * The reservation remains PENDING for Online Manager approval.
     */
    @Transactional
    public Reservation createRegularOnlineBooking(
            String email,
            Long roomId,
            java.time.LocalDate checkIn,
            java.time.LocalTime checkInTime,
            java.time.LocalDate checkOut,
            java.time.LocalTime checkOutTime) {

        Reservation reservation = reservationService.reserve(
                email, roomId, checkIn, checkInTime, checkOut, checkOutTime);

        BigDecimal total = money(reservation.getTotalCost());

        if (total.signum() <= 0) {
            throw new IllegalArgumentException("The reservation total must be greater than zero.");
        }

        reservation.setBookingType("ONLINE_REGULAR");
        reservations.save(reservation);

        payments.save(new Payment(
                reservation,
                total,
                "ONLINE_FULL_PAYMENT",
                PAYMENT_METHOD,
                "PAID",
                createTransactionReference()
        ));

        return reservation;
    }

    /**
     * Creates a Pencil Booking after its simulated 50% down payment.
     *
     * The 72-hour manager review period starts from the successful
     * payment timestamp stored in the Payment record.
     */
    @Transactional
    public Reservation createPencilBooking(
            String email,
            Long roomId,
            java.time.LocalDate checkIn,
            java.time.LocalTime checkInTime,
            java.time.LocalDate checkOut,
            java.time.LocalTime checkOutTime) {

        Reservation reservation = reservationService.reserve(
                email, roomId, checkIn, checkInTime, checkOut, checkOutTime);

        BigDecimal total = money(reservation.getTotalCost());

        if (total.signum() <= 0) {
            throw new IllegalArgumentException("The reservation total must be greater than zero.");
        }

        BigDecimal downPayment = total
                .multiply(new BigDecimal("0.50"))
                .setScale(2, RoundingMode.HALF_UP);

        if (downPayment.signum() <= 0) {
            throw new IllegalArgumentException("The Pencil Booking down payment must be greater than zero.");
        }

        reservation.setBookingType("PENCIL");
        reservation.setStatus("PENCIL_PENDING_REVIEW");
        reservations.save(reservation);

        payments.save(new Payment(
                reservation,
                downPayment,
                "PENCIL_DOWN_PAYMENT",
                PAYMENT_METHOD,
                "PAID",
                createTransactionReference()
        ));

        return reservation;
    }

    /**
     * Records the remaining Pencil Booking balance.
     *
     * Only the reservation's registered customer can pay.
     * Payment is allowed only after manager approval and before check-in.
     */
    @Transactional
    public Payment payPencilBalance(Long reservationId, String customerEmail) {

        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found."));

        if (reservation.getCustomer() == null ||
                !reservation.getCustomer().getEmail().equalsIgnoreCase(customerEmail)) {
            throw new IllegalArgumentException("You are not authorized to pay for this reservation.");
        }

        if (!"PENCIL".equals(reservation.getBookingType())) {
            throw new IllegalArgumentException("This reservation is not a Pencil Booking.");
        }

        if (!"APPROVED".equals(reservation.getStatus())) {
            throw new IllegalArgumentException("The Pencil Booking must be approved before paying its balance.");
        }
        if (reservation.getCheckIn() == null) {
            throw new IllegalArgumentException("This reservation has no valid check-in date.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila")).withNano(0);
        java.time.LocalTime checkInTime = reservation.getCheckInTime() == null
                ? java.time.LocalTime.of(14, 0)
                : reservation.getCheckInTime();

        if (!now.isBefore(reservation.getCheckIn().atTime(checkInTime))) {
            throw new IllegalArgumentException("The remaining balance must be paid before check-in.");
        }

        if (payments.findByReservationIdAndPaymentTypeAndStatus(
                reservationId, "PENCIL_BALANCE_PAYMENT", "PAID").isPresent()) {
            throw new IllegalArgumentException("The Pencil Booking balance has already been paid.");
        }

        List<Payment> paymentRecords =
                payments.findAllByReservationIdOrderByPaidAtAsc(reservationId);

        // A valid Pencil Booking must have exactly one successful down payment.
        // This prevents missing, duplicate, or incorrectly typed down-payment records.
        List<Payment> downPayments = paymentRecords.stream()
                .filter(payment -> "PAID".equals(payment.getStatus()))
                .filter(payment -> "PENCIL_DOWN_PAYMENT".equals(payment.getPaymentType()))
                .toList();

        if (downPayments.size() != 1) {
            throw new IllegalArgumentException(
                    "A valid Pencil Booking down payment must be recorded before paying the balance.");
        }

        BigDecimal total = money(reservation.getTotalCost());
        BigDecimal expectedDownPayment = money(
                total.multiply(new BigDecimal("0.50")));

        BigDecimal actualDownPayment = money(downPayments.get(0).getAmount());

        if (actualDownPayment.compareTo(expectedDownPayment) != 0) {
            throw new IllegalArgumentException(
                    "The recorded Pencil Booking down payment is incorrect.");
        }

        // Count every successful transaction to prevent the customer from
        // paying more than the reservation's total charge.
        BigDecimal paidAmount = BigDecimal.ZERO;

        for (Payment payment : paymentRecords) {
            if ("PAID".equals(payment.getStatus())) {
                if (payment.getAmount() == null) {
                    throw new IllegalArgumentException(
                            "A recorded payment has no valid amount.");
                }

                paidAmount = paidAmount.add(payment.getAmount());
            }
        }

        paidAmount = money(paidAmount);

        if (paidAmount.compareTo(total) > 0) {
            throw new IllegalArgumentException(
                    "Recorded payments already exceed the reservation total.");
        }

        BigDecimal remainingBalance = money(total.subtract(paidAmount));

        if (remainingBalance.signum() <= 0) {
            throw new IllegalArgumentException("There is no remaining balance to pay.");
        }

        return payments.save(new Payment(
                reservation,
                remainingBalance,
                "PENCIL_BALANCE_PAYMENT",
                PAYMENT_METHOD,
                "PAID",
                createTransactionReference()
        ));
    }

    /**
     * Uses two decimal places for all stored payment amounts.
     */
    private BigDecimal money(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Reservation total is unavailable.");
        }

        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Generates a unique reference for each simulated transaction.
     */
    private String createTransactionReference() {
        return "NEXUS-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(java.util.Locale.ROOT);
    }
}
