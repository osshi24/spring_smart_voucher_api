package com.smartvoucher.service;

import com.smartvoucher.dto.response.VoucherUsageResponse;
import com.smartvoucher.entity.VoucherUsage;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VoucherUsageService {

    private final VoucherUsageRepository voucherUsageRepository;

    @Transactional(readOnly = true)
    public Page<VoucherUsageResponse> findAll(Specification<VoucherUsage> spec, Pageable pageable) {
        return voucherUsageRepository.findAll(spec, pageable).map(VoucherUsageResponse::from);
    }

    @Transactional(readOnly = true)
    public List<String> findDistinctBranchIds() {
        return voucherUsageRepository.findDistinctBranchIds();
    }
}
