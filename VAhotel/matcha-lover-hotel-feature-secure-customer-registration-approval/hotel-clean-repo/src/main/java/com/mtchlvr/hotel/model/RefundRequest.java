package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tracks a customer's refund request for a Pencil Booking voided because
 * the Online Manager did not review it before the scheduled check-in.
 *
 * Status lifecycle: REQUESTED -> APPROVED -> PROCESSED, or REQUESTED -> REJECTED.
 * Processing is a local Nexus simulation; it does not contact a real gateway.
 */
@Entity
@Table(name = "refund_requests",
       uniqueConstraints = @UniqueConstraint(name = "uk_refund_request_reservation",
               columnNames = "reservation_id"))
public class RefundRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status = "REQUESTED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "manager_note", length = 500)
    private String managerNote;

    protected RefundRequest() {}

    public RefundRequest(Reservation reservation, BigDecimal amount) {
        this.reservation = reservation;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public String getManagerNote() { return managerNote; }

    public void setStatus(String value) { status = value; }
    public void setReviewedAt(Instant value) { reviewedAt = value; }
    public void setProcessedAt(Instant value) { processedAt = value; }
    public void setManagerNote(String value) { managerNote = value; }
}
