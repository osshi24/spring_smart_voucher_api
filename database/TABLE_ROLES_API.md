# Smart Voucher System - Nhiệm vụ từng bảng (API-focused)

> 9 bảng. Hệ thống **không quản lý** sản phẩm, chi nhánh, đơn hàng — những thứ đó thuộc hệ thống ngoài (POS, e-commerce). Tích hợp qua external ID và API.

---

## 1. `users` - Người dùng hệ thống

Tài khoản đăng nhập hệ thống voucher, gồm 2 vai trò:
- **ADMIN**: tạo chiến dịch, tạo/quản lý voucher, phân phối, xem báo cáo, quản lý API key
- **STAFF**: hỗ trợ tạo voucher, phân phối, xem báo cáo (quyền hạn chế hơn)

Không quản lý nhân viên bán hàng — đó thuộc hệ thống POS bên ngoài.

---

## 2. `customers` - Khách hàng

Người nhận và sử dụng voucher. Lưu email/SĐT để:
- Gửi voucher qua Email/SMS (`voucher_distributions`)
- Gán voucher private (`voucher_customers`)
- Kiểm soát số lần sử dụng của từng khách (`voucher_usages`)

`external_id` liên kết với mã khách hàng từ hệ thống ngoài (POS, e-commerce).

---

## 3. `campaigns` - Chiến dịch khuyến mãi

Nhóm các voucher theo chiến dịch marketing để quản lý và báo cáo hiệu quả.

| Thông tin | Cột |
|-----------|-----|
| Tên chiến dịch | `name` |
| Ngân sách dự kiến | `budget` (so sánh với thực chi trên dashboard) |
| Thời gian | `start_date` → `end_date` |
| Trạng thái | `status`: DRAFT → ACTIVE → PAUSED / ENDED |
| Người tạo | `created_by` → `users` |

---

## 4. `vouchers` - Voucher *(Bảng CORE)*

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

## 5. `voucher_customers` - Gán voucher cho KH

Khi `vouchers.is_public = FALSE`, chỉ khách hàng có trong bảng này mới được dùng.

VD: Voucher VIP100K (is_public=FALSE) chỉ gán cho 2 khách hàng VIP.

---

## 6. `voucher_distributions` - Phân phối qua Email/SMS

Theo dõi trạng thái gửi voucher đến khách hàng:
- `channel`: EMAIL hoặc SMS
- `status`: PENDING → SENT / FAILED
- `sent_at`: thời điểm gửi thành công
- `error_message`: lý do lỗi nếu FAILED

Phục vụ báo cáo: tỷ lệ gửi thành công, kênh nào hiệu quả hơn.

---

## 7. `voucher_usages` - Lịch sử sử dụng

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

## 8. `api_keys` - Xác thực hệ thống tích hợp

API key cho các hệ thống bên ngoài (POS, e-commerce, mobile app) gọi API:
- `key_hash`: hash của API key (không lưu plaintext)
- `system_name`: tên hệ thống tích hợp (VD: POS_QUAN1, ECOMMERCE_WEB)
- `is_active` + `expires_at`: kiểm soát quyền truy cập

---

## 9. `api_request_logs` - Log request từ hệ thống ngoài

Ghi lại toàn bộ request từ hệ thống ngoài gọi đến API, phục vụ debug và phát hiện gian lận:
- Endpoint nào, method gì
- Request body và response (JSONB)
- IP address
- Liên kết với `api_keys` để biết hệ thống nào gọi

---

## Luồng dữ liệu tổng quát

```
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

| | Bản đầy đủ (17 bảng) | Bản đơn giản (14 bảng) | Bản API-focused (9 bảng) |
|---|---|---|---|
| Quản lý SP/chi nhánh/đơn hàng | Co (branches, products, orders, order_items) | Co | **Khong** — thuoc he thong ngoai |
| Phạm vi voucher | Bảng trung gian N:N | Bảng trung gian N:N | **JSONB** (external ID) |
| Chiến dịch | Bảng `campaigns` | Gộp `campaign_name` | Bảng `campaigns` (co `budget`) |
| Audit log | Bảng `voucher_audit_logs` | Bo | Bo |
| API authentication | Khong | Khong | `api_keys` + `api_request_logs` |
| Race condition protection | Khong | Khong | **Trigger voi SELECT FOR UPDATE** |
| Lượt dùng/KH enforcement | Chi o app layer | Chi o app layer | **Trigger check tai DB level** |
