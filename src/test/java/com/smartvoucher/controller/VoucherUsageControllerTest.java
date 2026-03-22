package com.smartvoucher.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartvoucher.dto.response.VoucherUsageResponse;
import com.smartvoucher.exception.GlobalExceptionHandler;
import com.smartvoucher.service.ExportService;
import com.smartvoucher.service.VoucherUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VoucherUsageControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock private VoucherUsageService voucherUsageService;
    @Mock private ExportService exportService;

    @InjectMocks
    private VoucherUsageController controller;

    private VoucherUsageResponse sampleUsage;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new ByteArrayHttpMessageConverter(), converter)
                .build();

        sampleUsage = new VoucherUsageResponse();
        sampleUsage.setId(1L);
        sampleUsage.setVoucherId(10L);
        sampleUsage.setVoucherCode("SAVE10");
        sampleUsage.setCustomerId(5L);
        sampleUsage.setCustomerName("Nguyen Van A");
        sampleUsage.setExternalOrderId("ORD001");
        sampleUsage.setExternalBranchId("BRANCH_01");
        sampleUsage.setDiscountAmount(BigDecimal.valueOf(20000));
        sampleUsage.setOrderTotal(BigDecimal.valueOf(200000));
        sampleUsage.setStatus("COMPLETED");
        sampleUsage.setUsedAt(OffsetDateTime.now());
    }

    // ── GET /api/v1/usages ──────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getAll_noFilter_returns200WithPage() throws Exception {
        when(voucherUsageService.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleUsage), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/usages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].voucherCode").value("SAVE10"))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_withExternalOrderIdFilter_returns200() throws Exception {
        when(voucherUsageService.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/usages").param("externalOrderId", "ORD001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_withBranchFilter_returns200() throws Exception {
        when(voucherUsageService.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleUsage), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/usages").param("externalBranchId", "BRANCH_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].externalBranchId").value("BRANCH_01"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_withDateRangeFilter_returns200() throws Exception {
        when(voucherUsageService.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/usages")
                        .param("usedAtFrom", "2026-01-01T00:00:00Z")
                        .param("usedAtTo", "2026-03-01T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_paginationParams_respected() throws Exception {
        when(voucherUsageService.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 10));

        mockMvc.perform(get("/api/v1/usages").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(10));
    }

    // ── GET /api/v1/usages/export ───────────────────────────────────────────

    @Test
    void exportCsv_noFilter_returnsCsvFile() throws Exception {
        when(exportService.exportAllUsages(any())).thenReturn("id,voucherCode\n1,SAVE10\n".getBytes());

        mockMvc.perform(get("/api/v1/usages/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"usages.csv\""));
    }

    @Test
    void exportCsv_withFilters_returnsCsvFile() throws Exception {
        when(exportService.exportAllUsages(any())).thenReturn("id,voucherCode\n".getBytes());

        mockMvc.perform(get("/api/v1/usages/export")
                        .param("externalBranchId", "BRANCH_01")
                        .param("usedAtFrom", "2026-01-01T00:00:00Z")
                        .param("usedAtTo", "2026-03-01T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"));
    }

    // ── GET /api/v1/usages/branches ────────────────────────────────────────

    @Test
    void getBranches_returnsList() throws Exception {
        when(voucherUsageService.findDistinctBranchIds())
                .thenReturn(List.of("BRANCH_01", "BRANCH_02", "BRANCH_HCM"));

        mockMvc.perform(get("/api/v1/usages/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("BRANCH_01"))
                .andExpect(jsonPath("$.data[1]").value("BRANCH_02"))
                .andExpect(jsonPath("$.data[2]").value("BRANCH_HCM"));
    }

    @Test
    void getBranches_noBranchesYet_returnsEmptyList() throws Exception {
        when(voucherUsageService.findDistinctBranchIds()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/usages/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
