# Smart Voucher System - Nhiệm vụ từng bảng (API-focused)

> 13 bảng. Hệ thống **không quản lý** sản phẩm, chi nhánh, đơn hàng — những thứ đó thuộc hệ thống ngoài (POS, e-commerce). Tích hợp qua external ID và API.

---

## 1. `users` - Người dùng hệ thống

Tài khoản đăng nhập hệ thống voucher, gồm 2 vai trò:
- **ADMIN**: toàn quyền — tạo chiến dịch, quản lý voucher, phân phối, xem báo cáo, quản lý API key, quản lý người dùng
- **STAFF**: hỗ trợ tạo voucher, phân phối, xem báo cáo (quyền hạn chế hơn)

| Thông tin | Cột |
|-----------|-----|
| Tên đăng nhập | `username` |
| Họ tên | `full_name` |
| Vai trò | `role`: ADMIN \| STAFF |
| Trạng thái | `status`: ACTIVE \| PENDING \| REJECTED \| SUSPENDED |
| Kích hoạt | `is_active` |
| Email đã xác minh | `email_verified` (true/false) |

**Quyền** được xác định qua `role` → `role_permissions` → `permissions` (không lưu trực tiếp trên user).

Không quản lý nhân viên bán hàng — đó thuộc hệ thống POS bên ngoài.

---

## 2. `permissions` - Quyền hạn

Định nghĩa danh sách quyền trong hệ thống. Mỗi quyền đại diện cho một tính năng cụ thể.

| Permission | Mô tả |
|-----------|-------|
| `VOUCHER_READ` | Xem danh sách và chi tiết voucher |
| `VOUCHER_CREATE` | Tạo voucher mới |
| `VOUCHER_UPDATE` | Cập nhật voucher |
| `VOUCHER_DELETE` | Xóa voucher |
| `CAMPAIGN_READ` | Xem danh sách và chi tiết chiến dịch |
| `CAMPAIGN_CREATE` | Tạo chiến dịch mới |
| `CAMPAIGN_UPDATE` | Cập nhật chiến dịch |
| `CAMPAIGN_DELETE` | Xóa chiến dịch |
| `CUSTOMER_READ` | Xem danh sách khách hàng |
| `CUSTOMER_CREATE` | Tạo khách hàng mới |
| `CUSTOMER_UPDATE` | Cập nhật thông tin khách hàng |
| `DISTRIBUTION_READ` | Xem lịch sử phân phối |
| `DISTRIBUTION_CREATE` | Thực hiện phân phối voucher |
| `API_KEY_MANAGE` | Tạo, vô hiệu hóa, xem API key |
| `REPORT_VIEW` | Xem báo cáo và thống kê |
| `USER_MANAGE` | Quản lý tài khoản người dùng |

---

## 3. `role_permissions` - Phân quyền theo vai trò

Bảng trung gian ánh xạ `role` → `permission`. Dùng để kiểm tra quyền khi nhận request.

| Role | Permissions |
|------|------------|
| **ADMIN** | Tất cả quyền trên |
| **STAFF** | VOUCHER_READ, VOUCHER_CREATE, VOUCHER_UPDATE, CAMPAIGN_READ, CUSTOMER_READ, CUSTOMER_CREATE, CUSTOMER_UPDATE, DISTRIBUTION_READ, DISTRIBUTION_CREATE, REPORT_VIEW |

Khóa chính: `(role, permission_id)` — mỗi cặp chỉ tồn tại một lần.

---

## 4. `email_verification_tokens` - Token xác minh email

Lưu token gửi đến email khi người dùng đăng ký hoặc yêu cầu xác minh lại email.

- Token hết hạn sau **24 giờ**
- Dùng một lần (`used = true` sau khi xác minh)
- Sau khi xác minh thành công: `users.email_verified = true`

---

## 5. `password_reset_tokens` - Token đặt lại mật khẩu

Lưu token gửi đến email khi người dùng yêu cầu đặt lại mật khẩu.

- Token hết hạn sau **1 giờ**
- Dùng một lần (`used = true` sau khi đổi mật khẩu)

---

## 6. `customers` - Khách hàng

Người nhận và sử dụng voucher. Lưu email/SĐT để:
- Gửi voucher qua Email/SMS (`voucher_distributions`)
- Gán voucher private (`voucher_customers`)
- Kiểm soát số lần sử dụng của từng khách (`voucher_usages`)

`external_id` liên kết với mã khách hàng từ hệ thống ngoài (POS, e-commerce). VD: `"CUS-001"`.

---

## 7. `campaigns` - Chiến dịch khuyến mãi

Nhóm các voucher theo chiến dịch marketing để quản lý và báo cáo hiệu quả.

| Thông tin | Cột |
|-----------|-----|
| Tên chiến dịch | `name` |
| Ngân sách dự kiến | `budget` (so sánh với thực chi trên dashboard) |
| Thời gian | `start_date` → `end_date` |
| Trạng thái | `status`: DRAFT → ACTIVE → PAUSED / ENDED |
| Người tạo | `created_by` → `users` |

---

## 8. `vouchers` - Voucher *(Bảng CORE)*

Bảng trung tâm, lưu toàn bộ thông tin và điều kiện voucher:

| Thông tin | Cột |
|-----------|-----|
| Mã voucher duy nhất | `code` |
| Thuộc chiến dịch nào | `campaign_id` → `campaigns` |
| Giảm % hay cố định | `discount_type` + `discount_value` |
| Trần giảm giá (cho %) | `max_discount_amount` |
| Đơn hàng tối thiểu | `min_order_value` |
| Phạm vi SP/ngành hàng/chi nhánh | `applicable_products/categories/branches` (JSONB) |
| Giới hạn tổng lượt | `max_usage_total` |
| Giới hạn lượt/khách | `max_usage_per_customer` |
| Đã dùng bao nhiêu lần | `current_usage_count` (trigger tự tăng) |
| Public hay private | `is_public` |
| Thời gian hiệu lực | `valid_from` → `valid_until` |
| Trạng thái | `status`: ACTIVE / INACTIVE / EXPIRED |

**Phạm vi áp dụng (JSONB)**: Thay vì dùng bảng trung gian N:N, schema API-focused lưu mảng ID từ hệ thống ngoài dạng JSONB. `[]` = áp dụng tất cả. VD: `["SP001","SP002"]`.

**Trigger bảo vệ**: Khi INSERT vào `voucher_usages`, trigger sẽ:
1. Lock row voucher (`SELECT ... FOR UPDATE`) để tránh race condition
2. Check `max_usage_total` — RAISE EXCEPTION nếu hết lượt
3. Check `max_usage_per_customer` — RAISE EXCEPTION nếu khách hết lượt
4. Tăng `current_usage_count + 1`

---

## 9. `voucher_customers` - Gán voucher cho KH

Khi `vouchers.is_public = FALSE`, chỉ khách hàng có trong bảng này mới được dùng.

VD: Voucher VIP100K (is_public=FALSE) chỉ gán cho 2 khách hàng VIP.

---

## 10. `voucher_distributions` - Phân phối qua Email/SMS

Theo dõi trạng thái gửi voucher đến khách hàng:
- `channel`: EMAIL hoặc SMS
- `status`: PENDING → SENT / FAILED / CANCELLED
- `sent_at`: thời điểm gửi thành công
- `error_message`: lý do lỗi nếu FAILED

Phục vụ báo cáo: tỷ lệ gửi thành công, kênh nào hiệu quả hơn.

---

## 11. `voucher_usages` - Lịch sử sử dụng

Ghi nhận mỗi lần voucher được sử dụng thành công:

| Thông tin | Cột |
|-----------|-----|
| Voucher nào | `voucher_id` |
| Khách nào | `customer_id` (NOT NULL) |
| Đơn hàng nào | `external_order_id` (NOT NULL, từ hệ thống ngoài) |
| Chi nhánh nào | `external_branch_id` (từ hệ thống ngoài) |
| Giảm bao nhiêu | `discount_amount` |
| Tổng đơn hàng | `order_total` (NOT NULL, từ hệ thống ngoài) |

**Ràng buộc**: `UNIQUE(voucher_id, external_order_id)` — 1 đơn hàng không thể dùng cùng 1 voucher 2 lần.

Phục vụ báo cáo: doanh thu từ voucher, thống kê chi nhánh, phát hiện gian lận.

---

## 12. `api_keys` - Xác thực hệ thống tích hợp

API key cho các hệ thống bên ngoài (POS, e-commerce, mobile app) gọi API:

| Thông tin | Cột |
|-----------|-----|
| Tên key | `name` |
| Hash key | `key_hash` (không lưu plaintext, dùng BCrypt) |
| Hệ thống tích hợp | `system_name` (VD: POS_QUAN1, ECOMMERCE_WEB) |
| Kích hoạt | `is_active` + `expires_at` |
| Giới hạn request/phút | `rate_limit_per_minute` (NULL = không giới hạn) |
| Giới hạn request/ngày | `rate_limit_per_day` (NULL = không giới hạn) |
| Người tạo | `created_by` → `users` |

**Rate limiting**: Khi request đến, `RateLimitFilter` kiểm tra Redis:
- `rate_limit:minute:{apiKeyId}` — counter hiện tại trong phút này
- `rate_limit:day:{apiKeyId}:{yyyyMMdd}` — counter hiện tại trong ngày (UTC)
- Response headers trả về: `X-RateLimit-Limit-Minute`, `X-RateLimit-Remaining-Minute`, `X-RateLimit-Limit-Day`, `X-RateLimit-Remaining-Day`

---

## 13. `api_request_logs` - Log request từ hệ thống ngoài

Ghi lại toàn bộ request từ hệ thống ngoài gọi đến API, phục vụ debug và phát hiện gian lận:

| Thông tin | Cột |
|-----------|-----|
| API key nào | `api_key_id` |
| Endpoint | `endpoint` + `method` |
| Request/Response | `request_body` + `response_body` (JSONB) |
| HTTP status | `response_status` |
| Thời gian xử lý | `response_time_ms` (milliseconds) |
| IP | `ip_address` |

---

## Quyền theo vai trò (tóm tắt)

| API | ADMIN | STAFF |
|-----|-------|-------|
| Quản lý voucher (CRUD) | ✅ | ✅ tạo/sửa, ❌ xóa |
| Quản lý chiến dịch (CRUD) | ✅ | ✅ xem/tạo/sửa, ❌ xóa |
| Quản lý khách hàng | ✅ | ✅ |
| Phân phối voucher | ✅ | ✅ |
| Quản lý API key | ✅ | ❌ |
| Xem báo cáo | ✅ | ✅ |
| Quản lý người dùng | ✅ | ❌ |
| Xem log request | ✅ | ❌ |

---

## Luồng dữ liệu tổng quát

```
[Dang ky] → email_verification_tokens → [Xac minh] → users.email_verified = true
[Quen MK] → password_reset_tokens     → [Doi MK]   → users.password_hash cap nhat

Admin tao campaign ──→ Tao voucher (gan campaign_id)
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
          Gan KH private         Phan phoi Email/SMS
        (voucher_customers)    (voucher_distributions)
                                        │
                                        ▼
                                  KH nhan ma voucher
                                        │
                                        ▼
┌──────────────────────────────────────────────────────────┐
│            HE THONG NGOAI GOI API CHECK VOUCHER          │
│                                                          │
│  api_keys (xac thuc) ──→ api_request_logs (ghi log)     │
│  rate_limit_per_minute + rate_limit_per_day (Redis)      │
│                                                          │
│  Gui: code, customer_id, order_total, products, branch   │
│                                                          │
│  Kiem tra:                                               │
│    - vouchers.status = ACTIVE?                           │
│    - valid_from <= NOW <= valid_until?                    │
│    - order_total >= min_order_value?                      │
│    - applicable_products/categories/branches phu hop?     │
│    - is_public = TRUE hoac KH trong voucher_customers?   │
│    - max_usage_total chua vuot?                          │
│    - max_usage_per_customer chua vuot?                   │
│                                                          │
│  Hop le ──→ voucher_usages (ghi nhan)                   │
│             + trigger tang current_usage_count            │
│  Khong hop le ──→ tra ly do                             │
└──────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────┐
│                  DASHBOARD BAO CAO                        │
│                                                          │
│  campaigns + vouchers + voucher_usages                   │
│  → Hieu qua tung chien dich (budget vs thuc chi)        │
│  → Ty le su dung voucher                                │
│  → Bieu do doanh thu theo thoi gian/chi nhanh           │
│  → Phat hien gian lan                                   │
└──────────────────────────────────────────────────────────┘
```

---

## So sánh với các bản khác

| | Bản đầy đủ (17 bảng) | Bản đơn giản (14 bảng) | Bản API-focused (13 bảng) |
|---|---|---|---|
| Quản lý SP/chi nhánh/đơn hàng | Co (branches, products, orders, order_items) | Co | **Khong** — thuoc he thong ngoai |
| Phạm vi voucher | Bảng trung gian N:N | Bảng trung gian N:N | **JSONB** (external ID) |
| Chiến dịch | Bảng `campaigns` | Gộp `campaign_name` | Bảng `campaigns` (co `budget`) |
| Phân quyền | Gộp trong user | Gộp trong user | **role_permissions** (tach biet) |
| Xác minh email | Khong | Khong | **email_verification_tokens** |
| Đặt lại mật khẩu | Khong | Khong | **password_reset_tokens** |
| Audit log | Bảng `voucher_audit_logs` | Bo | Bo |
| API authentication | Khong | Khong | `api_keys` + `api_request_logs` |
| Rate limiting | Khong | Khong | **per-minute + per-day (Redis)** |
| Race condition protection | Khong | Khong | **Trigger voi SELECT FOR UPDATE** |
| Lượt dùng/KH enforcement | Chi o app layer | Chi o app layer | **Trigger check tai DB level** |
