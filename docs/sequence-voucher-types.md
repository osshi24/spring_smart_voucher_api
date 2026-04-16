# Sequence Diagrams — SHARED vs UNIQUE Voucher

## 1. SHARED Voucher (Public, dùng chung)

Một mã code duy nhất, ai biết code đều có thể dùng. Giới hạn bởi `maxUsageTotal` và `maxUsagePerCustomer`.

```plantuml
@startuml shared_voucher_flow
title SHARED Voucher — Tạo, Phát hành, Sử dụng

actor Merchant
actor Customer
participant "VoucherController" as VC
participant "DistributionService" as DS
participant "DistributionProcessor\n(async)" as DP
participant "EmailService" as ES
participant "VoucherRedemptionService" as RS
database "DB" as DB

== 1. Tạo voucher SHARED ==
Merchant -> VC: POST /api/v1/vouchers\n{code:"SUMMER24", codeType:"SHARED",\nisPublic:true, maxUsageTotal:1000}
VC -> DB: INSERT vouchers
VC --> Merchant: 201 Created {id:10, code:"SUMMER24"}

== 2. (Tuỳ chọn) Phân phối email cho khách ==
Merchant -> VC: POST /api/v1/distributions\n{voucherId:10, customerId:1, channel:"EMAIL"}
VC -> DS: createInternal()
DS -> DB: INSERT voucher_distributions (PENDING)
DS -> DS: registerAfterCommit()
DS --> Merchant: 201 Created (PENDING)

note over DS, DP #lightyellow
  Sau khi transaction commit
  → trigger async worker
end note

DS -> DP: processAsync(id)
activate DP
DP -> DB: SELECT voucher_distributions
DP -> ES: sendVoucherEmail(SUMMER24, qrCode)
ES --> DP: sent
DP -> DB: UPDATE status=SENT
deactivate DP

== 3. Customer dùng voucher ==
Customer -> RS: POST /external/vouchers/redeem\n{voucherCode:"SUMMER24", customerRef:"42", orderTotal:500000}
RS -> DB: SELECT voucher_codes WHERE code='SUMMER24'
note right: Không tìm thấy\n(SHARED không có entry trong voucher_codes)
RS -> DB: SELECT vouchers WHERE code='SUMMER24'
RS -> DB: SELECT ... FOR UPDATE (lock voucher)
RS -> RS: Check status, validity,\nminOrderValue, maxUsageTotal
RS -> DB: INSERT voucher_usages
RS -> DB: UPDATE vouchers SET current_usage_count+1
RS --> Customer: 200 {valid:true, voucherCode:"SUMMER24",\ndiscountAmount:50000, message:"Voucher redeemed successfully"}

== 4. Customer khác cũng dùng được ==
actor "Customer 2" as C2
C2 -> RS: POST /external/vouchers/redeem\n{voucherCode:"SUMMER24", customerRef:"99"}
RS --> C2: 200 OK (same code vẫn dùng được)

@enduml
```

---

## 2. UNIQUE Voucher (Private, mỗi customer một mã riêng)

Một voucher "cha" có N mã con sinh sẵn. Mỗi mã dùng được **1 lần**, gán cho **1 customer**.

```plantuml
@startuml unique_voucher_flow
title UNIQUE Voucher — Tạo, Sinh code, Gán khách, Phát hành, Sử dụng

actor Merchant
actor Customer
participant "VoucherController" as VC
participant "VoucherCodeService" as VCS
participant "DistributionService" as DS
participant "DistributionProcessor\n(async)" as DP
participant "EmailService" as ES
participant "VoucherRedemptionService" as RS
database "DB" as DB

== 1. Tạo voucher UNIQUE ==
Merchant -> VC: POST /api/v1/vouchers\n{code:"VIP2024", codeType:"UNIQUE", isPublic:false}
VC -> DB: INSERT vouchers (parent)
VC --> Merchant: 201 Created {id:20}

== 2. Sinh pool unique codes ==
Merchant -> VC: POST /api/v1/vouchers/20/codes/generate\n?quantity=100
VC -> VCS: generateCodes(20, 100)
loop 100 lần
    VCS -> VCS: random 8-char Base62 (vd: "aB3xK9mP")
    VCS -> DB: INSERT voucher_codes\n(voucher_id=20, code="aB3xK9mP",\ncustomer_id=NULL, used=false)
end
VCS --> Merchant: {generated:100}

== 3. Gán quyền cho khách (nếu private) ==
Merchant -> VC: POST /api/v1/vouchers/20/customers\n{customerIds:[1, 2, 3]}
VC -> DB: INSERT voucher_customers x3
VC --> Merchant: 200 OK

== 4. Phân phối email cho khách ==
Merchant -> DS: POST /api/v1/distributions\n{voucherId:20, customerId:1, channel:"EMAIL"}
DS -> DB: INSERT voucher_distributions (PENDING)
DS -> DS: registerAfterCommit()
DS --> Merchant: 201 Created (PENDING)

DS -> DP: processAsync(id)
activate DP
DP -> DB: SELECT voucher_codes\nWHERE voucher_id=20 AND customer_id IS NULL\nLIMIT 1
DP -> DB: UPDATE voucher_codes\nSET customer_id=1 (gán unique code cho customer)
DP -> ES: sendVoucherEmail("aB3xK9mP", qrCode)
note right: Email chứa code riêng\ncủa customer, không phải\nvoucher.code "VIP2024"
ES --> DP: sent
DP -> DB: UPDATE distribution status=SENT
deactivate DP

== 5. Customer dùng voucher (unique code) ==
Customer -> RS: POST /external/vouchers/redeem\n{voucherCode:"aB3xK9mP", customerRef:"1"}

RS -> DB: SELECT voucher_codes WHERE code='aB3xK9mP'
note right #lightgreen: Tìm thấy!\nLấy parent voucher

RS -> RS: Check used==false
RS -> RS: Check customer_id == 1 (ownership)
RS -> DB: SELECT vouchers (parent) FOR UPDATE
RS -> RS: Check status, validity, minOrderValue
RS -> DB: INSERT voucher_usages
RS -> DB: UPDATE vouchers current_usage_count+1
RS -> DB: UPDATE voucher_codes\nSET used=true, used_at=NOW()
RS --> Customer: 200 {valid:true, voucherCode:"aB3xK9mP",\ndiscountAmount:50000, message:"Voucher redeemed successfully"}

== 6. Dùng lại cùng code → reject ==
Customer -> RS: POST /external/vouchers/redeem\n{voucherCode:"aB3xK9mP"}
RS -> DB: SELECT voucher_codes
RS -> RS: used==true → throw ConflictException
RS --> Customer: 409 "Voucher đã được sử dụng"

== 7. Customer khác dùng code này → reject ==
actor "Customer 2" as C2
C2 -> RS: POST /external/vouchers/redeem\n{voucherCode:"aB3xK9mP", customerRef:"99"}
RS -> DB: SELECT voucher_codes
RS -> RS: customer_id(1) != request customer(99)
RS --> C2: 403 "Voucher này không thuộc về khách hàng"

@enduml
```

---

## 3. So sánh nhanh

| Tiêu chí | SHARED | UNIQUE |
|----------|--------|--------|
| Số code/voucher | 1 (code cha) | N (pool con) |
| Ai dùng được | Mọi người biết code | Chỉ customer được gán |
| Dùng mấy lần | Theo `maxUsageTotal` | 1 lần / code |
| Cần `voucher_customers`? | Không (nếu `isPublic=true`) | Có (thường private) |
| Cần generate codes? | Không | Có (`/codes/generate`) |
| Use case | Flash sale công khai, banner | Voucher VIP, cá nhân hóa |
| Gửi trong email | `voucher.code` | Unique code riêng (`voucher_codes.code`) |

---

## 4. Điểm quan trọng về luồng async

Ở bước 4 (phân phối), `POST /distributions` trả về `201 PENDING` **ngay lập tức**.
Email được gửi bởi `distributionTaskExecutor` thread pool (5 core, 10 max).

FE muốn biết kết quả gửi → poll `GET /api/v1/distributions/{id}` cho đến khi status = `SENT` hoặc `FAILED`.
