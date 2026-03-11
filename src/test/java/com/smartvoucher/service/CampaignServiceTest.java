package com.smartvoucher.service;

import com.smartvoucher.dto.request.CampaignCreateRequest;
import com.smartvoucher.dto.response.CampaignResponse;
import com.smartvoucher.entity.Campaign;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.CampaignStatus;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CampaignRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private VoucherUsageRepository voucherUsageRepository;

    @InjectMocks
    private CampaignService campaignService;

    private User admin;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setUsername("admin01");
        admin.setRole(UserRole.ADMIN);
        admin.setIsActive(true);

        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setName("Summer Sale");
        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setCreatedBy(admin);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        admin.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @Test
    void create_success() {
        CampaignCreateRequest req = new CampaignCreateRequest();
        req.setName("Summer Sale");
        req.setStartDate(OffsetDateTime.now());
        req.setEndDate(OffsetDateTime.now().plusDays(30));

        when(userRepository.findByUsername("admin01")).thenReturn(Optional.of(admin));
        when(campaignRepository.save(any())).thenReturn(campaign);

        CampaignResponse response = campaignService.create(req);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Summer Sale");
        verify(campaignRepository).save(any(Campaign.class));
    }

    @Test
    void create_endDateBeforeStartDate_throws() {
        CampaignCreateRequest req = new CampaignCreateRequest();
        req.setName("Bad Dates");
        req.setStartDate(OffsetDateTime.now().plusDays(10));
        req.setEndDate(OffsetDateTime.now().plusDays(5));

        assertThatThrownBy(() -> campaignService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End date");
    }

    @Test
    void getById_found() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        CampaignResponse response = campaignService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Summer Sale");
    }

    @Test
    void getById_notFound_throws() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> campaignService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_success() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        campaignService.delete(1L);

        verify(campaignRepository).delete(campaign);
    }
}
