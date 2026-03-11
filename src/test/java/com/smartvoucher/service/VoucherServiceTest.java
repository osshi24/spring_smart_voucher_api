package com.smartvoucher.service;

import com.smartvoucher.dto.request.VoucherCreateRequest;
import com.smartvoucher.dto.request.VoucherUpdateRequest;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CampaignRepository;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private VoucherCustomerRepository voucherCustomerRepository;

    @InjectMocks
    private VoucherService voucherService;

    private User admin;
    private Voucher voucher;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setUsername("admin01");
        admin.setRole(UserRole.ADMIN);
        admin.setIsActive(true);

        voucher = new Voucher();
        voucher.setId(1L);
        voucher.setCode("TEST01");
        voucher.setDiscountType(DiscountType.PERCENTAGE);
        voucher.setDiscountValue(BigDecimal.TEN);
        voucher.setMinOrderValue(BigDecimal.ZERO);
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setCurrentUsageCount(0);
        voucher.setIsPublic(true);
        voucher.setApplicableProducts(new ArrayList<>());
        voucher.setApplicableCategories(new ArrayList<>());
        voucher.setApplicableBranches(new ArrayList<>());
        voucher.setValidFrom(OffsetDateTime.now().minusDays(1));
        voucher.setValidUntil(OffsetDateTime.now().plusDays(30));
        voucher.setCreatedBy(admin);

        // Set up security context
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        admin.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @Test
    void create_success() {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setCode("NEW01");
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setDiscountValue(BigDecimal.TEN);
        req.setValidFrom(OffsetDateTime.now().minusDays(1));
        req.setValidUntil(OffsetDateTime.now().plusDays(30));

        when(voucherRepository.existsByCode("NEW01")).thenReturn(false);
        when(userRepository.findByUsername("admin01")).thenReturn(Optional.of(admin));
        when(voucherRepository.save(any())).thenReturn(voucher);

        VoucherResponse response = voucherService.create(req);

        assertThat(response).isNotNull();
        verify(voucherRepository).save(any(Voucher.class));
    }

    @Test
    void create_duplicateCode_throws() {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setCode("TEST01");
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setDiscountValue(BigDecimal.TEN);
        req.setValidFrom(OffsetDateTime.now().minusDays(1));
        req.setValidUntil(OffsetDateTime.now().plusDays(30));

        when(voucherRepository.existsByCode("TEST01")).thenReturn(true);

        assertThatThrownBy(() -> voucherService.create(req))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_percentageOver100_throws() {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setCode("BAD01");
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setDiscountValue(BigDecimal.valueOf(150));
        req.setValidFrom(OffsetDateTime.now().minusDays(1));
        req.setValidUntil(OffsetDateTime.now().plusDays(30));

        when(voucherRepository.existsByCode("BAD01")).thenReturn(false);

        assertThatThrownBy(() -> voucherService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void create_validUntilBeforeValidFrom_throws() {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setCode("BAD02");
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setDiscountValue(BigDecimal.TEN);
        req.setValidFrom(OffsetDateTime.now().plusDays(10));
        req.setValidUntil(OffsetDateTime.now().plusDays(5));

        when(voucherRepository.existsByCode("BAD02")).thenReturn(false);

        assertThatThrownBy(() -> voucherService.create(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getById_success() {
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        VoucherResponse response = voucherService.getById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("TEST01");
    }

    @Test
    void getById_notFound_throws() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voucherService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_withoutFilter() {
        when(voucherRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(voucher)));

        Page<VoucherResponse> result = voucherService.getAll(null, Pageable.ofSize(10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void delete_withUsages_throws() {
        voucher.setCurrentUsageCount(5);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        assertThatThrownBy(() -> voucherService.delete(1L))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void delete_noUsages_success() {
        voucher.setCurrentUsageCount(0);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        voucherService.delete(1L);

        verify(voucherRepository).delete(voucher);
    }
}
