package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.model.Room;
import com.mtchlvr.hotel.model.WalkInGuest;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import com.mtchlvr.hotel.repo.RoomRepository;
import com.mtchlvr.hotel.repo.WalkInGuestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Handles reservations recorded directly at the hotel by the On-Site Manager.
 *
 * Walk-in guests do not need customer accounts. Their information is stored
 * separately in WalkInGuest and linked to their reservation.
 *
 * A walk-in reservation is immediately APPROVED because the guest is
 * physically present and the On-Site Manager records the full payment.
 *
 * The reservation and its full-payment record are saved in one transaction.
 */
@Service
public class WalkInBookingService {

    private static final String PAYMENT_METHOD = "NEXUS_SIMULATION";
    private static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Manila");

    private final ReservationService reservationService;
    private final RoomRepository rooms;
    private final WalkInGuestRepository walkInGuests;
    private final ReservationRepository reservations;
    private final PaymentRepository payments;

    public WalkInBookingService(
            ReservationService reservationService,
            RoomRepository rooms,
            WalkInGuestRepository walkInGuests,
            ReservationRepository reservations,
            PaymentRepository payments) {

        this.reservationService = reservationService;
        this.rooms = rooms;
        this.walkInGuests = walkInGuests;
        this.reservations = reservations;
        this.payments = payments;
    }

    /**
     * Records a walk-in guest, reserves the selected room, and records
     * the complete reservation charge as a simulated full payment.
     *
     * No AppUser account is looked up or created.
     */
    @Transactional
    public Reservation createWalkInBooking(
            String fullName,
            String email,
            String phone,
            Long roomId,
            LocalDate checkIn,
            LocalTime checkInTime,
            LocalDate checkOut,
            LocalTime checkOutTime) {

        // Validate and normalize the guest's contact information.
        String cleanName = fullName == null ? "" : fullName.trim();
        String cleanEmail = email == null ? "" : email.trim();
        String cleanPhone = phone == null ? "" : phone.trim();

        if (cleanName.isBlank() || cleanName.length() > 150) {
            throw new IllegalArgumentException(
                    "Enter the guest's name using no more than 150 characters.");
        }

        if (cleanEmail.length() > 160) {
            throw new IllegalArgumentException(
                    "The guest email must not exceed 160 characters.");
        }

        if (cleanPhone.length() > 40) {
            throw new IllegalArgumentException(
                    "The guest phone number must not exceed 40 characters.");
        }

        if (roomId == null) {
            throw new IllegalArgumentException("Choose a room for the walk-in guest.");
        }

        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException(
                    "Enter valid check-in and check-out dates.");
        }
        // All new stays use the hotel's published 2 PM / noon schedule.
        checkInTime = LocalTime.of(14, 0);
        checkOutTime = LocalTime.NOON;

        // Retrieve the selected room. The shared availability service
        // checks whether it is active, available, and free for this interval.
        Room room = rooms.findById(roomId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Room not found."));

        if (!reservationService.isAvailable(
                room, checkIn, checkInTime, checkOut, checkOutTime)) {
            throw new IllegalArgumentException(
                    "That room is unavailable for the selected dates and times.");
        }

        // Save the guest separately; a walk-in guest has no AppUser account.
        WalkInGuest guest = walkInGuests.save(
                new WalkInGuest(
                        cleanName,
                        cleanEmail.isBlank() ? null : cleanEmail,
                        cleanPhone.isBlank() ? null : cleanPhone
                )
        );

        // A manager-recorded walk-in is approved immediately.
        Reservation reservation = new Reservation(
                guest,
                room,
                checkIn,
                checkOut,
                checkInTime,
                checkOutTime
        );

        reservation.setStatus("APPROVED");

        BigDecimal total = reservation.getTotalCost()
                .setScale(2, RoundingMode.HALF_UP);

        if (total.signum() <= 0) {
            throw new IllegalArgumentException(
                    "The reservation total must be greater than zero.");
        }

        Reservation savedReservation = reservations.save(reservation);

        // Walk-ins require full payment, not a down payment or balance.
        payments.save(new Payment(
                savedReservation,
                total,
                "WALK_IN_FULL_PAYMENT",
                PAYMENT_METHOD,
                "PAID",
                createTransactionReference()
        ));

        return savedReservation;
    }

    /**
     * Generates a unique reference for the simulated walk-in payment.
     */
    private String createTransactionReference() {
        return "NEXUS-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(java.util.Locale.ROOT);
    }
}
