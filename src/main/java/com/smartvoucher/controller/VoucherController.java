package com.smartvoucher.controller;

import com.smartvoucher.dto.request.BulkAssignRequest;
import com.smartvoucher.dto.request.UniqueCodeGenerateRequest;
import com.smartvoucher.dto.request.VoucherCreateRequest;
import com.smartvoucher.dto.request.VoucherUpdateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.BulkDistributeResponse;
import com.smartvoucher.dto.response.BulkOperationResponse;
import com.smartvoucher.dto.response.UniqueCodeGenerateResponse;
import com.smartvoucher.dto.response.VoucherCustomerResponse;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.dto.response.VoucherUsageResponse;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.service.ExportService;
import com.smartvoucher.service.VoucherAssignmentService;
import com.smartvoucher.service.VoucherCodeService;
import com.smartvoucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "Voucher", description = "Quản lý voucher khuyến mãi: tạo, phân phối, pause/resume và theo dõi")
public class VoucherController {

    private final VoucherService voucherService;
    private final VoucherAssignmentService voucherAssignmentService;
    private final ExportService exportService;
    private final VoucherCodeService voucherCodeService;

    @Operation(summary = "Tạo voucher khuyến mãi mới")
    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> create(@Valid @RequestBody VoucherCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(voucherService.create(request)));
    }

    @Operation(summary = "Lấy danh sách voucher (có bộ lọc)")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VoucherResponse>>> getAll(
            @And({
                @Spec(spec = Equal.class,              params = "id",              path = "id"),
                @Spec(spec = Like.class,               params = "code",            path = "code"),
                @Spec(spec = Like.class,               params = "description",     path = "description"),
                @Spec(spec = Equal.class,              params = "status",          path = "status"),
                @Spec(spec = Equal.class,              params = "discountType",    path = "discountType"),
                @Spec(spec = Equal.class,              params = "campaignId",      path = "campaign.id"),
                @Spec(spec = IsTrue.class,             params = "isPublic",        path = "isPublic"),
                @Spec(spec = GreaterThanOrEqual.class, params = "discountValueMin",path = "discountValue"),
                @Spec(spec = LessThanOrEqual.class,    params = "discountValueMax",path = "discountValue"),
                @Spec(spec = GreaterThanOrEqual.class, params = "minOrderValue",   path = "minOrderValue"),
                @Spec(spec = GreaterThanOrEqual.class, params = "maxUsageTotal",   path = "maxUsageTotal"),
                @Spec(spec = GreaterThanOrEqual.class, params = "validFrom",       path = "validFrom",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "validUntil",      path = "validUntil",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = GreaterThanOrEqual.class, params = "createdAtFrom",   path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "createdAtTo",     path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX")
            }) Specification<Voucher> spec,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getAll(spec, pageable)));
    }

    @Operation(summary = "Lấy chi tiết voucher theo ID")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getById(id)));
    }

    @Operation(summary = "Cập nhật thông tin voucher")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @PathVariable Long id, @Valid @RequestBody VoucherUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.update(id, request)));
    }

    @Operation(summary = "Xóa voucher (chỉ khi chưa có lượt dùng)")
    @PreAuthorize("hasAuthority('VOUCHER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        voucherService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Gán danh sách khách hàng vào voucher")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PostMapping("/{id}/customers")
    public ResponseEntity<ApiResponse<Void>> assignCustomers(
            @PathVariable Long id, @RequestBody List<Long> customerIds) {
        voucherService.assignCustomers(id, customerIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Lấy danh sách khách hàng được gán voucher này")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}/customers")
    public ResponseEntity<ApiResponse<Page<VoucherCustomerResponse>>> getVoucherCustomers(
            @PathVariable Long id,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerEmail,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherAssignmentService.getVoucherCustomers(id, customerName, customerEmail, pageable)));
    }

    @Operation(summary = "Thu hồi quyền dùng voucher của một khách hàng")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @DeleteMapping("/{id}/customers/{customerId}")
    public ResponseEntity<ApiResponse<String>> revokeAssignment(
            @PathVariable Long id, @PathVariable Long customerId) {
        voucherAssignmentService.revokeAssignment(id, customerId);
        return ResponseEntity.ok(ApiResponse.success("Assignment revoked."));
    }

    @Operation(summary = "Lấy lịch sử sử dụng của voucher")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}/usages")
    public ResponseEntity<ApiResponse<Page<VoucherUsageResponse>>> getVoucherUsages(
            @PathVariable Long id,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String externalOrderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherAssignmentService.getVoucherUsages(id, customerId, externalOrderId, usedAtFrom, usedAtTo, pageable)));
    }

    @Operation(summary = "Xuất danh sách voucher ra file CSV")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @And({
                @Spec(spec = Equal.class, params = "status",       path = "status"),
                @Spec(spec = Equal.class, params = "campaignId",   path = "campaign.id"),
                @Spec(spec = Equal.class, params = "discountType", path = "discountType")
            }) Specification<Voucher> spec) {
        byte[] csv = exportService.exportVouchers(spec);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"vouchers.csv\"")
                .body(csv);
    }

    @Operation(summary = "Xuất lịch sử sử dụng voucher ra file CSV")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}/usages/export")
    public ResponseEntity<byte[]> exportUsagesCsv(@PathVariable Long id) {
        byte[] csv = exportService.exportVoucherUsages(id);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"usages.csv\"")
                .body(csv);
    }

    @Operation(summary = "Tạo hàng loạt mã unique code cho voucher")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PostMapping("/{id}/codes/generate")
    public ResponseEntity<ApiResponse<UniqueCodeGenerateResponse>> generateCodes(
            @PathVariable Long id,
            @Valid @RequestBody UniqueCodeGenerateRequest request) {
        var result = voucherCodeService.generateCodes(id, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success(new UniqueCodeGenerateResponse(result.generated(), result.total())));
    }

    @Operation(summary = "Gán nhiều khách hàng vào voucher cùng lúc")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PostMapping("/{id}/customers/bulk")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkAssign(
            @PathVariable Long id,
            @Valid @RequestBody BulkAssignRequest request) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.bulkAssign(id, request)));
    }

    @Operation(summary = "Nhân bản voucher (tạo bản sao với code mới)")
    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    @PostMapping("/{id}/clone")
    public ResponseEntity<ApiResponse<VoucherResponse>> cloneVoucher(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(voucherService.clone(id)));
    }

    @Operation(summary = "Gửi voucher hàng loạt qua email đến tất cả khách hàng đã gán")
    @PreAuthorize("hasAuthority('DISTRIBUTION_CREATE')")
    @PostMapping("/{id}/distribute/bulk")
    public ResponseEntity<ApiResponse<BulkDistributeResponse>> bulkDistribute(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.bulkDistribute(id)));
    }

    @Operation(summary = "Tạm dừng voucher — POS sẽ từ chối voucher này cho đến khi resume")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PutMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<VoucherResponse>> pause(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.pause(id)));
    }

    @Operation(summary = "Kích hoạt lại voucher đang tạm dừng")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PutMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<VoucherResponse>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.resume(id)));
    }
}
