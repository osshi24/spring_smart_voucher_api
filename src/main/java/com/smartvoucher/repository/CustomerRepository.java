package com.smartvoucher.repository;

import com.smartvoucher.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByExternalId(String externalId);
    boolean existsByExternalId(String externalId);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}
