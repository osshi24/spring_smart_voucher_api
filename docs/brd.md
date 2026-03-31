# BRD — Business Requirements Document
# Hệ thống Smart Voucher

> **Phiên bản**: 1.0  
> **Ngày lập**: 2026-03-31  
> **Tác giả**: devthuan  
> **Trạng thái**: Draft

---

## Mục lục

1. [Tổng quan hệ thống](#1-tổng-quan-hệ-thống)
2. [Phạm vi & Mục tiêu](#2-phạm-vi--mục-tiêu)
3. [Stakeholders & Actors](#3-stakeholders--actors)
4. [Yêu cầu chức năng](#4-yêu-cầu-chức-năng)
5. [Quy tắc nghiệp vụ](#5-quy-tắc-nghiệp-vụ)
6. [Yêu cầu phi chức năng](#6-yêu-cầu-phi-chức-năng)
7. [Luồng trạng thái](#7-luồng-trạng-thái)
8. [Tích hợp ngoài](#8-tích-hợp-ngoài)
9. [Giả định & Ràng buộc](#9-giả-định--ràng-buộc)

---

## 1. Tổng quan hệ thống

**Smart Voucher** là nền tảng quản lý chương trình khuyến mãi dạng voucher (phiếu giảm giá), cho phép:

- **Merchant và nhân viên** tạo chiến dịch khuyến mãi, tạo và phân phối voucher cho khách hàng.
- **Hệ thống điểm bán hàng (POS)** và **sàn thương mại điện tử (e-commerce)** tích hợp để kiểm tra và đổi voucher theo thời gian thực.
- **Quản trị viên** giám sát toàn bộ hệ thống, phân quyền và xem báo cáo.

### Công nghệ nền tảng

| Thành phần | Công nghệ |
|-----------|-----------|
| Backend | Java 21 + Spring Boot 3.4.3 |
| Bảo mật | Spring Security 6.x + JWT (JJWT 0.12.6) |
| Cơ sở dữ liệu | PostgreSQL |
| Cache & Rate limit | Redis |
| Migration DB | Flyway |
| Gửi email | Spring Mail |
| QR Code | ZXing |
| API Docs | SpringDoc OpenAPI (Swagger) |

---

## 2. Phạm vi & Mục tiêu

### 2.1 Trong phạm vi

| STT | Tính năng |
|-----|-----------|
| 1 | Xác thực người dùng (đăng ký, đăng nhập, JWT, OTP email) |
| 2 | Quản lý chiến dịch khuyến mãi (Campaign) |
| 3 | Quản lý voucher (mã chung và mã unique) |
| 4 | Phân phối voucher qua Email / SMS |
| 5 | Tích hợp POS/e-commerce qua API Key |
| 6 | Validate & Redeem voucher (đổi voucher) |
| 7 | Quản lý khách hàng (Customer) |
| 8 | Quản lý API Key + Rate Limiting |
| 9 | Webhook realtime khi voucher được đổi |
| 10 | Phân quyền RBAC (Admin / Staff / User) |
| 11 | Dashboard thống kê và báo cáo |
| 12 | Audit log & Request log |

### 2.2 Ngoài phạm vi

- Thanh toán trực tuyến (payment gateway)
- Ứng dụng mobile cho khách hàng
- Chức năng loyalty points / điểm thưởng
- Giao diện frontend (hệ thống chỉ cung cấp REST API)

### 2.3 Mục tiêu nghiệp vụ

1. Giảm thời gian tạo và phân phối voucher từ thủ công sang tự động.
2. Đảm bảo tính toàn vẹn dữ liệu khi nhiều POS đồng thời đổi cùng một voucher.
3. Hỗ trợ Merchant tự vận hành mà không cần can thiệp của Admin.
4. Cung cấp API mở để hệ thống bên thứ ba tích hợp dễ dàng.

---

## 3. Stakeholders & Actors

### 3.1 Stakeholders

| Stakeholder | Vai trò | Mối quan tâm chính |
|-------------|---------|-------------------|
| Chủ hệ thống (Admin) | Quản trị toàn bộ platform | Bảo mật, hiệu năng, phân quyền |
| Merchant (Doanh nghiệp) | Tạo và quản lý voucher | Dễ dùng, báo cáo, tích hợp API |
| Nhân viên vận hành | Tạo campaign, xử lý phân phối | Công cụ CRUD đơn giản |
| Hệ thống POS | Đổi voucher tại quầy | API nhanh, ổn định, idempotent |
| Khách hàng cuối | Nhận và dùng voucher | Nhận email kịp thời, QR dễ quét |

### 3.2 Actors

| Actor | Xác thực | Mô tả |
|-------|----------|-------|
| **ADMIN** | JWT | Toàn quyền hệ thống. Tạo thủ công trong DB. |
| **STAFF** | JWT | Nhân viên vận hành. Đăng ký qua `/auth/register`, xác minh email → tự động ACTIVE. Chỉ thấy data của mình. |
| **USER (Merchant)** | JWT | Đại diện doanh nghiệp. Đăng ký qua `/auth/register-merchant`. Giống STAFF nhưng không có quyền xóa. Chỉ thấy data của mình. |
| **External System (POS)** | API Key (`X-API-Key`) | Hệ thống điểm bán hàng / e-commerce tích hợp. STAFF/USER tạo API Key và cấp cho hệ thống. |
| **Guest** | Không | Người chưa đăng nhập, chỉ có thể đăng ký và yêu cầu reset mật khẩu. |

### 3.3 Phân quyền tổng quan

| Quyền | ADMIN | STAFF | USER (Merchant) |
|-------|:-----:|:-----:|:---:|
| `VOUCHER_READ/CREATE/UPDATE` | ✅ | ✅ | ✅ |
| `VOUCHER_DELETE` | ✅ | ❌ | ❌ |
| `CAMPAIGN_READ/CREATE/UPDATE` | ✅ | ✅ | ✅ |
| `CAMPAIGN_DELETE` | ✅ | ❌ | ❌ |
| `CUSTOMER_READ/CREATE/UPDATE` | ✅ | ✅ | ✅ |
| `CUSTOMER_DELETE` | ✅ | ❌ | ❌ |
| `DISTRIBUTION_READ/CREATE` | ✅ | ✅ | ✅ |
| `APIKEY_CREATE/DEACTIVATE` | ✅ | ✅ | ✅ |
| `DASHBOARD_READ` | ✅ | ✅ | ✅ |
| `USER_READ/APPROVE/REJECT/MANAGE` | ✅ | ❌ | ❌ |
| `ROLE_PERMISSION_MANAGE` | ✅ | ❌ | ❌ |
| `REQUEST_LOG_READ` | ✅ | ❌ | ❌ |

> **Data Isolation**: STAFF và USER chỉ thấy data do chính họ tạo ra. ADMIN thấy toàn bộ.

---

## 4. Yêu cầu chức năng

### 4.1 Xác thực & Quản lý tài khoản

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-AUTH-01 | Đăng ký STAFF | Người dùng đăng ký với username, email, mật khẩu. Hệ thống tạo tài khoản PENDING và gửi OTP xác minh email (TTL 15 phút). |
| FR-AUTH-02 | Đăng ký Merchant | Giống STAFF, nhưng role = USER. Hệ thống tự động tạo Customer record liên kết với tài khoản này. |
| FR-AUTH-03 | Xác minh Email | Người dùng nhập OTP nhận qua email. Nếu hợp lệ và chưa hết hạn, emailVerified = true và status = ACTIVE. |
| FR-AUTH-04 | Đăng nhập | Người dùng đăng nhập bằng username/password. Hệ thống trả về JWT Access Token (ngắn hạn) và Refresh Token (dài hạn). |
| FR-AUTH-05 | Làm mới Token | Dùng Refresh Token để nhận Access Token mới mà không cần đăng nhập lại. |
| FR-AUTH-06 | Quên mật khẩu | Người dùng nhập email → nhận OTP reset (TTL 15 phút). Phản hồi luôn giống nhau dù email tồn tại hay không. |
| FR-AUTH-07 | Xác thực OTP reset | Nhập email + OTP → nhận Reset Token (JWT ngắn hạn) để thực hiện đặt lại mật khẩu. |
| FR-AUTH-08 | Đặt lại mật khẩu | Dùng Reset Token + mật khẩu mới. Hệ thống cập nhật mật khẩu và đánh dấu emailVerified = true. |
| FR-AUTH-09 | Đổi mật khẩu | Người dùng đã đăng nhập nhập mật khẩu cũ và mới để thay đổi mật khẩu. |
| FR-AUTH-10 | Admin duyệt/từ chối | Admin xem danh sách tài khoản PENDING, duyệt (→ ACTIVE) hoặc từ chối (→ REJECTED). Gửi email thông báo. |

### 4.2 Quản lý Chiến dịch (Campaign)

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-CAM-01 | Tạo chiến dịch | Tạo mới với tên, ngân sách, ngày bắt đầu/kết thúc. Mặc định status = DRAFT. |
| FR-CAM-02 | Cập nhật chiến dịch | Sửa thông tin cơ bản. Không thể thay đổi vouchers đã gán. |
| FR-CAM-03 | Thay đổi trạng thái | Chuyển trạng thái theo luồng hợp lệ: DRAFT → ACTIVE → PAUSED / ENDED. |
| FR-CAM-04 | Xem thống kê | Xem tổng vouchers, tổng lượt đổi, tổng tiền giảm, ngân sách đã dùng so với phân bổ. |
| FR-CAM-05 | Nhân bản chiến dịch | Clone toàn bộ thông tin, status = DRAFT, thời gian = hiện tại. |
| FR-CAM-06 | Xóa chiến dịch | Chỉ ADMIN. |

### 4.3 Quản lý Voucher

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-VOU-01 | Tạo voucher | Tạo mới với mã duy nhất (code), loại giảm giá (FIXED/PERCENTAGE), giá trị, giới hạn sử dụng, thời gian hiệu lực. Có thể gán vào campaign. |
| FR-VOU-02 | Tạm dừng / Kích hoạt | Pause → POS từ chối voucher; Resume → POS chấp nhận trở lại. |
| FR-VOU-03 | Gán khách hàng | Gán một hoặc nhiều khách hàng cho voucher riêng tư (isPublic = false). |
| FR-VOU-04 | Thu hồi gán | Xóa liên kết khách hàng — voucher. |
| FR-VOU-05 | Sinh mã unique | Với codeType = UNIQUE: sinh N mã dạng `{code}_{randomId}`, mỗi mã cho một khách. |
| FR-VOU-06 | Xem lịch sử sử dụng | Lọc theo customer, đơn hàng, thời gian. Xuất CSV. |
| FR-VOU-07 | Nhân bản voucher | Clone với code mới (`{code}_copy_{timestamp}`), currentUsageCount = 0. |
| FR-VOU-08 | Xóa voucher | Chỉ ADMIN. Chỉ xóa được khi currentUsageCount = 0. |

### 4.4 Phân phối Voucher (Distribution)

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-DIST-01 | Phân phối đơn lẻ | Tạo lệnh phân phối voucher đến một khách hàng qua Email hoặc SMS. Hệ thống tạo QR Code (JWT token) và gửi kèm email. |
| FR-DIST-02 | Phân phối hàng loạt | Gửi tới tất cả khách hàng đã gán trong voucher. Xử lý bất đồng bộ. |
| FR-DIST-03 | Thử lại phân phối | Gửi lại khi status = FAILED. |
| FR-DIST-04 | Hủy phân phối | Chỉ hủy được khi status = PENDING. |
| FR-DIST-05 | Gửi lại | Re-send email/SMS cho cả SENT và FAILED. |

### 4.5 Quản lý Khách hàng (Customer)

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-CUS-01 | Tạo khách hàng | Với các thông tin: họ tên, email, phone, externalId (từ hệ thống ngoài). |
| FR-CUS-02 | Tìm theo externalId | Tra cứu khách hàng từ hệ thống ngoài qua ID bên ngoài. |
| FR-CUS-03 | Vô hiệu hóa / Kích hoạt | Soft delete — isActive = false/true. |
| FR-CUS-04 | Import CSV | Import danh sách khách hàng từ file CSV. |
| FR-CUS-05 | Xem voucher của khách | Danh sách tất cả voucher được gán. |
| FR-CUS-06 | Xem lịch sử dùng | Lịch sử tất cả voucher đã đổi. |

### 4.6 API Key & Tích hợp External

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-AK-01 | Tạo API Key | Sinh key `sv_live_{base64}`, hash bằng BCrypt, lưu hash. Chỉ hiển thị key dạng plaintext MỘT LẦN khi tạo. |
| FR-AK-02 | Giới hạn số lượng | Tối đa 10 API Key active trên một tài khoản. |
| FR-AK-03 | Rate Limiting | Kiểm tra giới hạn request/phút và request/ngày qua Redis counter. Trả 429 khi vượt. |
| FR-AK-04 | Xem thống kê | Xem số request trong phút và ngày hiện tại so với giới hạn. |
| FR-AK-05 | Cập nhật Rate Limit | Thay đổi rateLimitPerMinute và rateLimitPerDay. |
| FR-AK-06 | Vô hiệu hóa | Đặt isActive = false. Key không thể dùng được nữa. |

### 4.7 Validate & Redeem Voucher (External API)

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-EXT-01 | Validate Voucher | POS kiểm tra voucher hợp lệ không. Không tiêu lượt sử dụng. Trả thông tin chiết khấu. |
| FR-EXT-02 | Redeem Voucher | POS đổi voucher — tiêu lượt, lưu usage, tính chiết khấu. Hỗ trợ Idempotency-Key. |
| FR-EXT-03 | Hoàn tác đổi | Hủy một lượt đổi — giảm currentUsageCount, xóa usage, phục hồi status FULLY_USED → ACTIVE nếu cần. |
| FR-EXT-04 | Giải mã QR | Decode JWT token từ QR Code email, trả thông tin voucher + khách hàng để POS hiển thị. |
| FR-EXT-05 | Lấy voucher khả dụng | POS tra cứu danh sách voucher khả dụng của khách theo số điện thoại / email / externalId, lọc theo orderTotal. |
| FR-EXT-06 | Tra cứu khách hàng | Tìm khách hàng qua phone, email, hoặc externalId. |

### 4.8 Webhook

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-WH-01 | Đăng ký Webhook | Merchant đăng ký URL nhận sự kiện, secret (cho HMAC), danh sách events (`voucher.redeemed`). |
| FR-WH-02 | Dispatch tự động | Sau mỗi lần redeem thành công, hệ thống async POST tới tất cả webhook đang active của merchant. |
| FR-WH-03 | Ký payload | Payload được ký bằng HMAC-SHA256 với webhook.secret. Header: `X-Webhook-Signature: sha256={sig}`. |
| FR-WH-04 | Retry | Tối đa 3 lần với exponential backoff nếu endpoint không trả 2xx. |
| FR-WH-05 | Auto-disable | Sau 10 lần thất bại liên tiếp, webhook tự động bị vô hiệu hóa. |

### 4.9 Dashboard & Báo cáo

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-DASH-01 | Tổng quan | Tổng số voucher active, tổng lượt đổi, tổng tiền giảm, tỷ lệ chuyển đổi, top 5 voucher theo lượt dùng. |
| FR-DASH-02 | Xu hướng sử dụng | Biểu đồ lượt đổi theo ngày/tuần/tháng trong khoảng thời gian chọn. |
| FR-DASH-03 | Thống kê chi nhánh | Group lượt đổi theo externalBranchId. |
| FR-DASH-04 | Tổng số Merchant | (Chỉ ADMIN) Số lượng Merchant đang hoạt động trên hệ thống. |

### 4.10 Audit & Request Log

| Mã | Tên | Mô tả |
|----|-----|-------|
| FR-LOG-01 | Audit Log | Ghi lại mọi thao tác CREATE/UPDATE/DELETE/APPROVE/REDEEM/REVERSE trên các entity. Lưu oldValue và newValue dạng JSON. |
| FR-LOG-02 | Request Log | Ghi lại mọi API call từ POS: endpoint, method, request body, response status, thời gian xử lý, IP. |

---

## 5. Quy tắc nghiệp vụ

### 5.1 Voucher

| Mã | Quy tắc |
|----|---------|
| BR-V-01 | Mã voucher (code) là duy nhất trên toàn hệ thống, không phân biệt hoa thường, không thể thay đổi sau khi tạo. |
| BR-V-02 | `validFrom` phải nhỏ hơn `validUntil`. |
| BR-V-03 | Nếu `discountType = PERCENTAGE`, giá trị `discountValue` phải trong khoảng [1, 100]. |
| BR-V-04 | Khi `currentUsageCount >= maxUsageTotal` (nếu có), voucher tự động chuyển sang trạng thái `FULLY_USED`. |
| BR-V-05 | Voucher chỉ có thể xóa khi `currentUsageCount = 0`. |
| BR-V-06 | Voucher `isPublic = false` chỉ có thể dùng bởi khách hàng đã được gán qua `VoucherCustomer`. |
| BR-V-07 | Voucher trạng thái `PAUSED` bị từ chối bởi External API. |
| BR-V-08 | Không thể gán voucher vào campaign có status `ENDED`. |
| BR-V-09 | `codeType = UNIQUE`: mỗi khách hàng được phân một mã riêng từ bảng `voucher_codes`. |

### 5.2 Chiến dịch (Campaign)

| Mã | Quy tắc |
|----|---------|
| BR-C-01 | `startDate` phải nhỏ hơn `endDate`. |
| BR-C-02 | Luồng trạng thái hợp lệ: `DRAFT → ACTIVE → PAUSED → ACTIVE → ENDED`. Không thể quay về DRAFT. |
| BR-C-03 | Khi campaign kết thúc (ENDED), không thể thêm voucher mới vào campaign. |

### 5.3 Đổi Voucher (Redemption)

| Mã | Quy tắc |
|----|---------|
| BR-R-01 | **Idempotency**: Nếu `Idempotency-Key` đã được dùng trong 24 giờ, trả về kết quả cũ, không xử lý lại. |
| BR-R-02 | **Duplicate Order**: Nếu cặp `(voucherId, externalOrderId)` đã tồn tại trong `voucher_usages`, coi là idempotent success. |
| BR-R-03 | **Concurrency**: Trước khi redeem, hệ thống thực hiện `SELECT ... FOR UPDATE` (pessimistic lock) trên Voucher để tránh race condition khi nhiều POS đồng thời. |
| BR-R-04 | Voucher phải ở trạng thái `ACTIVE` (không phải `PAUSED`, `EXPIRED`, `FULLY_USED`). |
| BR-R-05 | `orderTotal >= minOrderValue` (nếu minOrderValue > 0). |
| BR-R-06 | Nếu `applicableProducts/categories/branches` được cấu hình (không rỗng), request phải thỏa mãn ít nhất một điều kiện. |
| BR-R-07 | Số lần dùng của khách hàng không được vượt `maxUsagePerCustomer` (nếu có). |

### 5.4 Tính toán Chiết khấu

| Loại | Công thức |
|------|----------|
| `FIXED` | `discount = min(discountValue, orderTotal)` |
| `PERCENTAGE` | `discount = orderTotal × discountValue / 100`, giới hạn tối đa bởi `maxDiscountAmount` (nếu có) |
| `TIERED` | Tra bảng phân bậc theo `orderTotal` |

### 5.5 API Key

| Mã | Quy tắc |
|----|---------|
| BR-AK-01 | Mỗi tài khoản tối đa **10 API Key active**. |
| BR-AK-02 | Plaintext key chỉ trả về **một lần duy nhất** khi tạo. Hệ thống lưu BCrypt hash. |
| BR-AK-03 | API Key bị từ chối nếu `isActive = false` hoặc `expiresAt < now`. |

### 5.6 Bảo mật tài khoản

| Mã | Quy tắc |
|----|---------|
| BR-SEC-01 | Mật khẩu lưu dưới dạng BCrypt hash, không lưu plaintext. |
| BR-SEC-02 | OTP (email verify, reset password) có TTL **15 phút**, dùng một lần. |
| BR-SEC-03 | Endpoint quên mật khẩu luôn trả phản hồi giống nhau dù email tồn tại hay không (tránh lộ thông tin). |
| BR-SEC-04 | Reset Token (JWT) có thời gian sống ngắn, chỉ dùng cho một mục đích. |

### 5.7 Data Isolation

| Mã | Quy tắc |
|----|---------|
| BR-ISO-01 | STAFF và USER chỉ đọc/ghi data do chính họ tạo (`createdBy = currentUserId`). |
| BR-ISO-02 | ADMIN không bị giới hạn, thấy toàn bộ data của mọi user. |
| BR-ISO-03 | API Key khi validate/redeem chỉ resolve khách hàng thuộc merchant sở hữu API Key đó. |

---

## 6. Yêu cầu phi chức năng

### 6.1 Hiệu năng

| Mã | Yêu cầu |
|----|---------|
| NFR-P-01 | API Validate/Redeem phải phản hồi trong vòng **500ms** ở điều kiện tải bình thường. |
| NFR-P-02 | Redis được dùng để rate limiting và idempotency cache nhằm tránh đọc DB nhiều lần. |
| NFR-P-03 | Xử lý email và webhook **bất đồng bộ** (`@Async`) để không block response API. |

### 6.2 Tính sẵn sàng

| Mã | Yêu cầu |
|----|---------|
| NFR-A-01 | Pessimistic locking đảm bảo không có voucher bị đổi quá giới hạn dưới tải cao (race condition). |
| NFR-A-02 | Webhook tự động retry tối đa 3 lần với exponential backoff khi endpoint merchant lỗi. |

### 6.3 Bảo mật

| Mã | Yêu cầu |
|----|---------|
| NFR-S-01 | Toàn bộ API (trừ /auth/login, /auth/register, /auth/forgot-password) yêu cầu Bearer Token hoặc X-API-Key. |
| NFR-S-02 | External API phân biệt merchant — POS chỉ truy cập data của merchant sở hữu API Key. |
| NFR-S-03 | Webhook payload được ký HMAC-SHA256 để merchant xác minh nguồn gốc. |
| NFR-S-04 | API Key lưu dạng hash, không thể đọc ngược lại. |

### 6.4 Kiểm toán & Tuân thủ

| Mã | Yêu cầu |
|----|---------|
| NFR-AU-01 | Mọi thao tác thay đổi dữ liệu đều được ghi vào Audit Log với old/new value. |
| NFR-AU-02 | Mọi API call từ POS được ghi vào Request Log (endpoint, status, response time, IP). |

### 6.5 Khả năng mở rộng

| Mã | Yêu cầu |
|----|---------|
| NFR-SC-01 | Hỗ trợ Docker Compose cho môi trường dev và prod. |
| NFR-SC-02 | Flyway migration đảm bảo schema database có thể upgrade không mất dữ liệu. |

---

## 7. Luồng trạng thái

### 7.1 User Status

```
PENDING ──(Admin duyệt)──→ ACTIVE
PENDING ──(Admin từ chối)──→ REJECTED
```

### 7.2 Campaign Status

```
DRAFT ──→ ACTIVE ──→ PAUSED ──→ ACTIVE
                └──→ ENDED
```

> Khi ENDED: không thể thêm voucher mới. Không thể quay về trạng thái trước.

### 7.3 Voucher Status

```
ACTIVE ──(pause)──→ PAUSED ──(resume)──→ ACTIVE
ACTIVE ──(đạt maxUsageTotal)──→ FULLY_USED ──(reverse redemption)──→ ACTIVE
ACTIVE ──(hết validUntil)──→ EXPIRED
ACTIVE ──(admin)──→ INACTIVE
```

### 7.4 Distribution Status

```
PENDING ──(gửi thành công)──→ SENT
PENDING ──(gửi lỗi)──────→ FAILED ──(retry)──→ SENT / FAILED
PENDING ──(hủy)──────────→ CANCELLED
```

### 7.5 Voucher Redemption Flow

```
POS gọi /validate  → [không tiêu lượt, chỉ kiểm tra]
POS gọi /redeem    → [tiêu lượt, lưu VoucherUsage, cập nhật count]
POS gọi /reverse   → [hoàn tác, giảm count, xóa VoucherUsage]
```

---

## 8. Tích hợp ngoài

### 8.1 Cách POS/E-commerce tích hợp

```
1. Merchant tạo API Key trong hệ thống
   → Nhận key dạng: sv_live_xxxxxxxxxxxx

2. POS thêm header vào mọi request:
   X-API-Key: sv_live_xxxxxxxxxxxx

3. (Tuỳ chọn) POS thêm header idempotency:
   Idempotency-Key: {orderId}-{voucherId}

4. Validate trước khi thanh toán:
   POST /api/v1/external/vouchers/validate

5. Redeem sau khi thanh toán xong:
   POST /api/v1/external/vouchers/redeem

6. Nếu cần hoàn tiền:
   POST /api/v1/external/vouchers/usages/{id}/reverse
```

### 8.2 Webhook Payload mẫu

```json
{
  "event": "voucher.redeemed",
  "timestamp": 1743350400,
  "data": {
    "usageId": 42,
    "voucherId": 7,
    "voucherCode": "SUMMER2026",
    "customerId": 15,
    "externalOrderId": "ORD-20260331-001",
    "externalBranchId": "BRANCH-HCM-01",
    "discountAmount": 50000,
    "orderTotal": 200000,
    "usedAt": "2026-03-31T10:00:00+07:00"
  }
}
```

Headers:
```
X-Webhook-Event: voucher.redeemed
X-Webhook-Signature: sha256=abc123...
X-Webhook-Timestamp: 1743350400
```

### 8.3 Tài khoản mặc định

| Username | Password | Role |
|----------|----------|------|
| `admin01` | `admin123` | ADMIN |
| `staff01` | `staff123` | STAFF |
| `merchant01` | `staff123` | USER (Merchant) |

---

## 9. Giả định & Ràng buộc

### 9.1 Giả định

- Hệ thống chạy trong môi trường có PostgreSQL và Redis sẵn sàng.
- Email SMTP được cấu hình qua biến môi trường `MAIL_USERNAME`, `MAIL_PASSWORD`.
- JWT secret được cấu hình đủ dài (>= 256 bit) qua biến môi trường `JWT_SECRET`.
- POS/E-commerce có khả năng lưu trữ API Key an toàn trên phía họ.

### 9.2 Ràng buộc kỹ thuật

- Hệ thống không lưu plaintext API Key sau khi tạo.
- Xử lý email và webhook không được block thread chính (phải dùng `@Async`).
- Không hỗ trợ giao diện người dùng (chỉ REST API + Swagger UI).

### 9.3 Rủi ro

| Rủi ro | Mức độ | Biện pháp giảm thiểu |
|--------|--------|----------------------|
| Race condition khi nhiều POS redeem cùng lúc | Cao | Pessimistic lock (`SELECT FOR UPDATE`) |
| Webhook gửi trùng do retry | Trung bình | Merchant phải implement idempotency phía nhận |
| API Key bị lộ | Cao | Chỉ hiển thị một lần; lưu hash; hỗ trợ deactivate ngay lập tức |
| OTP brute-force | Trung bình | TTL 15 phút; không phản hồi khác biệt khi email không tồn tại |
