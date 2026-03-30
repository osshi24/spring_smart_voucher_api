package com.smartvoucher.service;

import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherCode;
import com.smartvoucher.entity.enums.CodeType;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.VoucherCodeRepository;
import com.smartvoucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherCodeService {

    private static final String BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VoucherCodeRepository voucherCodeRepository;
    private final VoucherRepository voucherRepository;

    @Transactional
    public UniqueCodeGenerateResult generateCodes(Long voucherId, int quantity) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + voucherId));

        if (voucher.getCodeType() != CodeType.UNIQUE) {
            throw new IllegalArgumentException("Voucher is not configured for UNIQUE codes. Set codeType=UNIQUE first.");
        }

        List<VoucherCode> toSave = new ArrayList<>(quantity);
        Set<String> existingCodes = new HashSet<>();
        // pre-load existing codes for this voucher to avoid collisions
        voucherCodeRepository.findByVoucherIdAndCustomerIsNull(voucherId)
                .forEach(vc -> existingCodes.add(vc.getCode()));

        int generated = 0;
        int attempts = 0;
        while (generated < quantity && attempts < quantity * 3) {
            attempts++;
            String code = generateBase62Code();
            if (!existingCodes.contains(code) && !voucherCodeRepository.findByCode(code).isPresent()) {
                existingCodes.add(code);
                toSave.add(VoucherCode.builder()
                        .voucher(voucher)
                        .code(code)
                        .build());
                generated++;
            }
        }

        voucherCodeRepository.saveAll(toSave);
        long total = voucherCodeRepository.countByVoucherId(voucherId);
        log.info("Generated {} unique codes for voucher {}, total={}", generated, voucherId, total);
        return new UniqueCodeGenerateResult(generated, total);
    }

    private String generateBase62Code() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listCodes(Long voucherId, Pageable pageable) {
        if (!voucherRepository.existsById(voucherId)) {
            throw new ResourceNotFoundException("Voucher not found: " + voucherId);
        }
        return voucherCodeRepository.findByVoucherId(voucherId, pageable).map(vc -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", vc.getId());
            m.put("code", vc.getCode());
            m.put("customerId", vc.getCustomer() != null ? vc.getCustomer().getId() : null);
            m.put("used", vc.getUsed());
            m.put("usedAt", vc.getUsedAt());
            m.put("createdAt", vc.getCreatedAt());
            return m;
        });
    }

    public record UniqueCodeGenerateResult(int generated, long total) {}
}
