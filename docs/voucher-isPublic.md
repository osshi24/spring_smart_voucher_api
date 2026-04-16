# `isPublic` — Voucher công khai vs riêng tư

Trường `isPublic` trên voucher kiểm soát **ai được phép dùng voucher**.

## Bảng so sánh

| Tình huống | `isPublic: true` (công khai) | `isPublic: false` (riêng tư) |
|-----------|------------------------------|------------------------------|
| Ai dùng được? | **Bất kỳ customer nào** biết code | **Chỉ customer được gán** (có trong `voucher_customers`) |
| Cần gán customer trước? | ❌ Không cần | ✅ Bắt buộc |
| Validate khi redeem | Chỉ check status/validity/limit | Thêm check `voucher_customers` |
| Nếu customer chưa gán mà redeem | ✅ OK (dùng được) | ❌ 400 `"Voucher is not assigned to this customer"` |
| Use case điển hình | Flash sale, banner website, coupon công khai | Voucher VIP, cá nhân hoá, loyalty |

---

## Ví dụ cụ thể

### `isPublic: true` — Flash sale công khai

```json
POST /api/v1/vouchers
{
  "code": "BLACKFRIDAY24",
  "codeType": "SHARED",
  "isPublic": true,
  "maxUsageTotal": 10000,
  "discountType": "PERCENTAGE",
  "discountValue": 20
}
```

→ Post code lên banner website → ai nhập code cũng redeem được (đến khi hết 10000 lượt).

### `isPublic: false` — Voucher VIP

```json
POST /api/v1/vouchers
{
  "code": "VIP_ONLY",
  "codeType": "SHARED",
  "isPublic": false,
  "discountType": "FIXED",
  "discountValue": 500000
}

POST /api/v1/vouchers/{id}/customers
{ "customerIds": [1, 5, 10] }
```

→ Chỉ customer 1, 5, 10 redeem được. Customer khác biết code cũng bị reject:
```json
{
  "valid": false,
  "message": "Voucher is not assigned to this customer"
}
```

---

## Code check (logic backend)

File: [VoucherValidationService.java:98-105](../src/main/java/com/smartvoucher/service/VoucherValidationService.java#L98-L105)

```java
// Check public vs private
if (!voucher.getIsPublic()) {
    boolean customerAssigned = voucherCustomerRepository
        .existsByVoucherIdAndCustomerId(voucher.getId(), customer.getId());
    if (!customerAssigned) {
        return VoucherValidateResponse.invalid("Voucher is not assigned to this customer");
    }
}
```

---

## Quan hệ với `codeType`

`isPublic` và `codeType` **độc lập** nhau — có 4 combo:

| `codeType` | `isPublic` | Ý nghĩa | Thực tế dùng |
|-----------|-----------|---------|--------------|
| SHARED | true | Code chung, công khai | ✅ Phổ biến |
| SHARED | false | Code chung nhưng chỉ khách được gán dùng | VIP group |
| UNIQUE | true | Mỗi khách 1 code nhưng ai có code cũng dùng | ❌ Hiếm, ít ý nghĩa |
| UNIQUE | false | Code riêng từng người + check quyền | ✅ Chuẩn cá nhân hoá |

**Quy tắc thực tế:**
- **UNIQUE thường đi với `isPublic: false`**
- **SHARED thường đi với `isPublic: true`** cho đơn giản

---

## Default value

Nếu không truyền `isPublic` khi tạo voucher:
- Default = `true` (xem [Voucher.java:79](../src/main/java/com/smartvoucher/entity/Voucher.java#L79) và [VoucherCreateRequest.java:49](../src/main/java/com/smartvoucher/dto/request/VoucherCreateRequest.java#L49))

---

## Lưu ý

- Field `isPublic` **chỉ ảnh hưởng bước validate quyền truy cập**, không liên quan đến việc gửi email hay sinh mã.
- Nếu `isPublic: false` mà quên gán customer → mọi lần redeem đều bị reject.
- Muốn chuyển voucher từ private → public hoặc ngược lại, dùng `PUT /api/v1/vouchers/{id}` với field `isPublic`.
