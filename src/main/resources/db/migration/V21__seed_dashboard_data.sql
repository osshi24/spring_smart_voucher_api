-- =============================================
-- SEED DATA CHO DASHBOARD TESTING
-- 20 customers, 2 vouchers, ~150 usages trải dài Jan-Mar 2026
-- =============================================

-- 1. Thêm 20 customers mới
INSERT INTO customers (external_id, full_name, email, phone, is_active, created_by)
SELECT
    'M01-CUS-' || lpad((2 + gs)::text, 3, '0'),
    (ARRAY[
        'Nguyen Van An', 'Tran Thi Bich', 'Le Van Cuong', 'Pham Thi Dung', 'Hoang Van Em',
        'Nguyen Thi Phuong', 'Bui Van Giang', 'Do Thi Huong', 'Vu Van Khanh', 'Dang Thi Lan',
        'Nguyen Van Minh', 'Tran Thi Ngoc', 'Le Van Phat', 'Pham Van Quang', 'Hoang Thi Ro',
        'Dinh Van Son', 'Ngo Thi Thu', 'Duong Van Uyen', 'Ly Thi Vy', 'Cao Van Xuan'
    ])[gs],
    'm01cus' || (2 + gs) || '@test.com',
    '09011100' || lpad(gs::text, 2, '0'),
    true,
    (SELECT id FROM users WHERE username = 'merchant01')
FROM generate_series(1, 20) gs;

-- 2. Campaign cho Q1 2026
INSERT INTO campaigns (name, description, budget, start_date, end_date, status, created_by)
VALUES ('Thuong khach hang than thiet Q1 2026',
        'Chuong trinh khuyen mai danh rieng cho khach hang than thiet Q1/2026',
        30000000.00, '2026-01-01', '2026-03-31', 'ACTIVE',
        (SELECT id FROM users WHERE username = 'merchant01'));

-- 3. Vouchers cho Q1 2026 (max_usage_per_customer = NULL = không giới hạn)
INSERT INTO vouchers (code, campaign_id, description, discount_type, discount_value, max_discount_amount,
                      min_order_value, applicable_products, applicable_categories, applicable_branches,
                      max_usage_total, max_usage_per_customer, is_public, valid_from, valid_until, status, created_by)
VALUES
('LOYAL10',
 (SELECT id FROM campaigns WHERE name = 'Thuong khach hang than thiet Q1 2026'),
 'Giam 10% toi da 50K cho khach hang than thiet', 'PERCENTAGE', 10.00, 50000.00, 100000.00,
 '[]', '[]', '[]', 2000, NULL, true, '2026-01-01', '2026-03-31', 'ACTIVE',
 (SELECT id FROM users WHERE username = 'merchant01')),

('MEMBER30K',
 (SELECT id FROM campaigns WHERE name = 'Thuong khach hang than thiet Q1 2026'),
 'Giam thang 30K cho hoi vien', 'FIXED_AMOUNT', 30000.00, NULL, 150000.00,
 '[]', '[]', '[]', 1500, NULL, true, '2026-01-01', '2026-03-31', 'ACTIVE',
 (SELECT id FROM users WHERE username = 'merchant01'));

-- 4. LOYAL10 usages: 1 usage/ngày trong 80 ngày (Jan 1 → Mar 21)
--    Xoay vòng qua 22 M01-CUS-% customers
INSERT INTO voucher_usages (voucher_id, customer_id, external_order_id, external_branch_id, discount_amount, order_total, used_at)
SELECT
    (SELECT id FROM vouchers WHERE code = 'LOYAL10'),
    c.id,
    'ORD-L10-' || lpad(s::text, 3, '0'),
    (ARRAY['BR-QUAN1', 'BR-QUAN3', 'BR-QUAN5', 'BR-QUAN7', 'BR-BINH'])[s % 5 + 1],
    20000.00,
    200000.00 + (s % 10) * 10000,
    '2026-01-01 09:00:00+07'::timestamptz + (s * interval '1 day')
FROM generate_series(0, 79) s
JOIN (
    SELECT id, (row_number() OVER (ORDER BY id) - 1) AS rn, count(*) OVER () AS total
    FROM customers
    WHERE external_id LIKE 'M01-CUS-%'
) c ON c.rn = s % c.total;

-- 5. MEMBER30K usages: 1 usage/ngày trong 70 ngày (Jan 1 → Mar 11)
--    Offset giờ khác để không trùng used_at
INSERT INTO voucher_usages (voucher_id, customer_id, external_order_id, external_branch_id, discount_amount, order_total, used_at)
SELECT
    (SELECT id FROM vouchers WHERE code = 'MEMBER30K'),
    c.id,
    'ORD-M30-' || lpad(s::text, 3, '0'),
    (ARRAY['BR-QUAN1', 'BR-QUAN3', 'BR-QUAN5', 'BR-QUAN7', 'BR-BINH'])[(s + 2) % 5 + 1],
    30000.00,
    200000.00 + (s % 8) * 15000,
    '2026-01-01 14:00:00+07'::timestamptz + (s * interval '1 day')
FROM generate_series(0, 69) s
JOIN (
    SELECT id, (row_number() OVER (ORDER BY id) - 1) AS rn, count(*) OVER () AS total
    FROM customers
    WHERE external_id LIKE 'M01-CUS-%'
) c ON c.rn = s % c.total;

-- 6. COFFEE20 usages trong tháng 3 (Mar 1 → Mar 20, max_usage_per_customer=2 nên 2 vòng)
--    Vòng 1: Mar 1-20
INSERT INTO voucher_usages (voucher_id, customer_id, external_order_id, external_branch_id, discount_amount, order_total, used_at)
SELECT
    (SELECT id FROM vouchers WHERE code = 'COFFEE20'),
    c.id,
    'ORD-CF20A-' || lpad(s::text, 2, '0'),
    (ARRAY['BR-QUAN1', 'BR-QUAN3', 'BR-QUAN5'])[s % 3 + 1],
    30000.00,
    150000.00 + (s % 5) * 20000,
    '2026-03-01 10:00:00+07'::timestamptz + (s * interval '1 day')
FROM generate_series(0, 19) s
JOIN (
    SELECT id, (row_number() OVER (ORDER BY id) - 1) AS rn, count(*) OVER () AS total
    FROM customers
    WHERE external_id LIKE 'M01-CUS-%'
) c ON c.rn = s % c.total;

--    Vòng 2: Mar 1-20 (lần 2, giờ khác)
INSERT INTO voucher_usages (voucher_id, customer_id, external_order_id, external_branch_id, discount_amount, order_total, used_at)
SELECT
    (SELECT id FROM vouchers WHERE code = 'COFFEE20'),
    c.id,
    'ORD-CF20B-' || lpad(s::text, 2, '0'),
    (ARRAY['BR-QUAN1', 'BR-QUAN3', 'BR-QUAN5'])[(s + 1) % 3 + 1],
    30000.00,
    150000.00 + (s % 7) * 10000,
    '2026-03-01 16:00:00+07'::timestamptz + (s * interval '1 day')
FROM generate_series(0, 19) s
JOIN (
    SELECT id, (row_number() OVER (ORDER BY id) - 1) AS rn, count(*) OVER () AS total
    FROM customers
    WHERE external_id LIKE 'M01-CUS-%'
) c ON c.rn = (s + 10) % c.total;  -- offset để không trùng customer với vòng 1

-- 7. Thêm distributions cho khách hàng mới
INSERT INTO voucher_distributions (voucher_id, customer_id, channel, status, sent_at, created_at)
SELECT
    (SELECT id FROM vouchers WHERE code = 'LOYAL10'),
    c.id,
    (ARRAY['EMAIL', 'SMS'])[((row_number() OVER (ORDER BY c.id) - 1) % 2) + 1],
    'SENT',
    '2026-01-01 08:00:00+07'::timestamptz,
    '2026-01-01 08:00:00+07'::timestamptz
FROM customers c
WHERE c.external_id LIKE 'M01-CUS-%';
