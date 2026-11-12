package com.mtchlvr.hotel.repo;

import com.mtchlvr.hotel.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Retrieves every payment transaction belonging to one reservation.
    List<Payment> findAllByReservationIdOrderByPaidAtAsc(Long reservationId);

    // Prevents reuse of an existing transaction reference.
    Optional<Payment> findByTransactionReference(String transactionReference);

    // Checks whether a payment of a particular type was already completed.
    Optional<Payment> findByReservationIdAndPaymentTypeAndStatus(
            Long reservationId, String paymentType, String status);
}
