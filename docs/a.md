@startuml swimlane_example
title Luồng voucher — Swimlane

|Merchant|
start
:1. Tạo voucher\nPOST /vouchers;
if (codeType?) then (UNIQUE)
    :2. Sinh code pool\nPOST /codes/generate;
else (SHARED)
endif
:3. Gán khách\nPOST /customers;
:4. Gửi email\nPOST /distributions;

|Hệ thống (Async)|
:Worker pick code,\ngửi email SMTP;
:UPDATE distribution\nstatus=SENT;

|Customer|
:Nhận email,\nxem voucher code;
:5. Dùng voucher\nPOST /redeem;

|Hệ thống|
:Check voucher_customers\n(quyền);
:Check voucher_codes\n(nếu UNIQUE);
:Apply discount,\nmark used;

|Customer|
:Nhận discount;
stop
@enduml