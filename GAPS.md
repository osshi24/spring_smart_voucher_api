# Smart Voucher — Phân tích Gaps & Vấn đề

> Ngày cập nhật: 2026-03-14
> Mục đích: Liệt kê đầy đủ các điểm còn thiếu hoặc chưa hoàn chỉnh cho hệ thống voucher multi-merchant (SaaS, F&B + Retail, 1 account = 1 branch)

---

## Tóm tắt mức độ ưu tiên

| Mức | Ý nghĩa | Số lượng |
|-----|---------|----------|
| 🔴 Critical | Blocking — không có thì hệ thống không hoạt động đúng | 6 |
| 🟠 High | Thiếu tính năng quan trọng cho merchant | 9 |
| 🟡 Medium | Cải thiện trải nghiệm / vận hành | 8 |
| 🟢 Low | Nice-to-have | 6 |

---

## Bối cảnh hệ thống (từ BA session)

| Câu hỏi | Quyết định |
| ------- | ---------- |
| Business model | SaaS — nhiều merchant dùng chung nền tảng |
| Target | F&B và Retail |
| Scale | ~100 merchant, 1 account = 1 branch |
| Billing | Free — bỏ qua payment |
| Integration | Merchant tự tích hợp API (X-API-Key) |
| Kênh phân phối | Email + QR code |
| End customer | Nhận qua email, không có customer portal |
| POS | Hỗ trợ cả 3 pattern: API headless, nhập tay, QR scan |

---

## 🔴 Critical

### C1. Email/SMS Distribution là mock — không gửi thực tế
**Vị trí:** `DistributionService.processDistribution()` — chỉ log, không gọi EmailService/SMS
**Ảnh hưởng:** Merchant tạo distribution nhưng customer không nhận được voucher
**Cần làm:**
- Tích hợp `EmailService.sendHtmlEmail()` để gửi voucher qua email
- Tích hợp SMS provider (Twilio, VIETTEL SMS...) cho channel SMS
- Thêm email template đẹp cho voucher

---

### C2. Không có cơ chế tự động hết hạn voucher (Auto-Expire)
**Vị trí:** Không có scheduler nào chạy
**Ảnh hưởng:** Voucher qua `valid_until` vẫn `status = ACTIVE` trong DB → POS có thể validate nhầm
**Cần làm:**
- Thêm `@Scheduled` job chạy mỗi ngày: chuyển voucher hết hạn sang `EXPIRED`
- Hoặc check `valid_until < NOW()` trong validate endpoint

---

### C3. External System không biết externalId của Customer
**Vị trí:** `POST /api/v1/external/vouchers/redeem` — cần `externalCustomerId`
**Ảnh hưởng:** POS phải biết trước `externalId` customer mới redeem được. Không có flow để POS tạo/lookup customer
**Cần làm:**
- `GET /api/v1/external/customers/lookup?email=...&phone=...` — tìm customer theo thông tin
- Hoặc auto-create customer khi redeem nếu `externalId` chưa tồn tại

---

### C4. QR Code chưa được sinh khi phát voucher qua email

**Vị trí:** `DistributionService` — email không đính kèm QR

**Ảnh hưởng:** Merchant không thể dùng QR scan pattern tại quầy

**Cần làm:**

- Khi gửi email voucher → generate QR image (encode signed URL `https://{domain}/v/{token}`)
- `signed_token` = JWT ngắn hạn (24h) chứa `{ voucherCode, customerId, issuedAt }`
- Đính QR vào email HTML dưới dạng inline image hoặc attachment
- Thư viện: ZXing (generate QR), JJWT (sign token)

---

### C5. Không có endpoint resolve QR token

**Vị trí:** Không có `GET /api/v1/external/vouchers/qr/{token}`

**Ảnh hưởng:** POS scan QR không biết gọi API nào để lấy thông tin voucher

**Cần làm:**

- `GET /api/v1/external/vouchers/qr/{token}` — decode token, trả về voucher info + customerName
- Validate token chưa hết hạn, chữ ký hợp lệ
- Dùng làm bước "preview" trước khi redeem

---

### C6. customerRef trên external API quá cứng — POS không biết customerId nội bộ

**Vị trí:** `POST /external/vouchers/validate` và `redeem` — đang yêu cầu `customerId` nội bộ

**Ảnh hưởng:** POS chỉ có email/phone/mã KH của họ, không có ID trong hệ thống voucher

**Cần làm:**

- Nhận `customerRef` dạng tự do: tự động resolve theo thứ tự externalId → email → phone
- Nếu không tìm thấy → auto-create customer mới (với `externalId = customerRef`)
- Merchant không cần pre-sync customer list trước khi tích hợp

---

## 🟠 High

### H1. Không có Batch/Bulk operations
**Ảnh hưởng:** Merchant có 10.000 customer không thể assign voucher cho tất cả qua API
**Cần làm:**
- `POST /api/v1/vouchers/{id}/customers/bulk` — assign danh sách lớn
- `POST /api/v1/customers/import` — upload CSV khách hàng
- `POST /api/v1/vouchers/batch` — tạo nhiều voucher cùng lúc (VD: tạo 500 mã unique)

---

### H2. Distribution không có Retry khi FAILED
**Vị trí:** `DistributionService` — khi gửi lỗi → status `FAILED`, không làm gì tiếp
**Cần làm:**
- `POST /api/v1/distributions/{id}/retry` — gửi lại các distribution bị lỗi
- Hoặc auto-retry với exponential backoff

---

### H3. Không có Export dữ liệu
**Ảnh hưởng:** Merchant không thể xuất báo cáo ra Excel/CSV để báo cáo nội bộ
**Cần làm:**
- `GET /api/v1/vouchers/export?format=csv` — xuất danh sách voucher
- `GET /api/v1/customers/export?format=csv` — xuất danh sách customer
- `GET /api/v1/vouchers/{id}/usages/export` — xuất lịch sử sử dụng
- Thư viện: Apache POI (Excel) hoặc OpenCSV

---

### H4. Không có Webhook khi voucher được dùng
**Ảnh hưởng:** Merchant không nhận được thông báo real-time khi customer dùng voucher tại POS
**Cần làm:**
- Thêm bảng `merchant_webhooks` (url, secret, events)
- `POST /api/v1/webhooks` — CRUD cấu hình webhook
- Trigger `@Async` call webhook sau khi redeem thành công

---

### H5. Dashboard thiếu metric quan trọng
**Vị trí:** `DashboardController`
**Thiếu:**
- Top 5 voucher được dùng nhiều nhất
- Conversion rate (distributed / redeemed)
- Revenue tổng theo thời gian (có chart data)
- Số merchant đang hoạt động (cho ADMIN)

---

### H6. Không có Voucher Template / Clone Campaign
**Ảnh hưởng:** Merchant phải tạo lại campaign từ đầu mỗi đợt khuyến mãi
**Cần làm:**
- `POST /api/v1/campaigns/{id}/clone` — clone campaign + vouchers
- `POST /api/v1/vouchers/{id}/clone` — clone voucher với code mới

---

### H7. VoucherStatus thiếu trạng thái FULLY_USED

**Vị trí:** `VoucherStatus` enum — chỉ có `ACTIVE`, `INACTIVE`, `EXPIRED`

**Ảnh hưởng:** Khi `current_usage_count >= max_usage_total` voucher vẫn hiển thị `ACTIVE`

**Cần làm:**

- Thêm `FULLY_USED` vào enum
- Trigger hoặc service check: sau mỗi redeem → nếu hết lượt → tự chuyển `FULLY_USED`

---

### H8. Unique code per customer chưa được hỗ trợ

**Vị trí:** Không có bảng `voucher_codes`

**Ảnh hưởng:** Retail thường cần 1 campaign → N unique code, mỗi customer 1 code riêng không ai trùng. Hiện tại chỉ hỗ trợ 1 code dùng chung (shared code).

**Cần làm:**

- Thêm bảng `voucher_codes (id, voucher_id, code, customer_id, used, created_at)`
- `POST /api/v1/vouchers/{id}/codes/generate?quantity=500` — batch generate unique codes
- Khi distribute → assign 1 code unique cho mỗi customer thay vì dùng chung 1 code
- Validate/redeem vẫn dùng chung luồng, chỉ lookup thêm bảng `voucher_codes`

---

### H9. Idempotency Key chưa có cho Redemption

**Vị trí:** `POST /api/v1/external/vouchers/redeem`

**Ảnh hưởng:** POS timeout → retry → voucher bị trừ 2 lần cho cùng 1 đơn hàng

**Cần làm:**

- Nhận header `Idempotency-Key: {externalOrderId}`
- Lưu `(idempotency_key, response)` vào Redis với TTL 24h
- Request trùng key → trả về kết quả cũ, không xử lý lại
- Đã có `external_order_id` unique constraint trong DB — cần expose lỗi này thành response thân thiện hơn

---

## 🟡 Medium

### M1. Customer chỉ có Delete, không có Deactivate
**Vị trí:** `CustomerController` — `DELETE /{id}` xóa hẳn
**Vấn đề:** Nếu customer đã có usage history → không nên xóa, chỉ nên deactivate
**Cần làm:** `PUT /api/v1/customers/{id}/deactivate` thay vì xóa cứng

---

### M2. Không kiểm tra Campaign status khi tạo Voucher
**Vị trí:** `VoucherService.create()` — không check campaign có `ACTIVE` không
**Vấn đề:** Tạo voucher vào campaign đã `ENDED`
**Cần làm:** Validate `campaign.status != ENDED` khi tạo voucher

---

### M3. Không có Audit Log cho hành động của user nội bộ
**Vấn đề:** Biết ai đã xóa voucher, ai đổi status campaign, ai deactivate API key
**Cần làm:**
- Bảng `audit_logs` (userId, action, entityType, entityId, oldValue, newValue, createdAt)
- Interceptor/AOP tự động ghi log khi có thay đổi quan trọng

---

### M4. Password policy chưa được enforce rõ ràng
**Vị trí:** `RegisterRequest` validation
**Vấn đề:** Không có regex check độ phức tạp password
**Cần làm:** `@Pattern` yêu cầu ít nhất 1 chữ hoa, 1 số, 1 ký tự đặc biệt, tối thiểu 8 ký tự

---

### M5. Rate limit chỉ áp dụng cho API Key, không áp dụng cho JWT
**Vấn đề:** User đăng nhập bằng JWT có thể gọi API không giới hạn
**Cần làm:** Áp dụng rate limit theo IP hoặc theo userId cho tất cả request

---

### M6. Không có Merchant Profile riêng
**Vấn đề:** User entity dùng chung cho ADMIN/STAFF/USER — merchant cần thêm business info
**Cần làm:**
- Bảng `merchant_profiles` (userId, businessName, businessType, address, logo, taxCode)
- `GET/PUT /api/v1/merchant/profile`

---

### M7. Không có cơ chế Idempotency cho Redemption
**Vị trí:** `POST /api/v1/external/vouchers/redeem`
**Vấn đề:** Nếu POS gọi 2 lần cùng một order (timeout retry) → voucher bị trừ 2 lần
**Cần làm:** `Idempotency-Key` header — lưu key + result trong Redis 24h, request trùng trả về kết quả cũ

---

### M8. Thiếu Pagination cho sub-resource endpoints
**Vị trí:** Một số endpoint trả về list không có page limit rõ ràng
**Cần làm:** Đảm bảo tất cả endpoint trả về list đều có `Pageable` với max size cap (VD: max 100)

---

## 🟢 Low

### L1. Không có Customer Notification Preferences
Merchant không biết customer muốn nhận voucher qua email hay SMS
**Cần làm:** Thêm `preferred_channel` vào `customers` table

---

### L2. Không có Campaign Schedule tự động
Merchant phải tự đổi status campaign từ DRAFT → ACTIVE
**Cần làm:** `auto_activate_at` field — scheduler tự chuyển DRAFT → ACTIVE đúng giờ

---

### L3. Không có Search toàn văn (Full-text search)
Tìm kiếm voucher/customer chỉ hỗ trợ `LIKE` — chậm khi data lớn
**Cần làm:** Index full-text search trên PostgreSQL (`tsvector`) hoặc Elasticsearch

---

### L4. API Versioning chưa có chiến lược rõ ràng
Hiện tại tất cả là `/api/v1/...` nhưng chưa có cơ chế deprecate khi lên v2
**Cần làm:** Định nghĩa policy upgrade version

---

### L5. Không có Health Check / Readiness endpoint
**Cần làm:** Expose Spring Actuator (`/actuator/health`) cho load balancer/k8s probe

---

### L6. Không có giới hạn số API Key per merchant
Merchant có thể tạo vô số API key
**Cần làm:** Giới hạn tối đa (VD: 10 key/merchant) hoặc config per merchant

---

## API hiện có đầy đủ

| Controller | Endpoint count | Trạng thái |
|---|:---:|---|
| Auth | 11 | ✅ Đầy đủ |
| User | 6 | ✅ Đầy đủ |
| Campaign | 8 | ✅ Đầy đủ |
| Voucher | 9 | ✅ Đầy đủ |
| Customer | 8 | ✅ Đầy đủ |
| Distribution | 4 | ⚠️ Thiếu retry, bulk |
| ApiKey | 6 | ✅ Đầy đủ |
| External (validate/redeem) | 2 | ✅ Có |
| Dashboard | 4 | ⚠️ Thiếu một số metric |
| Request Logs | 1 | ✅ Đầy đủ |
| Role/Permission | 4 | ✅ Đầy đủ |
