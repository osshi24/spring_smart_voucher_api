package com.smartvoucher.controller;

import com.smartvoucher.dto.request.WebhookRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.WebhookResponse;
import com.smartvoucher.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhook", description = "Cấu hình endpoint nhận sự kiện realtime từ hệ thống (ví dụ: voucher được đổi)")
public class WebhookController {

    private final WebhookService webhookService;

    @Operation(summary = "Đăng ký webhook endpoint mới để nhận sự kiện")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<WebhookResponse>> create(@Valid @RequestBody WebhookRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(webhookService.create(req)));
    }

    @Operation(summary = "Lấy danh sách webhook đã cấu hình")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WebhookResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(webhookService.getAll()));
    }

    @Operation(summary = "Cập nhật URL hoặc cấu hình webhook")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WebhookResponse>> update(
            @PathVariable Long id, @Valid @RequestBody WebhookRequest req) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.update(id, req)));
    }

    @Operation(summary = "Xóa webhook")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        webhookService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
