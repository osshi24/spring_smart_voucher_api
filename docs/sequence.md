# Sequence Diagram — Smart Voucher System

> **Ngôn ngữ**: PlantUML  
> **Cập nhật**: 2026-03-31

---

## Mục lục

1. [Đăng ký tài khoản & Xác minh email](#1-đăng-ký-tài-khoản--xác-minh-email)
2. [Đăng nhập & Làm mới Token](#2-đăng-nhập--làm-mới-token)
3. [Quên mật khẩu & Đặt lại mật khẩu](#3-quên-mật-khẩu--đặt-lại-mật-khẩu)
4. [Admin duyệt tài khoản](#4-admin-duyệt-tài-khoản)
5. [Tạo Chiến dịch & Voucher](#5-tạo-chiến-dịch--voucher)
6. [Phân phối Voucher qua Email](#6-phân-phối-voucher-qua-email)
7. [POS — Validate Voucher](#7-pos--validate-voucher)
8. [POS — Redeem Voucher (Đổi Voucher)](#8-pos--redeem-voucher-đổi-voucher)
9. [POS — Hoàn tác Đổi Voucher](#9-pos--hoàn-tác-đổi-voucher)
10. [Tạo & Sử dụng API Key](#10-tạo--sử-dụng-api-key)
11. [Webhook — Đăng ký & Tự động Dispatch](#11-webhook--đăng-ký--tự-động-dispatch)
12. [Phân quyền RBAC — Gán quyền cho Vai trò](#12-phân-quyền-rbac--gán-quyền-cho-vai-trò)

---

## 1. Đăng ký tài khoản & Xác minh email

```plantuml
@startuml SQ-01-Register
!theme plain
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor "Người dùng\n(Guest)" as USER
participant "AuthController\nPOST /auth/register" as CTRL
participant "AuthService" as SVC
database "PostgreSQL\n(users, email_otp)" as DB
participant "EmailService" as EMAIL

== Đăng ký tài khoản ==

USER -> CTRL: POST /auth/register\n{username, email, password, fullName}
activate CTRL

CTRL -> SVC: register(request)
activate SVC

SVC -> DB: SELECT * FROM users WHERE username = ? OR email = ?
DB --> SVC: (rỗng — chưa tồn tại)

SVC -> SVC: BCrypt.hash(password)

SVC -> DB: INSERT INTO users\n{status=PENDING, role=STAFF,\n emailVerified=false}
DB --> SVC: userId

SVC -> SVC: generateOtp(6 số, TTL 15 phút)
SVC -> DB: INSERT INTO email_verification_otps\n{email, otp, expiresAt}

SVC ->> EMAIL: sendVerificationEmail(email, otp)\n[async]
activate EMAIL
EMAIL --> SVC: (fire-and-forget)
deactivate EMAIL

SVC --> CTRL: {userId, message: "OTP đã gửi"}
deactivate SVC

CTRL --> USER: 201 Created\n{userId, message}
deactivate CTRL

== Xác minh email ==

USER -> CTRL: POST /auth/verify-email\n{email, otp}
activate CTRL
CTRL -> SVC: verifyEmail(email, otp)
activate SVC

SVC -> DB: SELECT * FROM email_verification_otps\nWHERE email = ? AND otp = ?
DB --> SVC: OTP record

alt OTP hết hạn hoặc không khớp
    SVC --> CTRL: throw BadRequestException
    CTRL --> USER: 400 OTP không hợp lệ
else OTP hợp lệ
    SVC -> DB: UPDATE users SET emailVerified = true,\nstatus = ACTIVE WHERE email = ?
    SVC -> DB: DELETE FROM email_verification_otps WHERE email = ?
    SVC --> CTRL: {message: "Xác minh thành công"}
    CTRL --> USER: 200 OK
end

deactivate SVC
deactivate CTRL

@enduml
```

---

## 2. Đăng nhập & Làm mới Token

```plantuml
@startuml SQ-02-Login
!theme plain
skinparam sequenceArrowThickness 2

actor "Người dùng" as USER
participant "AuthController\nPOST /auth/login" as CTRL
participant "AuthService" as SVC
participant "AuthenticationManager\n(Spring Security)" as AM
database "PostgreSQL\n(users)" as DB
participant "JwtTokenProvider" as JWT

== Đăng nhập ==

USER -> CTRL: POST /auth/login\n{username, password}
activate CTRL

CTRL -> SVC: login(request)
activate SVC

SVC -> AM: authenticate(username, password)
activate AM
AM -> DB: loadUserByUsername(username)
DB --> AM: UserDetails
AM -> AM: BCrypt.matches(password, hash)

alt Sai thông tin đăng nhập
    AM --> SVC: throw AuthenticationException
    SVC --> CTRL: throw UnauthorizedException
    CTRL --> USER: 401 Unauthorized
else Đăng nhập thành công
    AM --> SVC: Authentication object
    deactivate AM

    SVC -> DB: SELECT * FROM users\nJOIN role_permissions ON role\nWHERE username = ?
    DB --> SVC: User + permissions

    SVC -> JWT: generateAccessToken(userId, role, permissions)
    JWT --> SVC: accessToken (short-lived)

    SVC -> JWT: generateRefreshToken(userId)
    JWT --> SVC: refreshToken (long-lived)

    SVC -> DB: INSERT INTO audit_logs\n{action=LOGIN, userId, ...}

    SVC --> CTRL: LoginResponse\n{accessToken, refreshToken, expiresIn}
    CTRL --> USER: 200 OK\n{accessToken, refreshToken, expiresIn}
end

deactivate SVC
deactivate CTRL

== Làm mới Access Token ==

USER -> CTRL: POST /auth/refresh\n{refreshToken}
activate CTRL
CTRL -> SVC: refreshToken(refreshToken)
activate SVC

SVC -> JWT: validateRefreshToken(token)
JWT --> SVC: userId (hoặc throw nếu invalid)

SVC -> DB: SELECT * FROM users WHERE id = ?
DB --> SVC: User

SVC -> JWT: generateAccessToken(user)
JWT --> SVC: newAccessToken

SVC -> JWT: generateRefreshToken(user)
JWT --> SVC: newRefreshToken

SVC --> CTRL: {accessToken, refreshToken}
CTRL --> USER: 200 OK\n{accessToken, refreshToken}

deactivate SVC
deactivate CTRL

@enduml
```

---

## 3. Quên mật khẩu & Đặt lại mật khẩu

```plantuml
@startuml SQ-03-ForgotPassword
!theme plain
skinparam sequenceArrowThickness 2

actor "Người dùng" as USER
participant "AuthController" as CTRL
participant "AuthService" as SVC
database "PostgreSQL\n(users, password_reset_otps)" as DB
participant "EmailService" as EMAIL
participant "JwtTokenProvider" as JWT

== Quên mật khẩu ==

USER -> CTRL: POST /auth/forgot-password\n{email}
activate CTRL
CTRL -> SVC: forgotPassword(email)
activate SVC

SVC -> DB: SELECT * FROM users WHERE email = ?
DB --> SVC: User (hoặc null)

note right of SVC
  Dù email có tồn tại hay không,
  luôn trả message giống nhau
  để bảo vệ quyền riêng tư
end note

alt Email tồn tại
    SVC -> SVC: generateOtp(6 số, TTL 15 phút)
    SVC -> DB: INSERT INTO password_reset_otps
    SVC ->> EMAIL: sendPasswordResetEmail(email, otp)
end

SVC --> CTRL: {message: "Nếu email đã đăng ký, OTP đã được gửi"}
CTRL --> USER: 200 OK

deactivate SVC
deactivate CTRL

== Xác thực OTP reset ==

USER -> CTRL: POST /auth/verify-otp\n{email, otp}
activate CTRL
CTRL -> SVC: verifyResetOtp(email, otp)
activate SVC

SVC -> DB: SELECT * FROM password_reset_otps\nWHERE email = ? AND otp = ?
DB --> SVC: OTP record

alt OTP hết hạn hoặc không khớp
    SVC --> CTRL: throw BadRequestException
    CTRL --> USER: 400 OTP không hợp lệ
else OTP hợp lệ
    SVC -> JWT: generateResetToken(email, TTL ngắn)
    JWT --> SVC: resetToken

    SVC --> CTRL: {resetToken}
    CTRL --> USER: 200 OK {resetToken}
end

deactivate SVC
deactivate CTRL

== Đặt lại mật khẩu ==

USER -> CTRL: POST /auth/reset-password\n{resetToken, newPassword}
activate CTRL
CTRL -> SVC: resetPassword(token, newPassword)
activate SVC

SVC -> JWT: validateResetToken(token)
JWT --> SVC: email

SVC -> SVC: BCrypt.hash(newPassword)
SVC -> DB: UPDATE users SET passwordHash = ?,\nemailVerified = true WHERE email = ?
SVC -> DB: DELETE FROM password_reset_otps WHERE email = ?

SVC --> CTRL: {message: "Mật khẩu đã được đặt lại"}
CTRL --> USER: 200 OK

deactivate SVC
deactivate CTRL

@enduml
```

---

## 4. Admin duyệt tài khoản

```plantuml
@startuml SQ-04-ApproveUser
!theme plain
skinparam sequenceArrowThickness 2

actor "Quản trị viên\n(ADMIN)" as ADMIN
participant "UserController" as CTRL
participant "UserService" as SVC
database "PostgreSQL\n(users)" as DB
participant "EmailService" as EMAIL

== Xem danh sách tài khoản PENDING ==

ADMIN -> CTRL: GET /api/v1/users?status=PENDING
activate CTRL
CTRL -> SVC: getUsers(filter{status=PENDING}, pageable)
activate SVC
SVC -> DB: SELECT * FROM users WHERE status = 'PENDING'
DB --> SVC: Page<User>
SVC --> CTRL: Page<UserResponse>
CTRL --> ADMIN: 200 OK {users: [...]}
deactivate SVC
deactivate CTRL

== Duyệt tài khoản ==

ADMIN -> CTRL: POST /api/v1/users/{id}/approve
activate CTRL
CTRL -> SVC: approveUser(userId)
activate SVC

SVC -> DB: SELECT * FROM users WHERE id = ?
DB --> SVC: User{status=PENDING}

alt Không phải PENDING
    SVC --> CTRL: throw ConflictException
    CTRL --> ADMIN: 409 Conflict
else Hợp lệ
    SVC -> DB: UPDATE users SET status = 'ACTIVE' WHERE id = ?
    SVC -> DB: INSERT INTO audit_logs\n{action=APPROVE, entityType=User, ...}
    SVC ->> EMAIL: sendApprovalNotification(user.email)
    SVC --> CTRL: {message: "Tài khoản đã được duyệt"}
    CTRL --> ADMIN: 200 OK
end

deactivate SVC
deactivate CTRL

== Từ chối tài khoản ==

ADMIN -> CTRL: POST /api/v1/users/{id}/reject
activate CTRL
CTRL -> SVC: rejectUser(userId)
activate SVC

SVC -> DB: UPDATE users SET status = 'REJECTED' WHERE id = ?
SVC -> DB: INSERT INTO audit_logs\n{action=REJECT, ...}
SVC --> CTRL: {message: "Tài khoản đã bị từ chối"}
CTRL --> ADMIN: 200 OK

deactivate SVC
deactivate CTRL

@enduml
```

---

## 5. Tạo Chiến dịch & Voucher

```plantuml
@startuml SQ-05-CampaignVoucher
!theme plain
skinparam sequenceArrowThickness 2

actor "Nhân viên / Merchant\n(STAFF / USER)" as STAFF
participant "CampaignController" as CC
participant "CampaignService" as CS
participant "VoucherController" as VC
participant "VoucherService" as VS
database "PostgreSQL\n(campaigns, vouchers)" as DB

== Tạo chiến dịch ==

STAFF -> CC: POST /api/v1/campaigns\n{name, description, budget,\n startDate, endDate}
activate CC
CC -> CS: createCampaign(request, currentUser)
activate CS

CS -> CS: validate: startDate < endDate
CS -> DB: INSERT INTO campaigns\n{status=DRAFT, createdBy=userId}
DB --> CS: Campaign

CS -> DB: INSERT INTO audit_logs\n{action=CREATE, entityType=Campaign}
CS --> CC: CampaignResponse
CC --> STAFF: 201 Created {campaign}
deactivate CS
deactivate CC

== Cập nhật trạng thái chiến dịch ==

STAFF -> CC: PUT /api/v1/campaigns/{id}/status\n{status: "ACTIVE"}
activate CC
CC -> CS: updateStatus(campaignId, ACTIVE)
activate CS
CS -> DB: UPDATE campaigns SET status = 'ACTIVE' WHERE id = ?
CS -> DB: INSERT INTO audit_logs\n{action=STATUS_CHANGE, ...}
CS --> CC: CampaignResponse
CC --> STAFF: 200 OK {campaign}
deactivate CS
deactivate CC

== Tạo voucher trong chiến dịch ==

STAFF -> VC: POST /api/v1/vouchers\n{code, campaignId, discountType,\n discountValue, maxUsageTotal,\n validFrom, validUntil, codeType}
activate VC
VC -> VS: createVoucher(request, currentUser)
activate VS

VS -> DB: SELECT * FROM vouchers WHERE code = ?
DB --> VS: (rỗng — code chưa tồn tại)

VS -> DB: SELECT * FROM campaigns WHERE id = ?
DB --> VS: Campaign

VS -> VS: validate: campaign.status != ENDED\nvalidate: validFrom < validUntil\nvalidate: nếu PERCENTAGE thì discountValue <= 100

VS -> DB: INSERT INTO vouchers\n{status=ACTIVE, currentUsageCount=0,\n createdBy=userId, codeType=...}
DB --> VS: Voucher

VS -> DB: INSERT INTO audit_logs\n{action=CREATE, entityType=Voucher}
VS --> VC: VoucherResponse
VC --> STAFF: 201 Created {voucher}
deactivate VS
deactivate VC

== Sinh mã unique (nếu codeType=UNIQUE) ==

STAFF -> VC: POST /api/v1/vouchers/{id}/codes/generate\n{quantity: 100}
activate VC
VC -> VS: generateUniqueCodes(voucherId, quantity)
activate VS

loop quantity lần
    VS -> VS: generate: {voucherCode}_{randomId}
    VS -> DB: INSERT INTO voucher_codes\n{voucherId, code, used=false}
end

VS --> VC: UniqueCodeGenerateResponse\n{generated: 100}
VC --> STAFF: 200 OK
deactivate VS
deactivate VC

@enduml
```

---

## 6. Phân phối Voucher qua Email

```plantuml
@startuml SQ-06-Distribution
!theme plain
skinparam sequenceArrowThickness 2

actor "Nhân viên / Merchant" as STAFF
participant "VoucherController /\nDistributionController" as CTRL
participant "DistributionService" as SVC
participant "QrTokenService" as QR
participant "EmailService" as EMAIL
database "PostgreSQL\n(distributions, voucher_codes)" as DB

== Phân phối đơn lẻ ==

STAFF -> CTRL: POST /api/v1/distributions\n{voucherId, customerId, channel=EMAIL}
activate CTRL
CTRL -> SVC: createDistribution(request)
activate SVC

SVC -> DB: INSERT INTO voucher_distributions\n{status=PENDING}
DB --> SVC: distribution

SVC -> SVC: processDistribution(distribution)\n[async @Async]

activate SVC #lightblue
note right: Xử lý bất đồng bộ

alt codeType = UNIQUE
    SVC -> DB: SELECT unassigned VoucherCode WHERE voucherId = ?
    DB --> SVC: uniqueCode
    SVC -> DB: UPDATE voucher_codes SET customerId = ? WHERE id = ?
end

SVC -> QR: generateQrToken(voucherCode, customerId, merchantId)\n[JWT, short TTL]
QR --> SVC: qrToken

SVC -> QR: generateQrImage(qrToken)
QR --> SVC: qrImageBytes

SVC -> EMAIL: sendVoucherEmail(customer.email, voucher, qrImage)
activate EMAIL

alt Gửi thành công
    EMAIL --> SVC: success
    SVC -> DB: UPDATE voucher_distributions SET\nstatus=SENT, sentAt=now()
else Gửi thất bại
    EMAIL --> SVC: exception
    SVC -> DB: UPDATE voucher_distributions SET\nstatus=FAILED, errorMessage=?
end
deactivate EMAIL
deactivate SVC

SVC --> CTRL: DistributionResponse
CTRL --> STAFF: 201 Created {distribution}
deactivate SVC
deactivate CTRL

== Phân phối hàng loạt ==

STAFF -> CTRL: POST /api/v1/vouchers/{id}/distribute/bulk
activate CTRL
CTRL -> SVC: bulkDistribute(voucherId)
activate SVC

SVC -> DB: SELECT * FROM voucher_customers WHERE voucherId = ?
DB --> SVC: List<VoucherCustomer>

loop mỗi customer
    SVC -> DB: INSERT INTO voucher_distributions\n{status=PENDING}
end

SVC ->> SVC: processAll() [async]

SVC --> CTRL: BulkDistributeResponse\n{totalQueued: N}
CTRL --> STAFF: 200 OK {totalQueued}
deactivate SVC
deactivate CTRL

@enduml
```

---

## 7. POS — Validate Voucher

```plantuml
@startuml SQ-07-Validate
!theme plain
skinparam sequenceArrowThickness 2

actor "Hệ thống POS" as POS
participant "ExternalVoucherController\nPOST /external/vouchers/validate" as CTRL
participant "ApiKeyAuthFilter" as AUTH
participant "RateLimitService" as RATE
participant "VoucherValidationService" as SVC
participant "CustomerResolutionService" as CUST
database "PostgreSQL\n(vouchers, voucher_customers,\nvoucher_usages)" as DB
database "Redis\n(rate_limit)" as REDIS

POS -> CTRL: POST /api/v1/external/vouchers/validate\nHeader: X-API-Key: sv_live_xxxx\n{voucherCode, customerRef, orderTotal,\n products[], categoryId, branchId}
activate CTRL

== Xác thực API Key ==

CTRL -> AUTH: authenticate(X-API-Key)
activate AUTH
AUTH -> DB: SELECT * FROM api_keys WHERE isActive = true
DB --> AUTH: List<ApiKey>
AUTH -> AUTH: BCrypt.matches(rawKey, keyHash)\nkiểm tra expiresAt

alt API Key không hợp lệ hoặc hết hạn
    AUTH --> CTRL: throw UnauthorizedException
    CTRL --> POS: 401 Unauthorized
else API Key hợp lệ
    AUTH --> CTRL: ApiKey entity
    deactivate AUTH

    == Kiểm tra Rate Limit ==

    CTRL -> RATE: checkRateLimit(apiKeyId)
    activate RATE
    RATE -> REDIS: INCR api_key:{id}:minute\nEXPIRE 60s
    RATE -> REDIS: INCR api_key:{id}:day\nEXPIRE 86400s
    REDIS --> RATE: currentCounts

    alt Vượt rate limit
        RATE --> CTRL: throw RateLimitException
        CTRL --> POS: 429 Too Many Requests
    else Trong giới hạn
        RATE --> CTRL: ok
        deactivate RATE

        == Resolve Khách hàng ==

        CTRL -> CUST: resolveCustomer(customerRef, apiKey.createdBy)
        activate CUST
        CUST -> DB: SELECT * FROM customers WHERE\n(phone=? OR email=? OR externalId=?)\nAND createdBy = merchantId
        DB --> CUST: Customer
        CUST --> CTRL: Customer
        deactivate CUST

        == Validate Voucher ==

        CTRL -> SVC: validate(voucherCode, customer, orderTotal, context)
        activate SVC

        SVC -> DB: SELECT * FROM vouchers WHERE code = ?
        DB --> SVC: Voucher

        SVC -> SVC: Kiểm tra status = ACTIVE (không phải PAUSED/EXPIRED)
        SVC -> SVC: Kiểm tra validFrom <= now <= validUntil
        SVC -> SVC: Kiểm tra orderTotal >= minOrderValue

        alt applicableProducts / categories / branches được cấu hình
            SVC -> SVC: Kiểm tra context phù hợp với\napplicableProducts / categories / branches
        end

        alt isPublic = false
            SVC -> DB: SELECT * FROM voucher_customers\nWHERE voucherId=? AND customerId=?
            DB --> SVC: (có hoặc không)
            SVC -> SVC: Kiểm tra customer được phép dùng
        end

        SVC -> DB: SELECT COUNT(*) FROM voucher_usages\nWHERE voucherId=? AND customerId=?
        DB --> SVC: usageCount

        SVC -> SVC: Kiểm tra currentUsageCount < maxUsageTotal\nKiểm tra usageCount < maxUsagePerCustomer

        alt Voucher không hợp lệ
            SVC --> CTRL: {isValid: false, errorMessage: "..."}
        else Voucher hợp lệ
            SVC -> SVC: calculateDiscount(discountType, orderTotal)
            SVC --> CTRL: VoucherValidateResponse\n{isValid: true, discountType,\n discountValue, maxDiscountAmount}
        end

        deactivate SVC

        CTRL -> DB: INSERT INTO api_request_logs\n{apiKeyId, endpoint, requestBody,\n responseStatus, responseTimeMs}

        CTRL --> POS: 200 OK\n{isValid, discountType, discountValue, ...}
    end
end

deactivate CTRL

@enduml
```

---

## 8. POS — Redeem Voucher (Đổi Voucher)

```plantuml
@startuml SQ-08-Redeem
!theme plain
skinparam sequenceArrowThickness 2

actor "Hệ thống POS" as POS
participant "ExternalVoucherController\nPOST /external/vouchers/redeem" as CTRL
participant "ApiKeyAuthFilter + RateLimitService" as AUTH
participant "VoucherRedemptionService" as SVC
participant "VoucherValidationService" as VALID
database "PostgreSQL\n(vouchers FOR UPDATE,\nvoucher_usages)" as DB
database "Redis\n(idempotency)" as REDIS
participant "WebhookService\n[Async]" as WEBHOOK
participant "EventPublisher" as EVENT

POS -> CTRL: POST /api/v1/external/vouchers/redeem\nHeader: X-API-Key: sv_live_xxxx\nHeader: Idempotency-Key: order-abc-123\n{voucherCode, customerRef, externalOrderId,\n orderTotal, externalBranchId}
activate CTRL

CTRL -> AUTH: Authenticate API Key + Check Rate Limit
AUTH --> CTRL: ApiKey (hoặc 401/429)

== Kiểm tra Idempotency ==

CTRL -> SVC: redeem(request, apiKey)
activate SVC

SVC -> REDIS: GET idempotency:{apiKeyId}:{idempotencyKey}
REDIS --> SVC: cachedResponse (hoặc null)

alt Đã xử lý trước đó (idempotent)
    SVC --> CTRL: cachedResponse + {isIdempotent: true}
    CTRL --> POS: 200 OK (kết quả cũ, không xử lý lại)
else Yêu cầu mới

    == Kiểm tra trùng Order ==

    SVC -> DB: SELECT * FROM voucher_usages\nWHERE voucherId=? AND externalOrderId=?
    DB --> SVC: (có hoặc không)

    alt Đơn hàng đã đổi (idempotent success)
        SVC --> CTRL: cached usage response
        CTRL --> POS: 200 OK
    else Đơn hàng mới

        == Pessimistic Lock ==

        SVC -> DB: SELECT * FROM vouchers\nWHERE id = ? FOR UPDATE
        DB --> SVC: Voucher (locked)

        == Validate ==

        SVC -> VALID: validate(voucher, customer, orderTotal, context)
        VALID --> SVC: VoucherValidateResponse

        alt Không hợp lệ
            SVC --> CTRL: {isValid: false, errorMessage}
            CTRL --> POS: 200 OK {isValid: false}
        else Hợp lệ

            == Tính toán & Lưu ==

            SVC -> SVC: calculateDiscount(discountType, orderTotal)

            SVC -> DB: INSERT INTO voucher_usages\n{voucherId, customerId, externalOrderId,\n discountAmount, orderTotal, usedAt=now()}

            SVC -> DB: UPDATE vouchers SET\ncurrentUsageCount = currentUsageCount + 1

            alt currentUsageCount >= maxUsageTotal
                SVC -> DB: UPDATE vouchers SET status = 'FULLY_USED'
            end

            == Cache Idempotency ==

            SVC -> REDIS: SET idempotency:{apiKeyId}:{idempotencyKey}\n= response, TTL=24h

            == Side Effects (Bất đồng bộ) ==

            SVC ->> WEBHOOK: dispatchRedemptionEvent(merchantUserId, usage)\n[async]

            SVC ->> EVENT: publishEvent(VoucherRedeemedEvent)\n[async → email to customer]

            SVC -> DB: INSERT INTO audit_logs\n{action=REDEEM, entityType=VoucherUsage}

            SVC --> CTRL: VoucherValidateResponse\n{isValid: true, discountAmount, ...}
            CTRL --> POS: 200 OK {isValid, discountAmount, ...}
        end
    end
end

deactivate SVC
deactivate CTRL

@enduml
```

---

## 9. POS — Hoàn tác Đổi Voucher

```plantuml
@startuml SQ-09-Reverse
!theme plain
skinparam sequenceArrowThickness 2

actor "Hệ thống POS" as POS
participant "ExternalVoucherController\nPOST /external/vouchers/usages/{id}/reverse" as CTRL
participant "ApiKeyAuthFilter" as AUTH
participant "VoucherRedemptionService" as SVC
database "PostgreSQL\n(voucher_usages, vouchers)" as DB

POS -> CTRL: POST /api/v1/external/vouchers/usages/{usageId}/reverse\nHeader: X-API-Key: sv_live_xxxx
activate CTRL

CTRL -> AUTH: Authenticate API Key
AUTH --> CTRL: ApiKey

CTRL -> SVC: reverseRedemption(usageId, apiKey)
activate SVC

SVC -> DB: SELECT * FROM voucher_usages WHERE id = ?
DB --> SVC: VoucherUsage

alt Không tìm thấy hoặc không thuộc API Key này
    SVC --> CTRL: throw ResourceNotFoundException / ForbiddenException
    CTRL --> POS: 404 / 403
else Tìm thấy

    SVC -> DB: SELECT * FROM vouchers WHERE id = ? FOR UPDATE
    DB --> SVC: Voucher (locked)

    SVC -> DB: UPDATE vouchers SET\ncurrentUsageCount = currentUsageCount - 1

    alt voucher.status = FULLY_USED
        SVC -> DB: UPDATE vouchers SET status = 'ACTIVE'
    end

    SVC -> DB: DELETE FROM voucher_usages WHERE id = ?

    SVC -> DB: INSERT INTO audit_logs\n{action=REVERSE, entityType=VoucherUsage}

    SVC --> CTRL: RedemptionReverseResponse\n{usageId, voucherId, discountAmount, status=REVERSED}
    CTRL --> POS: 200 OK
end

deactivate SVC
deactivate CTRL

@enduml
```

---

## 10. Tạo & Sử dụng API Key

```plantuml
@startuml SQ-10-ApiKey
!theme plain
skinparam sequenceArrowThickness 2

actor "Merchant / STAFF" as MERCHANT
participant "ApiKeyController\nPOST /api/v1/api-keys" as CTRL
participant "ApiKeyService" as SVC
database "PostgreSQL\n(api_keys)" as DB
database "Redis\n(rate counters)" as REDIS
actor "Hệ thống POS" as POS

== Tạo API Key ==

MERCHANT -> CTRL: POST /api/v1/api-keys\n{name, systemName, expiresAt,\n rateLimitPerMinute, rateLimitPerDay}
activate CTRL

CTRL -> SVC: createApiKey(request, currentUser)
activate SVC

SVC -> DB: SELECT COUNT(*) FROM api_keys\nWHERE createdBy = ? AND isActive = true
DB --> SVC: count

alt Đã đạt giới hạn 10 API Keys
    SVC --> CTRL: throw ConflictException
    CTRL --> MERCHANT: 409 Tối đa 10 API Key
else Trong giới hạn

    SVC -> SVC: rawKey = "sv_live_" + Base64(random 32 bytes)
    SVC -> SVC: keyHash = BCrypt.hash(rawKey)

    SVC -> DB: INSERT INTO api_keys\n{keyHash, name, systemName,\n isActive=true, createdBy=userId,\n rateLimitPerMinute, rateLimitPerDay, expiresAt}
    DB --> SVC: ApiKey

    SVC -> DB: INSERT INTO audit_logs\n{action=CREATE, entityType=ApiKey}

    note right of SVC
      rawKey chỉ trả về một lần duy nhất.
      Sau đó không thể lấy lại — chỉ lưu keyHash.
    end note

    SVC --> CTRL: ApiKeyResponse\n{id, name, systemName, key=rawKey}
    CTRL --> MERCHANT: 201 Created\n{id, name, key: "sv_live_xxxx"}
end

deactivate SVC
deactivate CTRL

== POS sử dụng API Key — Rate Limit ==

POS -> CTRL: POST /api/v1/external/vouchers/redeem\nHeader: X-API-Key: sv_live_xxxx

CTRL -> SVC: checkRateLimit(apiKeyId)
activate SVC
SVC -> REDIS: INCR api_key:{id}:minute (TTL 60s)
REDIS --> SVC: minuteCount

SVC -> REDIS: INCR api_key:{id}:day (TTL 86400s)
REDIS --> SVC: dayCount

alt minuteCount > rateLimitPerMinute\nOR dayCount > rateLimitPerDay
    SVC --> CTRL: throw RateLimitException
    CTRL --> POS: 429 Too Many Requests
else OK
    SVC --> CTRL: passed
end
deactivate SVC

== Xem thống kê API Key ==

MERCHANT -> CTRL: GET /api/v1/api-keys/{id}/usage
activate CTRL
CTRL -> SVC: getApiKeyUsage(id)
activate SVC
SVC -> REDIS: GET api_key:{id}:minute
SVC -> REDIS: GET api_key:{id}:day
REDIS --> SVC: currentCounts

SVC --> CTRL: ApiKeyUsageResponse\n{todayRequests, thisMinuteRequests,\n limitPerMinute, limitPerDay}
CTRL --> MERCHANT: 200 OK
deactivate SVC
deactivate CTRL

@enduml
```

---

## 11. Webhook — Đăng ký & Tự động Dispatch

```plantuml
@startuml SQ-11-Webhook
!theme plain
skinparam sequenceArrowThickness 2

actor "Merchant" as MERCHANT
participant "WebhookController\nPOST /api/v1/webhooks" as CTRL
participant "WebhookService" as SVC
database "PostgreSQL\n(merchant_webhooks)" as DB
participant "VoucherRedemptionService" as REDEEM
participant "External Endpoint\n(của Merchant)" as ENDPOINT

== Đăng ký Webhook ==

MERCHANT -> CTRL: POST /api/v1/webhooks\n{url: "https://merchant.com/webhook",\n secret: "my-secret",\n events: ["voucher.redeemed"]}
activate CTRL
CTRL -> SVC: createWebhook(request, currentUser)
activate SVC
SVC -> DB: INSERT INTO merchant_webhooks\n{url, secret, events, isActive=true, userId}
DB --> SVC: MerchantWebhook
SVC --> CTRL: WebhookResponse
CTRL --> MERCHANT: 201 Created
deactivate SVC
deactivate CTRL

== Webhook Dispatch khi Redeem ==

note over REDEEM
  Sau khi POS gọi POST /redeem thành công,
  VoucherRedemptionService kích hoạt dispatch.
end note

REDEEM -> SVC: dispatchRedemptionEvent(merchantUserId, voucherUsage)\n[@Async — bất đồng bộ]
activate SVC

SVC -> DB: SELECT * FROM merchant_webhooks\nWHERE userId = ?\nAND 'voucher.redeemed' = ANY(events)\nAND isActive = true
DB --> SVC: List<MerchantWebhook>

loop mỗi webhook
    SVC -> SVC: buildPayload(voucherUsage) → JSON

    SVC -> SVC: signature = HMAC-SHA256(payload, webhook.secret)

    SVC -> ENDPOINT: POST webhook.url\nHeader: X-Webhook-Event: voucher.redeemed\nHeader: X-Webhook-Signature: sha256={signature}\nHeader: X-Webhook-Timestamp: {unix timestamp}\nBody: {payload}
    activate ENDPOINT

    alt Endpoint trả 2xx
        ENDPOINT --> SVC: 200 OK
        SVC -> DB: UPDATE merchant_webhooks\nSET lastTriggeredAt = now()
    else Endpoint lỗi hoặc timeout
        ENDPOINT --> SVC: 4xx/5xx/timeout
        deactivate ENDPOINT

        note right of SVC
          Retry với exponential backoff
          Tối đa MAX_RETRIES = 3 lần
        end note

        loop retry (tối đa 3 lần)
            SVC -> SVC: sleep(exponential delay)
            SVC -> ENDPOINT: POST (retry)
        end

        alt Vẫn thất bại sau 3 lần
            SVC -> DB: UPDATE merchant_webhooks\nSET failureCount = failureCount + 1
            alt failureCount >= 10
                SVC -> DB: UPDATE merchant_webhooks\nSET isActive = false
                note right: Auto-disable webhook\nkhi quá nhiều lỗi liên tiếp
            end
        end
    end
end

deactivate SVC

@enduml
```

---

## 12. Phân quyền RBAC — Gán quyền cho Vai trò

```plantuml
@startuml SQ-12-RBAC
!theme plain
skinparam sequenceArrowThickness 2

actor "Quản trị viên\n(ADMIN)" as ADMIN
participant "RolePermissionController" as CTRL
participant "RolePermissionService" as SVC
database "PostgreSQL\n(permissions, role_permissions)" as DB
participant "SecurityConfig\n(cache permissions)" as SEC

== Xem danh sách quyền ==

ADMIN -> CTRL: GET /api/v1/permissions
activate CTRL
CTRL -> SVC: getAllPermissions()
activate SVC
SVC -> DB: SELECT * FROM permissions
DB --> SVC: List<Permission>
SVC --> CTRL: List<PermissionResponse>
CTRL --> ADMIN: 200 OK
deactivate SVC
deactivate CTRL

== Xem quyền hiện tại của vai trò ==

ADMIN -> CTRL: GET /api/v1/roles/STAFF/permissions
activate CTRL
CTRL -> SVC: getPermissionsByRole(STAFF)
activate SVC
SVC -> DB: SELECT p.* FROM permissions p\nJOIN role_permissions rp ON p.id = rp.permissionId\nWHERE rp.role = 'STAFF'
DB --> SVC: List<Permission>
SVC --> CTRL: {role: STAFF, permissions: [...]}
CTRL --> ADMIN: 200 OK
deactivate SVC
deactivate CTRL

== Gán quyền cho vai trò ==

ADMIN -> CTRL: POST /api/v1/roles/STAFF/permissions/{permissionId}
activate CTRL
CTRL -> SVC: assignPermission(STAFF, permissionId)
activate SVC

SVC -> DB: SELECT * FROM permissions WHERE id = ?
DB --> SVC: Permission

SVC -> DB: SELECT * FROM role_permissions\nWHERE role = 'STAFF' AND permissionId = ?
DB --> SVC: (rỗng — chưa có)

SVC -> DB: INSERT INTO role_permissions\n{role=STAFF, permissionId}
SVC --> CTRL: {message: "Đã gán quyền"}
CTRL --> ADMIN: 200 OK
deactivate SVC
deactivate CTRL

note over SEC
  Khi user đăng nhập tiếp theo,
  Spring Security load lại permissions
  từ role_permissions cho JWT claims.
end note

== Thu hồi quyền ==

ADMIN -> CTRL: DELETE /api/v1/roles/STAFF/permissions/{permissionId}
activate CTRL
CTRL -> SVC: revokePermission(STAFF, permissionId)
activate SVC
SVC -> DB: DELETE FROM role_permissions\nWHERE role = 'STAFF' AND permissionId = ?
SVC --> CTRL: {message: "Đã thu hồi quyền"}
CTRL --> ADMIN: 200 OK
deactivate SVC
deactivate CTRL

@enduml
```
