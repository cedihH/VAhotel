package com.mtchlvr.hotel.repo;

import com.mtchlvr.hotel.model.RefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Optional<RefundRequest> findByReservationId(Long reservationId);
    List<RefundRequest> findAllByOrderByCreatedAtDesc();
}
