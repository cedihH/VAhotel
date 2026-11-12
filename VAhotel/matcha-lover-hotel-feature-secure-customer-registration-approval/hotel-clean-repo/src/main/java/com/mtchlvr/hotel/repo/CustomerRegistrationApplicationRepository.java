package com.mtchlvr.hotel.repo;

import com.mtchlvr.hotel.model.CustomerRegistrationApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerRegistrationApplicationRepository extends JpaRepository<CustomerRegistrationApplication, Long> {
    Optional<CustomerRegistrationApplication> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<CustomerRegistrationApplication> findAllByStatusOrderByCreatedAtAsc(String status);
}
