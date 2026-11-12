package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;

/**
 * Stores a hotel reservation.
 *
 * A reservation belongs to either:
 * 1. A registered online customer (AppUser), or
 * 2. A walk-in guest recorded by the On-Site Manager.
 *
 * ReservationService must enforce that exactly one guest relationship
 * is assigned before a reservation is saved.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Registered customer for online reservations.
    @ManyToOne(optional = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private AppUser customer;

    // Guest information for reservations recorded at the hotel.
    @ManyToOne(optional = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "walk_in_guest_id")
    private WalkInGuest walkInGuest;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Room room;

    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut;

    // Nullable so existing reservations without explicit times remain valid.
    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    // Identifies the booking workflow even after its status changes.
    // ONLINE_REGULAR, PENCIL, or WALK_IN.
    // Nullable for compatibility with older database records.
    @Column(name = "booking_type", length = 30)
    private String bookingType = "ONLINE_REGULAR";

    @Column(nullable = false)
    private String status = "PENDING";

    // Snapshot the rate so later room price edits do not rewrite past bills.
    @Column(name = "nightly_rate_at_booking", precision = 12, scale = 2)
    private java.math.BigDecimal nightlyRateAtBooking;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "stay_request_type", length = 20)
    private String stayRequestType;
    @Column(name = "stay_request_date")
    private LocalDate stayRequestDate;
    @Column(name = "stay_request_status", length = 20)
    private String stayRequestStatus;

    @Column(name = "status_before_cancellation")
    private String statusBeforeCancellation;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Soft-hide a processed record without deleting its reservation.
    @Column
    private Boolean historyVisible = true;

    protected Reservation() {
    }

    /**
     * A reservation must belong to exactly one guest identity.
     * Online bookings use AppUser; walk-ins use WalkInGuest.
     */
    @PrePersist
    @PreUpdate
    private void validateGuestRelationship() {
        if (nightlyRateAtBooking == null && room != null) nightlyRateAtBooking = room.getNightlyRate();
        boolean hasCustomer = customer != null;
        boolean hasWalkInGuest = walkInGuest != null;
        if (hasCustomer == hasWalkInGuest) {
            throw new IllegalStateException(
                    "A reservation must have exactly one registered customer or walk-in guest.");
        }
    }

    /**
     * Creates a reservation for a registered online customer.
     * Retained for compatibility with existing callers and tests.
     */
    public Reservation(
            AppUser customer,
            Room room,
            LocalDate checkIn,
            LocalDate checkOut) {

        this(customer, room, checkIn, checkOut,
                LocalTime.of(14, 0), LocalTime.NOON);
    }

    /**
     * Creates a timed reservation for a registered online customer.
     */
    public Reservation(
            AppUser customer,
            Room room,
            LocalDate checkIn,
            LocalDate checkOut,
            LocalTime checkInTime,
            LocalTime checkOutTime) {

        this.customer = customer;
        this.room = room;
        this.nightlyRateAtBooking = room == null ? null : room.getNightlyRate();
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
    }

    /**
     * Creates a reservation for a walk-in guest.
     * The On-Site Manager records the guest without creating an AppUser account.
     */
    public Reservation(
            WalkInGuest walkInGuest,
            Room room,
            LocalDate checkIn,
            LocalDate checkOut,
            LocalTime checkInTime,
            LocalTime checkOutTime) {

        this.walkInGuest = walkInGuest;
        this.bookingType = "WALK_IN";
        this.room = room;
        this.nightlyRateAtBooking = room == null ? null : room.getNightlyRate();
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
    }

    public Long getId() {
        return id;
    }

    public AppUser getCustomer() {
        return customer;
    }

    public WalkInGuest getWalkInGuest() {
        return walkInGuest;
    }

    public Room getRoom() {
        return room;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(LocalDate value) {
        checkOut = value;
    }

    public LocalTime getCheckInTime() {
        return checkInTime;
    }

    public LocalTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckInTime(LocalTime value) {
        checkInTime = value;
    }

    public void setCheckOutTime(LocalTime value) {
        checkOutTime = value;
    }

    public long getNights() {
        long dateNights =
                java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);

        if (dateNights > 0) {
            return dateNights;
        }

        if (dateNights < 0) {
            return 0;
        }

        // Same-day stays cost at least one nightly rate
        // when checkout is later than check-in.
        LocalTime start = checkInTime == null
                ? LocalTime.of(14, 0) : checkInTime;

        LocalTime end = checkOutTime == null
                ? LocalTime.NOON : checkOutTime;

        return end.isAfter(start) ? 1 : 0;
    }

    public java.math.BigDecimal getTotalCost() {
        java.math.BigDecimal rate = nightlyRateAtBooking == null ? room.getNightlyRate() : nightlyRateAtBooking;
        return rate
                .multiply(java.math.BigDecimal.valueOf(getNights()));
    }
    public java.math.BigDecimal getNightlyRateAtBooking() {
        return nightlyRateAtBooking == null ? room.getNightlyRate() : nightlyRateAtBooking;
    }
    public void setNightlyRateAtBooking(java.math.BigDecimal value) { nightlyRateAtBooking = value; }
    public Instant getCheckedOutAt() { return checkedOutAt; }
    public void setCheckedOutAt(Instant value) { checkedOutAt = value; }
    public String getStayRequestType() { return stayRequestType; }
    public LocalDate getStayRequestDate() { return stayRequestDate; }
    public String getStayRequestStatus() { return stayRequestStatus; }
    public void setStayRequest(String type, LocalDate date) { stayRequestType = type; stayRequestDate = date; stayRequestStatus = "PENDING"; }
    public void setStayRequestStatus(String status) { stayRequestStatus = status; }

    public String getBookingType() {
        // Older records without this field are treated as regular online bookings.
        return bookingType == null ? "ONLINE_REGULAR" : bookingType;
    }

    public void setBookingType(String value) {
        bookingType = value;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusBeforeCancellation() {
        return statusBeforeCancellation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isHistoryVisible() {
        return historyVisible == null || historyVisible;
    }

    public void setHistoryVisible(boolean visible) {
        historyVisible = visible;
    }

    public void setStatus(String value) {
        status = value;
    }

    public void setStatusBeforeCancellation(String value) {
        statusBeforeCancellation = value;
    }
}
