# Use Case Diagram — Smart Voucher System

> **Ngôn ngữ**: PlantUML  
> **Cập nhật**: 2026-03-31

---

## 1. Tổng quan Actor

| Actor | Mô tả | Xác thực |
|-------|-------|----------|
| **Quản trị viên (ADMIN)** | Toàn quyền hệ thống, quản lý user, phân quyền | JWT |
| **Nhân viên (STAFF)** | Tạo/quản lý voucher, campaign, customer, API Key | JWT |
| **Merchant (USER)** | Giống STAFF, có thể tự đăng ký, không có quyền xóa | JWT |
| **Hệ thống ngoài (POS/E-commerce)** | Validate, redeem voucher, tra cứu khách hàng | API Key (`X-API-Key`) |
| **Khách (Guest)** | Đăng ký tài khoản, quên mật khẩu | Không |

---

## 2. Sơ đồ tổng quan — Các Actor và Subsystem

```plantuml
@startuml UC-00-Overview
!theme plain
skinparam actorStyle awesome
skinparam packageStyle rectangle
skinparam ArrowColor #333333
skinparam ActorBorderColor #555555
skinparam UsecaseBorderColor #336699
skinparam UsecaseBackgroundColor #EEF4FF
left to right direction

actor "Khách\n(Guest)" as GUEST #lightgray
actor "Nhân viên\n(STAFF)" as STAFF #lightblue
actor "Merchant\n(USER)" as MERCHANT #lightyellow
actor "Quản trị viên\n(ADMIN)" as ADMIN #lightcoral
actor "Hệ thống ngoài\n(POS / E-commerce)" as EXTERNAL #lightgreen

rectangle "Smart Voucher System" {

  package "Xác thực & Tài khoản" as PKG_AUTH {
    usecase "Đăng ký / Đăng nhập" as UC_AUTH
    usecase "Quên / Đổi mật khẩu" as UC_PWD
  }

  package "Quản lý Chiến dịch" as PKG_CAMPAIGN {
    usecase "CRUD Campaign" as UC_CAMPAIGN
    usecase "Theo dõi thống kê" as UC_CAMPAIGN_STATS
  }

  package "Quản lý Voucher" as PKG_VOUCHER {
    usecase "CRUD Voucher" as UC_VOUCHER
    usecase "Gán khách hàng" as UC_VOUCHER_ASSIGN
    usecase "Sinh mã unique / QR" as UC_VOUCHER_CODES
  }

  package "Phân phối Voucher" as PKG_DIST {
    usecase "Gửi voucher (Email/SMS)" as UC_DIST
    usecase "Theo dõi trạng thái gửi" as UC_DIST_STATUS
  }

  package "Quản lý Khách hàng" as PKG_CUSTOMER {
    usecase "CRUD Khách hàng" as UC_CUSTOMER
    usecase "Import/Export CSV" as UC_CUSTOMER_CSV
  }

  package "API Key & Tích hợp" as PKG_APIKEY {
    usecase "Quản lý API Key" as UC_APIKEY
    usecase "Quản lý Webhook" as UC_WEBHOOK
  }

  package "External API\n(dành cho POS)" as PKG_EXTERNAL {
    usecase "Validate Voucher" as UC_VALIDATE
    usecase "Redeem Voucher" as UC_REDEEM
    usecase "Tra cứu Voucher / KH" as UC_LOOKUP
  }

  package "Quản trị Hệ thống" as PKG_ADMIN {
    usecase "Quản lý Người dùng" as UC_USER_MGMT
    usecase "Phân quyền RBAC" as UC_RBAC
    usecase "Xem Audit / Request Log" as UC_LOGS
  }

  package "Dashboard & Báo cáo" as PKG_DASHBOARD {
    usecase "Xem tổng quan & xu hướng" as UC_DASHBOARD
  }
}

GUEST      --> UC_AUTH
GUEST      --> UC_PWD

STAFF      --> UC_AUTH
STAFF      --> UC_PWD
STAFF      --> UC_CAMPAIGN
STAFF      --> UC_CAMPAIGN_STATS
STAFF      --> UC_VOUCHER
STAFF      --> UC_VOUCHER_ASSIGN
STAFF      --> UC_VOUCHER_CODES
STAFF      --> UC_DIST
STAFF      --> UC_DIST_STATUS
STAFF      --> UC_CUSTOMER
STAFF      --> UC_CUSTOMER_CSV
STAFF      --> UC_APIKEY
STAFF      --> UC_WEBHOOK
STAFF      --> UC_DASHBOARD

MERCHANT   --> UC_AUTH
MERCHANT   --> UC_PWD
MERCHANT   --> UC_CAMPAIGN
MERCHANT   --> UC_CAMPAIGN_STATS
MERCHANT   --> UC_VOUCHER
MERCHANT   --> UC_VOUCHER_ASSIGN
MERCHANT   --> UC_VOUCHER_CODES
MERCHANT   --> UC_DIST
MERCHANT   --> UC_DIST_STATUS
MERCHANT   --> UC_CUSTOMER
MERCHANT   --> UC_CUSTOMER_CSV
MERCHANT   --> UC_APIKEY
MERCHANT   --> UC_WEBHOOK
MERCHANT   --> UC_DASHBOARD

ADMIN      --> UC_AUTH
ADMIN      --> UC_PWD
ADMIN      --> UC_CAMPAIGN
ADMIN      --> UC_CAMPAIGN_STATS
ADMIN      --> UC_VOUCHER
ADMIN      --> UC_VOUCHER_ASSIGN
ADMIN      --> UC_VOUCHER_CODES
ADMIN      --> UC_DIST
ADMIN      --> UC_DIST_STATUS
ADMIN      --> UC_CUSTOMER
ADMIN      --> UC_CUSTOMER_CSV
ADMIN      --> UC_APIKEY
ADMIN      --> UC_WEBHOOK
ADMIN      --> UC_DASHBOARD
ADMIN      --> UC_USER_MGMT
ADMIN      --> UC_RBAC
ADMIN      --> UC_LOGS

EXTERNAL   --> UC_VALIDATE
EXTERNAL   --> UC_REDEEM
EXTERNAL   --> UC_LOOKUP

@enduml
```

---

## 3. Xác thực & Quản lý tài khoản

```plantuml
@startuml UC-01-Auth
!theme plain
skinparam actorStyle awesome
skinparam packageStyle rectangle
left to right direction

actor "Khách\n(Guest)" as GUEST
actor "Nhân viên / Merchant" as USER
actor "Quản trị viên\n(ADMIN)" as ADMIN

rectangle "Xác thực & Tài khoản" {
  usecase "Đăng ký tài khoản (STAFF)" as UC_REG_STAFF
  usecase "Đăng ký Merchant" as UC_REG_MERCHANT
  usecase "Xác minh email (OTP)" as UC_VERIFY_EMAIL
  usecase "Đăng nhập" as UC_LOGIN
  usecase "Làm mới Access Token" as UC_REFRESH
  usecase "Xem thông tin bản thân" as UC_ME
  usecase "Quên mật khẩu" as UC_FORGOT
  usecase "Xác thực OTP reset" as UC_VERIFY_OTP
  usecase "Đặt lại mật khẩu" as UC_RESET_PWD
  usecase "Đổi mật khẩu" as UC_CHANGE_PWD

  usecase "Duyệt tài khoản" as UC_APPROVE
  usecase "Từ chối tài khoản" as UC_REJECT
  usecase "Admin reset mật khẩu user" as UC_ADMIN_RESET

  UC_REG_STAFF .> UC_VERIFY_EMAIL : <<include>>
  UC_REG_MERCHANT .> UC_VERIFY_EMAIL : <<include>>
  UC_FORGOT .> UC_VERIFY_OTP : <<include>>
  UC_VERIFY_OTP .> UC_RESET_PWD : <<include>>
}

GUEST  --> UC_REG_STAFF
GUEST  --> UC_REG_MERCHANT
GUEST  --> UC_LOGIN
GUEST  --> UC_FORGOT

USER   --> UC_VERIFY_EMAIL
USER   --> UC_LOGIN
USER   --> UC_REFRESH
USER   --> UC_ME
USER   --> UC_CHANGE_PWD

ADMIN  --> UC_APPROVE
ADMIN  --> UC_REJECT
ADMIN  --> UC_ADMIN_RESET

@enduml
```

---

## 4. Quản lý Chiến dịch & Voucher

```plantuml
@startuml UC-02-CampaignVoucher
!theme plain
skinparam actorStyle awesome
skinparam packageStyle rectangle
left to right direction

actor "Nhân viên / Merchant\n(STAFF / USER)" as STAFF
actor "Quản trị viên\n(ADMIN)" as ADMIN

rectangle "Quản lý Chiến dịch" {
  usecase "Tạo chiến dịch" as UC_C_CREATE
  usecase "Xem danh sách chiến dịch" as UC_C_LIST
  usecase "Xem chi tiết chiến dịch" as UC_C_DETAIL
  usecase "Cập nhật chiến dịch" as UC_C_UPDATE
  usecase "Thay đổi trạng thái\n(DRAFT→ACTIVE→PAUSED→ENDED)" as UC_C_STATUS
  usecase "Xem thống kê chiến dịch" as UC_C_STATS
  usecase "Xem voucher của chiến dịch" as UC_C_VOUCHERS
  usecase "Nhân bản chiến dịch" as UC_C_CLONE
  usecase "Xóa chiến dịch" as UC_C_DELETE
}

rectangle "Quản lý Voucher" {
  usecase "Tạo voucher" as UC_V_CREATE
  usecase "Xem danh sách voucher" as UC_V_LIST
  usecase "Xem chi tiết voucher" as UC_V_DETAIL
  usecase "Cập nhật voucher" as UC_V_UPDATE
  usecase "Tạm dừng voucher" as UC_V_PAUSE
  usecase "Kích hoạt lại voucher" as UC_V_RESUME
  usecase "Gán khách hàng (đơn lẻ)" as UC_V_ASSIGN
  usecase "Gán khách hàng (hàng loạt)" as UC_V_BULK_ASSIGN
  usecase "Thu hồi gán khách hàng" as UC_V_REVOKE
  usecase "Xem khách hàng của voucher" as UC_V_CUSTOMERS
  usecase "Sinh mã unique" as UC_V_GEN_CODES
  usecase "Xem danh sách mã unique" as UC_V_LIST_CODES
  usecase "Xem lịch sử dùng voucher" as UC_V_USAGES
  usecase "Gửi voucher hàng loạt" as UC_V_BULK_DIST
  usecase "Nhân bản voucher" as UC_V_CLONE
  usecase "Xuất CSV voucher" as UC_V_EXPORT
  usecase "Xuất CSV lịch sử dùng" as UC_V_EXPORT_USAGE
  usecase "Xóa voucher" as UC_V_DELETE

  UC_V_CREATE .> UC_C_CREATE : <<extend>>\n(gán campaign tùy chọn)
  UC_V_BULK_DIST .> UC_V_ASSIGN : <<include>>
}

STAFF --> UC_C_CREATE
STAFF --> UC_C_LIST
STAFF --> UC_C_DETAIL
STAFF --> UC_C_UPDATE
STAFF --> UC_C_STATUS
STAFF --> UC_C_STATS
STAFF --> UC_C_VOUCHERS
STAFF --> UC_C_CLONE

STAFF --> UC_V_CREATE
STAFF --> UC_V_LIST
STAFF --> UC_V_DETAIL
STAFF --> UC_V_UPDATE
STAFF --> UC_V_PAUSE
STAFF --> UC_V_RESUME
STAFF --> UC_V_ASSIGN
STAFF --> UC_V_BULK_ASSIGN
STAFF --> UC_V_REVOKE
STAFF --> UC_V_CUSTOMERS
STAFF --> UC_V_GEN_CODES
STAFF --> UC_V_LIST_CODES
STAFF --> UC_V_USAGES
STAFF --> UC_V_BULK_DIST
STAFF --> UC_V_CLONE
STAFF --> UC_V_EXPORT
STAFF --> UC_V_EXPORT_USAGE

ADMIN --> UC_C_DELETE
ADMIN --> UC_V_DELETE

@enduml
```

---

## 5. Khách hàng & Phân phối

```plantuml
@startuml UC-03-CustomerDistribution
!theme plain
skinparam actorStyle awesome
skinparam packageStyle rectangle
left to right direction

actor "Nhân viên / Merchant\n(STAFF / USER)" as STAFF
actor "Quản trị viên\n(ADMIN)" as ADMIN

rectangle "Quản lý Khách hàng" {
  usecase "Tạo khách hàng" as UC_CU_CREATE
  usecase "Xem danh sách khách hàng" as UC_CU_LIST
  usecase "Xem chi tiết khách hàng" as UC_CU_DETAIL
  usecase "Tìm theo External ID" as UC_CU_BY_EXT
  usecase "Cập nhật khách hàng" as UC_CU_UPDATE
  usecase "Xem voucher của khách" as UC_CU_VOUCHERS
  usecase "Xem lịch sử dùng của khách" as UC_CU_USAGES
  usecase "Vô hiệu hóa khách hàng" as UC_CU_DEACTIVATE
  usecase "Kích hoạt lại khách hàng" as UC_CU_ACTIVATE
  usecase "Xóa khách hàng" as UC_CU_DELETE
  usecase "Nhập CSV khách hàng" as UC_CU_IMPORT
  usecase "Xuất CSV khách hàng" as UC_CU_EXPORT
}

rectangle "Phân phối Voucher" {
  usecase "Tạo lệnh phân phối" as UC_D_CREATE
  usecase "Xem danh sách phân phối" as UC_D_LIST
  usecase "Xem chi tiết phân phối" as UC_D_DETAIL
  usecase "Hủy phân phối (PENDING)" as UC_D_CANCEL
  usecase "Thử lại phân phối (FAILED)" as UC_D_RETRY
  usecase "Gửi lại voucher" as UC_D_RESEND

  UC_D_CREATE .> UC_CU_DETAIL : <<include>>\n(validate customer)
}

STAFF --> UC_CU_CREATE
STAFF --> UC_CU_LIST
STAFF --> UC_CU_DETAIL
STAFF --> UC_CU_BY_EXT
STAFF --> UC_CU_UPDATE
STAFF --> UC_CU_VOUCHERS
STAFF --> UC_CU_USAGES
STAFF --> UC_CU_DEACTIVATE
STAFF --> UC_CU_ACTIVATE
STAFF --> UC_CU_IMPORT
STAFF --> UC_CU_EXPORT

STAFF --> UC_D_CREATE
STAFF --> UC_D_LIST
STAFF --> UC_D_DETAIL
STAFF --> UC_D_CANCEL
STAFF --> UC_D_RETRY
STAFF --> UC_D_RESEND

ADMIN --> UC_CU_DELETE

@enduml
```

---

## 6. External API — POS / E-commerce

```plantuml
@startuml UC-04-ExternalAPI
!theme plain
skinparam actorStyle awesome
skinparam packageStyle rectangle
left to right direction

actor "Hệ thống ngoài\n(POS / E-commerce)" as POS
actor "Nhân viên / Merchant" as MERCHANT

rectangle "Quản lý API Key" {
  usecase "Tạo API Key" as UC_AK_CREATE
  usecase "Xem danh sách API Key" as UC_AK_LIST
  usecase "Xem chi tiết & thống kê" as UC_AK_DETAIL
  usecase "Cập nhật Rate Limit" as UC_AK_RATE
  usecase "Vô hiệu hóa API Key" as UC_AK_DEACTIVATE
}

rectangle "Quản lý Webhook" {
  usecase "Đăng ký Webhook" as UC_WH_CREATE
  usecase "Xem danh sách Webhook" as UC_WH_LIST
  usecase "Cập nhật Webhook" as UC_WH_UPDATE
  usecase "Xóa Webhook" as UC_WH_DELETE
}

rectangle "External API\n(xác thực bằng X-API-Key)" {
  usecase "Validate Voucher\n(kiểm tra, không tiêu lượt)" as UC_E_VALIDATE
  usecase "Redeem Voucher\n(đổi voucher, tiêu lượt)" as UC_E_REDEEM
  usecase "Hoàn tác đổi voucher" as UC_E_REVERSE
  usecase "Giải mã QR Token" as UC_E_QR
  usecase "Tra cứu khách hàng\n(phone / email / externalId)" as UC_E_LOOKUP_CUST
  usecase "Lấy voucher khả dụng của khách" as UC_E_AVAIL_VOUCHERS

  UC_E_REDEEM .> UC_E_VALIDATE : <<include>>
}

MERCHANT --> UC_AK_CREATE
MERCHANT --> UC_AK_LIST
MERCHANT --> UC_AK_DETAIL
MERCHANT --> UC_AK_RATE
MERCHANT --> UC_AK_DEACTIVATE
MERCHANT --> UC_WH_CREATE
MERCHANT --> UC_WH_LIST
MERCHANT --> UC_WH_UPDATE
MERCHANT --> UC_WH_DELETE

POS --> UC_E_VALIDATE
POS --> UC_E_REDEEM
POS --> UC_E_REVERSE
POS --> UC_E_QR
POS --> UC_E_LOOKUP_CUST
POS --> UC_E_AVAIL_VOUCHERS

@enduml
```

---

## 7. Quản trị Hệ thống (Admin)

```plantuml
@startuml UC-05-Admin
!theme plain
skinparam actorStyle awesome
skinparam packageStyle rectangle
left to right direction

actor "Quản trị viên\n(ADMIN)" as ADMIN

rectangle "Quản lý Người dùng" {
  usecase "Xem danh sách người dùng" as UC_U_LIST
  usecase "Xem chi tiết người dùng" as UC_U_DETAIL
  usecase "Cập nhật thông tin người dùng" as UC_U_UPDATE
  usecase "Duyệt tài khoản PENDING" as UC_U_APPROVE
  usecase "Từ chối tài khoản" as UC_U_REJECT
  usecase "Admin reset mật khẩu" as UC_U_RESET_PWD

  UC_U_APPROVE .> UC_U_DETAIL : <<include>>
  UC_U_REJECT  .> UC_U_DETAIL : <<include>>
}

rectangle "Phân quyền RBAC" {
  usecase "Xem danh sách quyền" as UC_R_LIST_PERM
  usecase "Xem quyền của vai trò" as UC_R_GET_ROLE_PERM
  usecase "Gán quyền cho vai trò" as UC_R_ASSIGN
  usecase "Thu hồi quyền vai trò" as UC_R_REVOKE
}

rectangle "Audit & Log" {
  usecase "Xem Audit Log\n(lịch sử thay đổi dữ liệu)" as UC_L_AUDIT
  usecase "Xem Request Log\n(lịch sử API call từ POS)" as UC_L_REQUEST
}

rectangle "Dashboard Admin" {
  usecase "Xem tổng quan hệ thống" as UC_D_OVERVIEW
  usecase "Xem xu hướng sử dụng" as UC_D_TREND
  usecase "Thống kê theo chi nhánh" as UC_D_BRANCH
  usecase "Xem tổng số Merchant hoạt động" as UC_D_MERCHANT
}

ADMIN --> UC_U_LIST
ADMIN --> UC_U_DETAIL
ADMIN --> UC_U_UPDATE
ADMIN --> UC_U_APPROVE
ADMIN --> UC_U_REJECT
ADMIN --> UC_U_RESET_PWD

ADMIN --> UC_R_LIST_PERM
ADMIN --> UC_R_GET_ROLE_PERM
ADMIN --> UC_R_ASSIGN
ADMIN --> UC_R_REVOKE

ADMIN --> UC_L_AUDIT
ADMIN --> UC_L_REQUEST

ADMIN --> UC_D_OVERVIEW
ADMIN --> UC_D_TREND
ADMIN --> UC_D_BRANCH
ADMIN --> UC_D_MERCHANT

@enduml
```
