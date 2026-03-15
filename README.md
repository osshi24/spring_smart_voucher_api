# Smart Voucher API

Spring Boot 3.4.3 · Java 21 · PostgreSQL · Redis

---

## Khởi động

### Cách 1: Chạy local (source)

Khởi động database và Redis bằng Docker, sau đó chạy Spring Boot trực tiếp:

```bash
# 1. Start PostgreSQL + Redis
docker compose -f docker-compose.dev.yml up -d

# 2. Chạy ứng dụng
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

Ứng dụng chạy tại: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`

---

### Cách 2: Chạy toàn bộ bằng Docker Compose

Tạo file `.env` (tuỳ chọn) để cấu hình email và JWT:

```env
JWT_SECRET=smartvoucher-secret-key-must-be-at-least-256-bits-long-for-hs256
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
```

```bash
# Start tất cả services (app + postgres + redis)
docker compose up -d

# Xem logs
docker compose logs -f app

# Dừng
docker compose down
```

---

## Query Params

Tất cả API `GET` danh sách đều hỗ trợ filter, phân trang, và sắp xếp.

### Phân trang & sắp xếp

| Param | Mô tả | Mặc định |
|-------|-------|----------|
| `page` | Số trang (0-based) | `0` |
| `size` | Số bản ghi mỗi trang | `20` |
| `sort` | Tên field + hướng | `createdAt,desc` |

Ví dụ:
```
GET /api/v1/vouchers?page=0&size=10&sort=createdAt,desc
```

### Filter theo field

Các filter có thể kết hợp tự do. Chỉ truyền param nào cần lọc, không cần truyền hết.

**Kiểu Equal** — khớp chính xác:
```
?id=5
?status=ACTIVE
?discountType=PERCENTAGE
```

**Kiểu Like** — khớp một phần (không phân biệt hoa thường):
```
?code=SUM
?name=khuyến
?email=gmail
```

**Kiểu boolean:**
```
?isActive=true
?isPublic=false
```

**Kiểu range số** — dùng `Min`/`Max` suffix:
```
?discountValueMin=10&discountValueMax=50
?budgetMin=1000000
?rateLimitMinMin=10&rateLimitMinMax=100
```

**Kiểu range thời gian** — dùng `From`/`To` suffix, format ISO 8601:
```
?createdAtFrom=2026-01-01T00:00:00+07:00
?createdAtTo=2026-12-31T23:59:59+07:00
?validFrom=2026-03-01T00:00:00+07:00
```

---

### Filter params từng endpoint

#### `GET /api/v1/vouchers`

| Param | Kiểu | Mô tả |
|-------|------|-------|
| `id` | Equal | ID voucher |
| `code` | Like | Mã voucher |
| `description` | Like | Mô tả |
| `status` | Equal | `ACTIVE` / `INACTIVE` / `EXPIRED` |
| `discountType` | Equal | `PERCENTAGE` / `FIXED_AMOUNT` |
| `campaignId` | Equal | ID chiến dịch |
| `isPublic` | Boolean | `true` / `false` |
| `discountValueMin` / `discountValueMax` | Range | Giá trị giảm |
| `minOrderValue` | GreaterThan | Đơn hàng tối thiểu |
| `maxUsageTotal` | GreaterThan | Giới hạn tổng lượt |
| `validFrom` / `validUntil` | Range date | Thời gian hiệu lực |
| `createdAtFrom` / `createdAtTo` | Range date | Ngày tạo |

#### `GET /api/v1/campaigns`

| Param | Kiểu | Mô tả |
|-------|------|-------|
| `id` | Equal | ID chiến dịch |
| `name` | Like | Tên chiến dịch |
| `description` | Like | Mô tả |
| `status` | Equal | `DRAFT` / `ACTIVE` / `PAUSED` / `ENDED` |
| `budgetMin` / `budgetMax` | Range | Ngân sách |
| `startDateFrom` / `startDateTo` | Range date | Ngày bắt đầu |
| `endDateFrom` / `endDateTo` | Range date | Ngày kết thúc |
| `createdAtFrom` / `createdAtTo` | Range date | Ngày tạo |

#### `GET /api/v1/customers`

| Param | Kiểu | Mô tả |
|-------|------|-------|
| `id` | Equal | ID khách hàng |
| `name` | Like | Họ tên |
| `email` | Like | Email |
| `phone` | Like | Số điện thoại |
| `externalId` | Equal | Mã từ hệ thống ngoài |
| `isActive` | Boolean | `true` / `false` |
| `createdAtFrom` / `createdAtTo` | Range date | Ngày tạo |
| `updatedAtFrom` / `updatedAtTo` | Range date | Ngày cập nhật |

#### `GET /api/v1/users`

| Param | Kiểu | Mô tả |
|-------|------|-------|
| `id` | Equal | ID user |
| `username` | Like | Tên đăng nhập |
| `email` | Like | Email |
| `fullName` | Like | Họ tên |
| `phone` | Like | Số điện thoại |
| `role` | Equal | `ADMIN` / `STAFF` / `USER` |
| `status` | Equal | `ACTIVE` / `PENDING` / `REJECTED` / `SUSPENDED` |
| `isActive` | Boolean | `true` / `false` |
| `emailVerified` | Equal | `true` / `false` |
| `createdAtFrom` / `createdAtTo` | Range date | Ngày tạo |
| `updatedAtFrom` / `updatedAtTo` | Range date | Ngày cập nhật |

#### `GET /api/v1/distributions`

| Param | Kiểu | Mô tả |
|-------|------|-------|
| `id` | Equal | ID bản ghi |
| `voucherId` | Equal | ID voucher |
| `customerId` | Equal | ID khách hàng |
| `status` | Equal | `PENDING` / `SENT` / `FAILED` / `CANCELLED` |
| `channel` | Equal | `EMAIL` / `SMS` |
| `sentAtFrom` / `sentAtTo` | Range date | Thời điểm gửi |
| `createdAtFrom` / `createdAtTo` | Range date | Ngày tạo |

#### `GET /api/v1/api-keys`

| Param | Kiểu | Mô tả |
|-------|------|-------|
| `id` | Equal | ID api key |
| `name` | Like | Tên key |
| `systemName` | Like | Tên hệ thống |
| `isActive` | Boolean | `true` / `false` |
| `rateLimitMinMin` / `rateLimitMinMax` | Range | Giới hạn request/phút |
| `rateLimitDayMin` / `rateLimitDayMax` | Range | Giới hạn request/ngày |
| `expiresAtFrom` / `expiresAtTo` | Range date | Ngày hết hạn |
| `createdAtFrom` / `createdAtTo` | Range date | Ngày tạo |

#### Sub-resources (voucher, customer, campaign)

Các endpoint lồng nhau cũng hỗ trợ filter:

| Endpoint | Filter params |
|----------|--------------|
| `GET /api/v1/vouchers/{id}/customers` | `customerName`, `customerEmail` |
| `GET /api/v1/vouchers/{id}/usages` | `customerId`, `externalOrderId`, `usedAtFrom`, `usedAtTo` |
| `GET /api/v1/customers/{id}/vouchers` | `voucherCode`, `discountType` |
| `GET /api/v1/customers/{id}/usages` | `voucherId`, `usedAtFrom`, `usedAtTo` |
| `GET /api/v1/campaigns/{id}/vouchers` | `code`, `status`, `discountType`, `validFrom`, `validUntil` |

---

## Phân quyền hệ thống

### Vai trò

| Role | Mô tả | Cách tạo | Data isolation |
| ---- | ----- | -------- | -------------- |
| `ADMIN` | Toàn quyền hệ thống | Tạo thủ công trong DB | Xem tất cả data |
| `STAFF` | Vận hành — tạo/sửa voucher, campaign, customer, API key | Đăng ký (`/auth/register`) → xác minh email → tự động ACTIVE | Chỉ thấy data của mình |
| `USER` | Merchant — tương tự STAFF, không có quyền xóa | Đăng ký (`/auth/register-merchant`) → xác minh email → tự động ACTIVE | Chỉ thấy data của mình |

> **STAFF và USER** đều bị **data isolation**: mỗi tài khoản chỉ thấy voucher, campaign, customer, API key, distribution do chính mình tạo.
>
> **Hệ thống ngoài (POS, e-commerce)** xác thực bằng **API key** qua header `X-API-Key`. STAFF hoặc USER tạo API key và cấp cho hệ thống của mình.

### Danh sách Permission

| Permission | ADMIN | STAFF | USER |
|-----------|:-----:|:-----:|:----:|
| `VOUCHER_READ` | ✅ | ✅ | ✅ |
| `VOUCHER_CREATE` | ✅ | ✅ | ✅ |
| `VOUCHER_UPDATE` | ✅ | ✅ | ✅ |
| `VOUCHER_DELETE` | ✅ | ❌ | ❌ |
| `CAMPAIGN_READ` | ✅ | ✅ | ✅ |
| `CAMPAIGN_CREATE` | ✅ | ✅ | ✅ |
| `CAMPAIGN_UPDATE` | ✅ | ✅ | ✅ |
| `CAMPAIGN_DELETE` | ✅ | ❌ | ❌ |
| `CUSTOMER_READ` | ✅ | ✅ | ✅ |
| `CUSTOMER_CREATE` | ✅ | ✅ | ✅ |
| `CUSTOMER_UPDATE` | ✅ | ✅ | ✅ |
| `CUSTOMER_DELETE` | ✅ | ❌ | ❌ |
| `DISTRIBUTION_READ` | ✅ | ✅ | ✅ |
| `DISTRIBUTION_CREATE` | ✅ | ✅ | ✅ |
| `APIKEY_CREATE` | ✅ | ✅ | ✅ |
| `APIKEY_DEACTIVATE` | ✅ | ✅ | ✅ |
| `DASHBOARD_READ` | ✅ | ✅ | ✅ |
| `USER_READ` | ✅ | ❌ | ❌ |
| `USER_APPROVE` | ✅ | ❌ | ❌ |
| `USER_REJECT` | ✅ | ❌ | ❌ |
| `USER_MANAGE` | ✅ | ❌ | ❌ |
| `ROLE_PERMISSION_MANAGE` | ✅ | ❌ | ❌ |
| `REQUEST_LOG_READ` | ✅ | ❌ | ❌ |

### Tài khoản mặc định

| Username | Password | Role |
| -------- | -------- | ---- |
| `admin01` | `admin123` | ADMIN |
| `staff01` | `staff123` | STAFF |
| `merchant01` | `staff123` | USER |

> Tài khoản USER (merchant) tự đăng ký qua `POST /api/v1/auth/register-merchant`.


docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
