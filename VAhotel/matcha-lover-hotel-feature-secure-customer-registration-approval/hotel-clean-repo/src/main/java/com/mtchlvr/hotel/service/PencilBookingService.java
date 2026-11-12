package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Handles the Online Manager's review of Pencil Bookings.
 *
 * Pencil Bookings use a separate review workflow from regular online
 * reservations. This prevents their special payment and deadline rules
 * from changing the existing regular reservation approval process.
 */
@Service
public class PencilBookingService {

    // The Online Manager has exactly 72 hours after successful down payment.
    private static final Duration REVIEW_PERIOD = Duration.ofHours(72);

    private static final String PENCIL_BOOKING = "PENCIL";
    private static final String PENDING_REVIEW = "PENCIL_PENDING_REVIEW";
    private static final String DOWN_PAYMENT_TYPE = "PENCIL_DOWN_PAYMENT";

    private final ReservationRepository reservations;
    private final PaymentRepository payments;

    public PencilBookingService(
            ReservationRepository reservations,
            PaymentRepository payments) {

        this.reservations = reservations;
        this.payments = payments;
    }

    /**
     * Approves or rejects a Pencil Booking.
     *
     * The review deadline is calculated from the successful down-payment
     * timestamp, not from the time the customer submitted the booking.
     *
     * @param reservationId reservation being reviewed
     * @param decision      APPROVED or REJECTED
     * @return the updated reservation
     */
    @Transactional
    public Reservation reviewBooking(Long reservationId, String decision) {

        // Reject unsupported decisions before changing any reservation.
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException(
                    "Pencil Bookings can only be approved or rejected.");
        }

        // Find the reservation that the manager selected.
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pencil Booking was not found."));

        // This workflow must never modify regular or walk-in reservations.
        if (!PENCIL_BOOKING.equals(reservation.getBookingType())) {
            throw new IllegalArgumentException(
                    "This reservation is not a Pencil Booking.");
        }

        // Only a Pencil Booking awaiting its first review can be processed.
        if (!PENDING_REVIEW.equals(reservation.getStatus())) {
            throw new IllegalArgumentException(
                    "This Pencil Booking is no longer awaiting review.");
        }
        if (reservation.getCheckIn() == null || reservation.getCheckOut() == null ||
                !reservation.getCheckOut().isAfter(reservation.getCheckIn())) {
            throw new IllegalArgumentException(
                    "This Pencil Booking has an invalid stay interval.");
        }

        // A successful down payment is required before the review period
        // can be calculated or the reservation can be approved.
        Payment downPayment = payments
                .findByReservationIdAndPaymentTypeAndStatus(
                        reservationId, DOWN_PAYMENT_TYPE, "PAID")
                .orElseThrow(() -> new IllegalArgumentException(
                        "A successful Pencil Booking down payment was not found."));

        if (downPayment.getPaidAt() == null || downPayment.getAmount() == null) {
            throw new IllegalArgumentException(
                    "The Pencil Booking payment record is incomplete.");
        }

        // Verify that the recorded successful payment is exactly 50%
        // of the reservation's total charge.
        BigDecimal expectedDownPayment = reservation.getTotalCost()
                .multiply(new BigDecimal("0.50"))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actualDownPayment = downPayment.getAmount()
                .setScale(2, RoundingMode.HALF_UP);

        if (actualDownPayment.compareTo(expectedDownPayment) != 0) {
            throw new IllegalArgumentException(
                    "The Pencil Booking down payment does not match 50% of the reservation total.");
        }

        // A manager cannot approve or reject a booking once its scheduled
        // check-in has arrived, even if the 60-second scheduler has not yet
        // changed its status to VOID.
        LocalTime scheduledCheckIn = reservation.getCheckInTime() == null
                ? LocalTime.of(14, 0)
                : reservation.getCheckInTime();
        LocalDateTime checkInDateTime = reservation.getCheckIn().atTime(scheduledCheckIn);
        LocalDateTime nowInHotelZone = LocalDateTime.now(ZoneId.of("Asia/Manila"))
                .withNano(0);
        if (!nowInHotelZone.isBefore(checkInDateTime)) {
            throw new IllegalArgumentException(
                    "This Pencil Booking can no longer be reviewed because its check-in time has arrived.");
        }

        // The 72-hour review period starts when the payment succeeded.
        Instant reviewDeadline = downPayment.getPaidAt().plus(REVIEW_PERIOD);

        // A decision at or after the deadline is no longer permitted.
        if (!Instant.now().isBefore(reviewDeadline)) {
            throw new IllegalArgumentException(
                    "The 72-hour Pencil Booking review deadline has expired.");
        }

        // Save only the permitted manager decision.
        reservation.setStatus(decision);

        return reservations.save(reservation);
    }
}
