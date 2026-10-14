package com.example.aml.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    boolean existsByCustomerNo(String customerNo);
    Optional<Customer> findByCustomerNo(String customerNo);
    Page<Customer> findByCustomerNoContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String customerNo, String fullName, Pageable pageable);
}
