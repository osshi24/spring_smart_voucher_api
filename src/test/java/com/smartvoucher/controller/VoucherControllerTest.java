package com.smartvoucher.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.kaczmarzyk.spring.data.jpa.web.SpecificationArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.smartvoucher.dto.request.VoucherCreateRequest;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.GlobalExceptionHandler;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VoucherControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    private VoucherService voucherService;

    @InjectMocks
    private VoucherController voucherController;

    private VoucherResponse sampleResponse;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(voucherController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new SpecificationArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();

        sampleResponse = new VoucherResponse();
        sampleResponse.setId(1L);
        sampleResponse.setCode("TEST01");
        sampleResponse.setDiscountType(DiscountType.PERCENTAGE);
        sampleResponse.setDiscountValue(BigDecimal.TEN);
        sampleResponse.setMinOrderValue(BigDecimal.ZERO);
        sampleResponse.setStatus(VoucherStatus.ACTIVE);
        sampleResponse.setCurrentUsageCount(0);
        sampleResponse.setIsPublic(true);
        sampleResponse.setApplicableProducts(new ArrayList<>());
        sampleResponse.setApplicableCategories(new ArrayList<>());
        sampleResponse.setApplicableBranches(new ArrayList<>());
        sampleResponse.setValidFrom(OffsetDateTime.now().minusDays(1));
        sampleResponse.setValidUntil(OffsetDateTime.now().plusDays(30));
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setCode("TEST01");
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setDiscountValue(BigDecimal.TEN);
        req.setValidFrom(OffsetDateTime.now().minusDays(1));
        req.setValidUntil(OffsetDateTime.now().plusDays(30));

        when(voucherService.create(any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/vouchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("TEST01"));
    }

    @Test
    void create_missingCode_returns400() throws Exception {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setDiscountValue(BigDecimal.TEN);
        req.setValidFrom(OffsetDateTime.now().minusDays(1));
        req.setValidUntil(OffsetDateTime.now().plusDays(30));

        mockMvc.perform(post("/api/v1/vouchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns200WithPage() throws Exception {
        when(voucherService.getAll(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/vouchers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void getById_found_returns200() throws Exception {
        when(voucherService.getById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/vouchers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(voucherService.getById(99L)).thenThrow(new ResourceNotFoundException("Voucher not found: 99"));

        mockMvc.perform(get("/api/v1/vouchers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void delete_noUsages_returns204() throws Exception {
        doNothing().when(voucherService).delete(1L);

        mockMvc.perform(delete("/api/v1/vouchers/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_withUsages_returns409() throws Exception {
        doThrow(new DuplicateResourceException("Cannot delete voucher with usages"))
                .when(voucherService).delete(1L);

        mockMvc.perform(delete("/api/v1/vouchers/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
