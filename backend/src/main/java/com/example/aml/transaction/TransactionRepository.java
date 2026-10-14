package com.example.aml.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<AmlTransaction, UUID> {
    boolean existsBySourceAndExternalId(String source, String externalId);
    Page<AmlTransaction> findByAccount_Id(UUID accountId, Pageable pageable);

    @Query("select distinct t from AmlTransaction t join AccountCustomer ac on ac.account = t.account " +
            "where ac.customer.id = :customerId")
    Page<AmlTransaction> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);
}
