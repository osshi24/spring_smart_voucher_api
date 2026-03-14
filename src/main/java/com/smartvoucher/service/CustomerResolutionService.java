package com.smartvoucher.service;

import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.User;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a flexible customerRef (email / phone / externalId / internal ID)
 * to a Customer entity, optionally auto-creating if not found.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerResolutionService {

    private final CustomerRepository customerRepository;

    /**
     * Resolve customer from either an explicit customerId or a free-form customerRef.
     * Resolution order: internal Long ID → externalId → email (contains @) → phone.
     * If autoCreate is true and no match is found, a new Customer is created with
     * externalId = customerRef. If autoCreate is false, throws ResourceNotFoundException.
     */
    @Transactional
    public Customer resolve(Long customerId, String customerRef, User merchant, boolean autoCreate) {
        // 1. Explicit internal ID
        if (customerId != null) {
            return customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        }

        if (customerRef == null || customerRef.isBlank()) {
            throw new IllegalArgumentException("Either customerId or customerRef must be provided");
        }

        // 2. Try parse as Long → internal ID
        try {
            Long parsedId = Long.parseLong(customerRef.trim());
            return customerRepository.findById(parsedId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + parsedId));
        } catch (NumberFormatException ignored) {
            // not a numeric ID, continue
        }

        // 3. Try externalId
        var byExternal = customerRepository.findByExternalId(customerRef.trim());
        if (byExternal.isPresent()) return byExternal.get();

        // 4. Try email
        if (customerRef.contains("@")) {
            var byEmail = customerRepository.findByEmail(customerRef.trim().toLowerCase());
            if (byEmail.isPresent()) return byEmail.get();
        }

        // 5. Try phone
        var byPhone = customerRepository.findByPhone(customerRef.trim());
        if (byPhone.isPresent()) return byPhone.get();

        // 6. Auto-create or throw
        if (autoCreate) {
            log.info("Auto-creating customer with externalId={}", customerRef);
            Customer newCustomer = Customer.builder()
                    .externalId(customerRef.trim())
                    .fullName(customerRef.trim())
                    .createdBy(merchant)
                    .build();
            return customerRepository.save(newCustomer);
        }

        throw new ResourceNotFoundException("Customer not found for ref: " + customerRef);
    }
}
