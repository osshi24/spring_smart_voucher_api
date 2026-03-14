-- Seed merchant01 account (USER role)
-- password = Staff@123 (dùng chung hash với staff01 cho seed data)
INSERT INTO users (username, password_hash, full_name, email, role, is_active, status, email_verified)
VALUES ('merchant01', '$2a$10$Z1VH3.JnAQKyqmoTUYnHm.sqSRShcq4o1PNt2cyQq20LtY1.EZnpm',
        'Coffee House VN', 'merchant01@voucher.vn', 'USER', true, 'ACTIVE', true);

-- Customer record cho merchant (tự động linked khi đăng ký qua /register-merchant)
INSERT INTO customers (external_id, full_name, email, is_active, created_by)
VALUES ('user:' || (SELECT id FROM users WHERE username = 'merchant01'),
        'Coffee House VN', 'merchant01@voucher.vn', true,
        (SELECT id FROM users WHERE username = 'merchant01'));

-- Sample customers của merchant01
INSERT INTO customers (external_id, full_name, email, phone, is_active, created_by) VALUES
('M01-CUS-001', 'Pham Thi Hoa', 'hoa.pham@gmail.com', '0934111222', true,
    (SELECT id FROM users WHERE username = 'merchant01')),
('M01-CUS-002', 'Vo Thanh Tung', 'tung.vo@gmail.com', '0934111333', true,
    (SELECT id FROM users WHERE username = 'merchant01'));

-- Campaign của merchant01
INSERT INTO campaigns (name, description, budget, start_date, end_date, status, created_by)
VALUES ('Khuyen mai Coffee thang 3', 'Uu dai cho khach hang than thiet cua Coffee House', 5000000.00,
        '2026-03-01', '2026-03-31', 'ACTIVE',
        (SELECT id FROM users WHERE username = 'merchant01'));

-- Voucher của merchant01
INSERT INTO vouchers (code, campaign_id, description, discount_type, discount_value, max_discount_amount,
                      min_order_value, applicable_products, applicable_categories, applicable_branches,
                      max_usage_total, max_usage_per_customer, is_public, valid_from, valid_until, status, created_by)
VALUES
('COFFEE20', (SELECT id FROM campaigns WHERE name = 'Khuyen mai Coffee thang 3'),
 'Giam 20% toi da 50K cho tat ca do uong', 'PERCENTAGE', 20.00, 50000.00, 50000.00,
 '[]', '[]', '[]', 200, 2, true, '2026-03-01', '2026-03-31', 'ACTIVE',
 (SELECT id FROM users WHERE username = 'merchant01')),
('COFFEE30K', (SELECT id FROM campaigns WHERE name = 'Khuyen mai Coffee thang 3'),
 'Giam thang 30K cho khach hang VIP', 'FIXED_AMOUNT', 30000.00, NULL, 100000.00,
 '[]', '[]', '[]', 50, 1, false, '2026-03-01', '2026-03-31', 'ACTIVE',
 (SELECT id FROM users WHERE username = 'merchant01'));
