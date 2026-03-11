package com.smartvoucher.service;

import com.smartvoucher.dto.request.CustomerCreateRequest;
import com.smartvoucher.dto.response.CustomerResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

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
    public Page<CustomerResponse> getAll(Pageable pageable) {
        return customerRepository.findAll(pageable).map(CustomerResponse::from);
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

    @Transactional
    public void delete(Long id) {
        customerRepository.delete(findById(id));
    }

    private Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }
}
