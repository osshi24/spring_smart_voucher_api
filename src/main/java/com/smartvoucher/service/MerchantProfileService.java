package com.smartvoucher.service;

import com.smartvoucher.dto.request.MerchantProfileRequest;
import com.smartvoucher.dto.response.MerchantProfileResponse;
import com.smartvoucher.entity.MerchantProfile;
import com.smartvoucher.entity.User;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.MerchantProfileRepository;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantProfileService {

    private final MerchantProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MerchantProfileResponse getProfile() {
        User user = getCurrentUser();
        MerchantProfile profile = profileRepository.findByUserId(user.getId())
                .orElseGet(() -> MerchantProfile.builder().user(user).build());
        return MerchantProfileResponse.from(profile);
    }

    @Transactional
    public MerchantProfileResponse updateProfile(MerchantProfileRequest req) {
        User user = getCurrentUser();
        MerchantProfile profile = profileRepository.findByUserId(user.getId())
                .orElseGet(() -> MerchantProfile.builder().user(user).build());
        if (req.getBusinessName() != null) profile.setBusinessName(req.getBusinessName());
        if (req.getBusinessType() != null) profile.setBusinessType(req.getBusinessType());
        if (req.getAddress() != null) profile.setAddress(req.getAddress());
        if (req.getLogoUrl() != null) profile.setLogoUrl(req.getLogoUrl());
        if (req.getTaxCode() != null) profile.setTaxCode(req.getTaxCode());
        return MerchantProfileResponse.from(profileRepository.save(profile));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
