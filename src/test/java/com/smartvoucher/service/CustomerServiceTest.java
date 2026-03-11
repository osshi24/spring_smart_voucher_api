package com.smartvoucher.service;

import com.smartvoucher.dto.request.CustomerCreateRequest;
import com.smartvoucher.dto.response.CustomerResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setExternalId("CUS001");
        customer.setFullName("John Doe");
        customer.setEmail("john@example.com");
        customer.setPhone("0901234567");
        customer.setIsActive(true);
    }

    @Test
    void create_success() {
        CustomerCreateRequest req = new CustomerCreateRequest();
        req.setExternalId("CUS002");
        req.setFullName("Jane Doe");
        req.setEmail("jane@example.com");

        when(customerRepository.existsByExternalId("CUS002")).thenReturn(false);
        when(customerRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(customerRepository.save(any())).thenReturn(customer);

        CustomerResponse response = customerService.create(req);

        assertThat(response).isNotNull();
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void create_duplicateExternalId_throws() {
        CustomerCreateRequest req = new CustomerCreateRequest();
        req.setExternalId("CUS001");
        req.setFullName("Duplicate");

        when(customerRepository.existsByExternalId("CUS001")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("CUS001");
    }

    @Test
    void create_duplicateEmail_throws() {
        CustomerCreateRequest req = new CustomerCreateRequest();
        req.setExternalId("CUS003");
        req.setEmail("john@example.com");
        req.setFullName("New Customer");

        when(customerRepository.existsByExternalId("CUS003")).thenReturn(false);
        when(customerRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("email");
    }

    @Test
    void getById_found() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getById(1L);

        assertThat(response.getExternalId()).isEqualTo("CUS001");
        assertThat(response.getFullName()).isEqualTo("John Doe");
    }

    @Test
    void getById_notFound_throws() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByExternalId_found() {
        when(customerRepository.findByExternalId("CUS001")).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getByExternalId("CUS001");

        assertThat(response.getExternalId()).isEqualTo("CUS001");
    }

    @Test
    void delete_success() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        customerService.delete(1L);

        verify(customerRepository).delete(customer);
    }
}
