package com.example.aml.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByAccountNo(String accountNo);
    Optional<Account> findByAccountNo(String accountNo);

    @Query("select ac.account from AccountCustomer ac where ac.customer.id = :customerId")
    Page<Account> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);
}
