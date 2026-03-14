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
    public Page<CustomerVoucherResponse> getCustomerVouchers(Long customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        return voucherCustomerRepository.findByCustomerId(customerId, pageable)
                .map(vc -> {
                    boolean used = voucherUsageRepository.existsByVoucherIdAndCustomerId(
                            vc.getVoucher().getId(), customerId);
                    return CustomerVoucherResponse.from(vc, used);
                });
    }

    @Transactional(readOnly = true)
    public Page<CustomerUsageResponse> getCustomerUsages(Long customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        return voucherUsageRepository.findByCustomerId(customerId, pageable)
                .map(CustomerUsageResponse::from);
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
