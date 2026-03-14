package com.smartvoucher.service;

import com.smartvoucher.dto.response.VoucherCustomerResponse;
import com.smartvoucher.dto.response.VoucherUsageResponse;
import com.smartvoucher.entity.VoucherCustomer;
import com.smartvoucher.entity.VoucherUsage;
import com.smartvoucher.exception.ConflictException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class VoucherAssignmentService {

    private final VoucherRepository voucherRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;
    private final VoucherUsageRepository voucherUsageRepository;

    @Transactional(readOnly = true)
    public Page<VoucherCustomerResponse> getVoucherCustomers(Long voucherId, String customerName,
                                                              String customerEmail, Pageable pageable) {
        if (!voucherRepository.existsById(voucherId)) {
            throw new ResourceNotFoundException("Voucher not found: " + voucherId);
        }
        Specification<VoucherCustomer> spec = (root, query, cb) ->
                cb.equal(root.get("voucher").get("id"), voucherId);

        if (customerName != null && !customerName.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("customer").get("fullName")), "%" + customerName.toLowerCase() + "%"));
        }
        if (customerEmail != null && !customerEmail.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("customer").get("email")), "%" + customerEmail.toLowerCase() + "%"));
        }
        return voucherCustomerRepository.findAll(spec, pageable).map(VoucherCustomerResponse::from);
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
    public Page<VoucherUsageResponse> getVoucherUsages(Long voucherId, Long customerId,
                                                        String externalOrderId,
                                                        OffsetDateTime usedAtFrom, OffsetDateTime usedAtTo,
                                                        Pageable pageable) {
        if (!voucherRepository.existsById(voucherId)) {
            throw new ResourceNotFoundException("Voucher not found: " + voucherId);
        }
        Specification<VoucherUsage> spec = (root, query, cb) ->
                cb.equal(root.get("voucher").get("id"), voucherId);

        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        }
        if (externalOrderId != null && !externalOrderId.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(root.get("externalOrderId"), "%" + externalOrderId + "%"));
        }
        if (usedAtFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("usedAt"), usedAtFrom));
        }
        if (usedAtTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("usedAt"), usedAtTo));
        }
        return voucherUsageRepository.findAll(spec, pageable).map(VoucherUsageResponse::from);
    }
}
