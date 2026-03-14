package com.smartvoucher.repository;

import com.smartvoucher.entity.VoucherCustomer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherCustomerRepository extends JpaRepository<VoucherCustomer, Long> {
    boolean existsByVoucherIdAndCustomerId(Long voucherId, Long customerId);
    List<VoucherCustomer> findByVoucherId(Long voucherId);
    Page<VoucherCustomer> findByVoucherId(Long voucherId, Pageable pageable);
    Page<VoucherCustomer> findByCustomerId(Long customerId, Pageable pageable);
    Optional<VoucherCustomer> findByVoucherIdAndCustomerId(Long voucherId, Long customerId);
    void deleteByVoucherIdAndCustomerId(Long voucherId, Long customerId);
}
