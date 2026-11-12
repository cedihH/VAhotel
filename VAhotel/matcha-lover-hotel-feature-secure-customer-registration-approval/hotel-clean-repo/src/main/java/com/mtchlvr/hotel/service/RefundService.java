package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.RefundRequest;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.RefundRequestRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Handles customer-requested refunds for Pencil Bookings voided at check-in
 * because the Online Manager did not make a decision within the review period.
 * All refund execution is simulated locally; no real payment gateway is called.
 */
@Service
public class RefundService {
    private final ReservationRepository reservations;
    private final PaymentRepository payments;
    private final RefundRequestRepository refundRequests;

    public RefundService(ReservationRepository reservations,
                         PaymentRepository payments,
                         RefundRequestRepository refundRequests) {
        this.reservations = reservations;
        this.payments = payments;
        this.refundRequests = refundRequests;
    }

    @Transactional
    public RefundRequest requestPencilRefund(Long reservationId, String customerEmail) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getCustomer() == null
                || !reservation.getCustomer().getEmail().equalsIgnoreCase(customerEmail)) {
            throw new IllegalArgumentException("You are not authorized to request this refund.");
        }
        if (!"PENCIL".equals(reservation.getBookingType())
                || !"VOID".equals(reservation.getStatus())) {
            throw new IllegalArgumentException(
                    "Refund requests are available only for Pencil Bookings voided at check-in.");
        }
        if (refundRequests.findByReservationId(reservationId).isPresent()) {
            throw new IllegalArgumentException(
                    "A refund request already exists for this Pencil Booking.");
        }

        List<Payment> paidPayments = payments
                .findAllByReservationIdOrderByPaidAtAsc(reservationId).stream()
                .filter(p -> "PAID".equals(p.getStatus()))
                .toList();
        if (paidPayments.isEmpty()) {
            throw new IllegalArgumentException("No successful payment is available to refund.");
        }

        BigDecimal refundable = BigDecimal.ZERO;
        for (Payment payment : paidPayments) {
            if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
                throw new IllegalArgumentException("A payment record has an invalid refund amount.");
            }
            refundable = refundable.add(payment.getAmount());
        }
        refundable = refundable.setScale(2, RoundingMode.HALF_UP);
        return refundRequests.save(new RefundRequest(reservation, refundable));
    }

    @Transactional
    public RefundRequest reviewRefundRequest(Long requestId, String decision, String managerNote) {
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("Choose APPROVED or REJECTED.");
        }
        RefundRequest request = refundRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found."));
        if (!"REQUESTED".equals(request.getStatus())) {
            throw new IllegalArgumentException("This refund request has already been reviewed.");
        }
        String cleanNote = managerNote == null ? "" : managerNote.trim();
        if (cleanNote.length() > 500) {
            throw new IllegalArgumentException("Manager note must not exceed 500 characters.");
        }
        request.setStatus(decision);
        request.setReviewedAt(Instant.now());
        request.setManagerNote(cleanNote.isBlank() ? null : cleanNote);
        return refundRequests.save(request);
    }

    /**
     * Completes an approved refund exactly once by changing all eligible
     * local payment records from PAID to REFUNDED in the same transaction.
     */
    @Transactional
    public RefundRequest processApprovedRefund(Long requestId) {
        RefundRequest request = refundRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found."));
        if (!"APPROVED".equals(request.getStatus())) {
            throw new IllegalArgumentException("Only an approved refund can be processed.");
        }

        List<Payment> paidPayments = payments
                .findAllByReservationIdOrderByPaidAtAsc(request.getReservation().getId()).stream()
                .filter(p -> "PAID".equals(p.getStatus()))
                .toList();
        BigDecimal paidTotal = BigDecimal.ZERO;
        for (Payment payment : paidPayments) {
            if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
                throw new IllegalArgumentException("A payment record has an invalid refund amount.");
            }
            paidTotal = paidTotal.add(payment.getAmount());
        }
        paidTotal = paidTotal.setScale(2, RoundingMode.HALF_UP);
        if (paidPayments.isEmpty() || paidTotal.compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "The successful payment total no longer matches the approved refund amount.");
        }

        paidPayments.forEach(payment -> payment.setStatus("REFUNDED"));
        payments.saveAll(paidPayments);
        request.setStatus("PROCESSED");
        request.setProcessedAt(Instant.now());
        return refundRequests.save(request);
    }
}
