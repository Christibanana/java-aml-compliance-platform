package com.example.aml.account;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountCustomerRepository extends JpaRepository<AccountCustomer, AccountCustomerId> {
    @EntityGraph(attributePaths = "customer")
    List<AccountCustomer> findByAccount_IdIn(Collection<UUID> accountIds);

    @EntityGraph(attributePaths = "customer")
    List<AccountCustomer> findByAccount_Id(UUID accountId);
}
