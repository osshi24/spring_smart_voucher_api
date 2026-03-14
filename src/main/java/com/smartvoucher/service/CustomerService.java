package com.smartvoucher.service;

import com.smartvoucher.dto.request.CustomerCreateRequest;
import com.smartvoucher.dto.response.CustomerResponse;
import com.smartvoucher.dto.response.CustomerUsageResponse;
import com.smartvoucher.dto.response.CustomerVoucherResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;
    private final VoucherUsageRepository voucherUsageRepository;

    @Transactional
    public CustomerResponse create(CustomerCreateRequest req) {
        if (req.getExternalId() != null && customerRepository.existsByExternalId(req.getExternalId())) {
            throw new DuplicateResourceException("Customer with external ID already exists: " + req.getExternalId());
        }
        if (req.getEmail() != null && customerRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateResourceException("Customer with email already exists: " + req.getEmail());
        }

        Customer customer = new Customer();
        customer.setFullName(req.getFullName());
        customer.setExternalId(req.getExternalId());
        customer.setEmail(req.getEmail());
        customer.setPhone(req.getPhone());
        customer.setIsActive(true);

        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAll(Specification<Customer> spec, Pageable pageable) {
        return customerRepository.findAll(spec != null ? spec : Specification.where(null), pageable).map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return CustomerResponse.from(findById(id));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByExternalId(String externalId) {
        Customer customer = customerRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + externalId));
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerCreateRequest req) {
        Customer customer = findById(id);
        if (req.getFullName() != null) customer.setFullName(req.getFullName());
        if (req.getEmail() != null) customer.setEmail(req.getEmail());
        if (req.getPhone() != null) customer.setPhone(req.getPhone());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerVoucherResponse> getCustomerVouchers(Long customerId, String voucherCode,
                                                              String discountType, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        org.springframework.data.jpa.domain.Specification<com.smartvoucher.entity.VoucherCustomer> spec =
                (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
        if (voucherCode != null && !voucherCode.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("voucher").get("code")), "%" + voucherCode.toLowerCase() + "%"));
        }
        if (discountType != null && !discountType.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("voucher").get("discountType").as(String.class), discountType));
        }
        final Long cid = customerId;
        return voucherCustomerRepository.findAll(spec, pageable)
                .map(vc -> {
                    boolean used = voucherUsageRepository.existsByVoucherIdAndCustomerId(
                            vc.getVoucher().getId(), cid);
                    return CustomerVoucherResponse.from(vc, used);
                });
    }

    @Transactional(readOnly = true)
    public Page<CustomerUsageResponse> getCustomerUsages(Long customerId, Long voucherId,
                                                          java.time.OffsetDateTime usedAtFrom,
                                                          java.time.OffsetDateTime usedAtTo,
                                                          Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        org.springframework.data.jpa.domain.Specification<com.smartvoucher.entity.VoucherUsage> spec =
                (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
        if (voucherId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("voucher").get("id"), voucherId));
        }
        if (usedAtFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("usedAt"), usedAtFrom));
        }
        if (usedAtTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("usedAt"), usedAtTo));
        }
        return voucherUsageRepository.findAll(spec, pageable).map(CustomerUsageResponse::from);
    }

    @Transactional
    public void delete(Long id) {
        customerRepository.delete(findById(id));
    }

    private Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }
}
