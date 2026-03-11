package com.smartvoucher.repository;

import com.smartvoucher.entity.VoucherCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherCustomerRepository extends JpaRepository<VoucherCustomer, Long> {
    boolean existsByVoucherIdAndCustomerId(Long voucherId, Long customerId);
    List<VoucherCustomer> findByVoucherId(Long voucherId);
    void deleteByVoucherIdAndCustomerId(Long voucherId, Long customerId);
}
