# DFD — Luồng Voucher (SHARED & UNIQUE)

## 1. Context Diagram (Level 0)

Hệ thống nhìn như một hộp đen — ai tương tác, truyền/nhận gì.

```plantuml
@startuml dfd_context
!define PROCESS(name) rectangle "**name**" as name #LightBlue
!define STORE(name, label) database "label" as name #LightYellow
!define ENTITY(name) actor "name"

skinparam rectangle {
    BackgroundColor LightBlue
    BorderColor DarkBlue
}

title Context Diagram — Smart Voucher System

actor "Merchant\n(ADMIN/STAFF)" as M
actor "Customer" as C
actor "POS / ERP\n(External API)" as POS
actor "Email Server\n(SMTP)" as SMTP

rectangle "**Smart Voucher\nSystem**" as SYS #LightBlue

M --> SYS : tạo voucher, gán khách,\nphân phối\n(JWT auth)
SYS --> M : voucher info,\ndistribution status

C --> SYS : xem voucher\ngắn với mình
SYS --> C : voucher list

POS --> SYS : validate/redeem voucher\n(API Key)
SYS --> POS : discount amount,\nvalid/invalid

SYS --> SMTP : email + QR code
SMTP --> C : email chứa voucher

@enduml
```

---

## 2. Level 1 DFD — Các process chính

```plantuml
@startuml dfd_level1
title Level 1 DFD — Các luồng dữ liệu chính

skinparam rectangle {
    BackgroundColor LightBlue
    BorderColor DarkBlue
    RoundCorner 20
}
skinparam database {
    BackgroundColor LightYellow
    BorderColor Orange
}

actor "Merchant" as M
actor "Customer" as C
actor "POS / ERP" as POS

rectangle "**1.0**\nCreate Voucher" as P1
rectangle "**2.0**\nGenerate Unique Codes\n(UNIQUE only)" as P2
rectangle "**3.0**\nAssign Customer\n(voucher_customers)" as P3
rectangle "**4.0**\nDistribute\n(send email)" as P4
rectangle "**5.0**\nValidate / Redeem" as P5

database "D1\nvouchers" as D1
database "D2\nvoucher_codes" as D2
database "D3\nvoucher_customers" as D3
database "D4\nvoucher_distributions" as D4
database "D5\nvoucher_usages" as D5

' --- Create voucher ---
M --> P1 : voucher data\n(code, codeType, discount...)
P1 --> D1 : INSERT voucher

' --- Generate codes ---
M --> P2 : voucherId, quantity
P2 <-- D1 : check codeType=UNIQUE
P2 --> D2 : INSERT N codes\n(customer_id=NULL, used=false)

' --- Assign ---
M --> P3 : voucherId, customerIds
P3 --> D3 : INSERT assignments

' --- Distribute ---
M --> P4 : voucherId, customerId, channel
P4 --> D4 : INSERT distribution (PENDING)
P4 <--> D2 : Lấy code chưa gán,\nUPDATE customer_id
P4 ..> C : email + QR (SMTP async)
P4 --> D4 : UPDATE status=SENT/FAILED

' --- Redeem ---
POS --> P5 : voucherCode, customerRef, orderTotal
P5 <-- D2 : tìm unique code
P5 <-- D1 : fallback shared code
P5 <-- D3 : check assignment
P5 --> D5 : INSERT voucher_usage
P5 --> D1 : UPDATE current_usage_count++
P5 --> D2 : UPDATE used=true (nếu UNIQUE)
P5 --> POS : {valid, discountAmount}

' Customer có thể xem
C --> P5 : validate (preview discount)

@enduml
```

---

## 3. Level 2 DFD — Process 5.0 "Validate / Redeem" (chi tiết)

Process phức tạp nhất — resolve code, validate, apply discount.

```plantuml
@startuml dfd_level2_redeem
title Level 2 DFD — Redeem Process (5.0)

skinparam rectangle {
    BackgroundColor LightBlue
    BorderColor DarkBlue
    RoundCorner 15
}
skinparam database {
    BackgroundColor LightYellow
    BorderColor Orange
}

actor "POS / ERP" as POS

rectangle "**5.1**\nIdempotency\nCheck" as P51
rectangle "**5.2**\nResolve Voucher\nCode" as P52
rectangle "**5.3**\nValidate\nOwnership\n& Used" as P53
rectangle "**5.4**\nLock Parent\n& Validate\nBusiness Rules" as P54
rectangle "**5.5**\nCalculate\nDiscount" as P55
rectangle "**5.6**\nPersist Usage\n& Update State" as P56
rectangle "**5.7**\nDispatch\nWebhook + Event" as P57

database "D1\nvouchers" as D1
database "D2\nvoucher_codes" as D2
database "D3\nvoucher_customers" as D3
database "D5\nvoucher_usages" as D5
database "D6\nRedis\nidempotency cache" as D6

POS --> P51 : {voucherCode, customerRef,\norderTotal, externalOrderId,\nidempotencyKey}

P51 <--> D6 : GET cacheKey
P51 --> POS : cached response\n(nếu hit)

P51 --> P52 : inputCode (upper)

P52 <-- D2 : SELECT by code\n(tìm UNIQUE trước)
P52 <-- D1 : SELECT by code\n(fallback SHARED)
P52 --> P53 : {voucherCode,\nuniqueCode?}

P53 --> P53 : check used=false\ncheck customer ownership
P53 <-- D3 : check assignment\n(nếu UNIQUE/private)

P53 --> P54 : voucher resolved

P54 <--> D1 : SELECT FOR UPDATE\n(pessimistic lock)
P54 --> P54 : check status,\nvalidFrom/Until,\nmaxUsageTotal

P54 --> P55 : voucher + orderTotal
P55 --> P56 : discountAmount

P56 --> D5 : INSERT voucher_usages
P56 --> D1 : UPDATE current_usage_count++\n(FULLY_USED if reach limit)
P56 --> D2 : UPDATE used=true,\nusedAt=NOW\n(nếu UNIQUE)

P56 --> P57 : usage record
P57 ..> POS : webhook\n(async, merchant endpoint)
P57 --> D6 : SET cacheKey\n(idempotency TTL=24h)

P56 --> POS : {valid:true, voucherCode,\ndiscountAmount, message}

@enduml
```

---

## 4. Data Flow — SHARED vs UNIQUE (so sánh)

```plantuml
@startuml dfd_compare
title Data Flow Comparison — SHARED vs UNIQUE

skinparam defaultFontSize 11

package "SHARED Voucher" {
    rectangle "Customer\ncó code" as S_IN
    rectangle "Validate code\nin vouchers" as S_P1
    rectangle "Check\nisPublic?" as S_P2
    rectangle "Apply discount" as S_P3
    database "vouchers\n(count++)" as S_D1
    database "voucher_usages" as S_D2
    
    S_IN --> S_P1
    S_P1 --> S_P2
    S_P2 --> S_P3
    S_P3 --> S_D1 : UPDATE count
    S_P3 --> S_D2 : INSERT
}

package "UNIQUE Voucher" {
    rectangle "Customer\ncó unique\ncode" as U_IN
    rectangle "Lookup\nin voucher_codes" as U_P1
    rectangle "Check\nused + ownership" as U_P2
    rectangle "Load parent\nvoucher" as U_P3
    rectangle "Apply discount\n+ mark used" as U_P4
    database "voucher_codes\n(used=true)" as U_D1
    database "vouchers\n(count++)" as U_D2
    database "voucher_usages" as U_D3
    
    U_IN --> U_P1
    U_P1 --> U_P2
    U_P2 --> U_P3
    U_P3 --> U_P4
    U_P4 --> U_D1
    U_P4 --> U_D2
    U_P4 --> U_D3
}

@enduml
```

---

## 5. Chú giải ký hiệu DFD

| Ký hiệu | Ý nghĩa |
|---------|---------|
| `actor` | **External Entity** — thực thể ngoài hệ thống (người, hệ thống khác) |
| `rectangle` tròn góc | **Process** — xử lý dữ liệu, đánh số 1.0, 2.0, 5.1... |
| `database` | **Data Store** — bảng/cache lưu trữ dữ liệu (D1, D2...) |
| `-->` | **Data Flow** — hướng dữ liệu di chuyển |
| `..>` | **Async Flow** — dữ liệu truyền bất đồng bộ (email, webhook) |

## 6. Mapping Process ↔ Code

| Process | Service class | Endpoint |
|---------|--------------|----------|
| 1.0 Create Voucher | `VoucherService.create()` | `POST /api/v1/vouchers` |
| 2.0 Generate Codes | `VoucherCodeService.generateCodes()` | `POST /vouchers/{id}/codes/generate` |
| 3.0 Assign Customer | `VoucherService.assignCustomers()` | `POST /vouchers/{id}/customers` |
| 4.0 Distribute | `DistributionService` + `DistributionProcessor` | `POST /api/v1/distributions` |
| 5.0 Redeem | `VoucherRedemptionService.redeem()` | `POST /external/vouchers/redeem` |
| 5.1–5.7 | Các bước bên trong `redeem()` | (cùng endpoint) |
