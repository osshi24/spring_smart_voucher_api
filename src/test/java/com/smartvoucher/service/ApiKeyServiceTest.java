package com.smartvoucher.service;

import com.smartvoucher.dto.request.ApiKeyCreateRequest;
import com.smartvoucher.dto.response.ApiKeyResponse;
import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.ApiKeyRepository;
import com.smartvoucher.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setUsername("admin01");
        admin.setRole(UserRole.ADMIN);
        admin.setIsActive(true);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        admin.getUsername(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @Test
    void create_success_returnsKeyWithPlainText() {
        ApiKeyCreateRequest req = new ApiKeyCreateRequest();
        req.setName("POS System Key");
        req.setSystemName("pos-system");

        ApiKey savedKey = new ApiKey();
        savedKey.setId(1L);
        savedKey.setName("POS System Key");
        savedKey.setSystemName("pos-system");
        savedKey.setKeyHash("hashed-key");
        savedKey.setIsActive(true);
        savedKey.setCreatedBy(admin);

        when(userRepository.findByUsername("admin01")).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-key");
        when(apiKeyRepository.save(any())).thenReturn(savedKey);

        ApiKeyResponse response = apiKeyService.create(req);

        assertThat(response).isNotNull();
        assertThat(response.getPlainTextKey()).startsWith("sv_live_");
        assertThat(response.getName()).isEqualTo("POS System Key");
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void deactivate_success() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(1L);
        apiKey.setIsActive(true);
        apiKey.setCreatedBy(admin);

        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any())).thenReturn(apiKey);

        ApiKeyResponse response = apiKeyService.deactivate(1L);

        assertThat(apiKey.getIsActive()).isFalse();
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    void deactivate_notFound_throws() {
        when(apiKeyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_returnsList() {
        ApiKey key1 = new ApiKey();
        key1.setId(1L);
        key1.setName("Key 1");
        key1.setIsActive(true);
        key1.setCreatedBy(admin);

        when(apiKeyRepository.findAll()).thenReturn(List.of(key1));

        List<ApiKeyResponse> result = apiKeyService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Key 1");
    }
}
