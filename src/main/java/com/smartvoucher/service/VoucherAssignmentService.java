package com.smartvoucher.service;

import com.smartvoucher.dto.response.VoucherCustomerResponse;
import com.smartvoucher.dto.response.VoucherUsageResponse;
import com.smartvoucher.entity.VoucherCustomer;
import com.smartvoucher.exception.ConflictException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoucherAssignmentService {

    private final VoucherRepository voucherRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;
    private final VoucherUsageRepository voucherUsageRepository;

    @Transactional(readOnly = true)
    public Page<VoucherCustomerResponse> getVoucherCustomers(Long voucherId, Pageable pageable) {
        if (!voucherRepository.existsById(voucherId)) {
            throw new ResourceNotFoundException("Voucher not found: " + voucherId);
        }
        return voucherCustomerRepository.findByVoucherId(voucherId, pageable)
                .map(VoucherCustomerResponse::from);
    }

    @Transactional
    public void revokeAssignment(Long voucherId, Long customerId) {
        VoucherCustomer assignment = voucherCustomerRepository
                .findByVoucherIdAndCustomerId(voucherId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found for voucher " + voucherId + " and customer " + customerId));

        if (voucherUsageRepository.existsByVoucherIdAndCustomerId(voucherId, customerId)) {
            throw new ConflictException("Cannot revoke: customer has already used this voucher.");
        }

        voucherCustomerRepository.delete(assignment);
    }

    @Transactional(readOnly = true)
    public Page<VoucherUsageResponse> getVoucherUsages(Long voucherId, Pageable pageable) {
        if (!voucherRepository.existsById(voucherId)) {
            throw new ResourceNotFoundException("Voucher not found: " + voucherId);
        }
        return voucherUsageRepository.findByVoucherId(voucherId, pageable)
                .map(VoucherUsageResponse::from);
    }
}
