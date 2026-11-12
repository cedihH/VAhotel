package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stores an individual payment transaction for a reservation.
 *
 * A reservation may have multiple payment records:
 * - ONLINE_FULL_PAYMENT
 * - PENCIL_DOWN_PAYMENT
 * - PENCIL_BALANCE_PAYMENT
 * - WALK_IN_FULL_PAYMENT
 *
 * Payments are simulated through Nexus. This entity does not connect
 * to or charge an actual bank account, card, or payment gateway.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many payment transactions may belong to one reservation.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Identifies the purpose of this individual payment transaction.
    @Column(name = "payment_type", nullable = false, length = 40)
    private String paymentType;

    // NEXUS_SIMULATION
    @Column(name = "payment_method", nullable = false, length = 40)
    private String paymentMethod = "NEXUS_SIMULATION";

    // PAID, FAILED, or REFUNDED
    @Column(nullable = false, length = 20)
    private String status = "PAID";

    @Column(name = "transaction_reference", unique = true, length = 100)
    private String transactionReference;

    // Records when this particular transaction was successfully completed.
    @Column(name = "paid_at", nullable = false)
    private Instant paidAt = Instant.now();

    protected Payment() {
    }

    /**
     * Creates a payment transaction using the current timestamp.
     * Retained as a convenient constructor for existing callers.
     */
    public Payment(
            Reservation reservation,
            BigDecimal amount,
            String paymentType,
            String paymentMethod,
            String status,
            String transactionReference) {

        this(reservation, amount, paymentType, paymentMethod,
                status, transactionReference, Instant.now());
    }

    /**
     * Creates a payment transaction with an explicit timestamp.
     * This also makes payment timing easier to test.
     */
    public Payment(
            Reservation reservation,
            BigDecimal amount,
            String paymentType,
            String paymentMethod,
            String status,
            String transactionReference,
            Instant paidAt) {

        this.reservation = reservation;
        this.amount = amount;
        this.paymentType = paymentType;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.transactionReference = transactionReference;
        this.paidAt = paidAt;
    }

    public Long getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setStatus(String value) {
        status = value;
    }
}
