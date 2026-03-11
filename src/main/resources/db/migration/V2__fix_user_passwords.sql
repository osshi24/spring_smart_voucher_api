-- Fix user passwords with correct BCrypt hashes
-- dock: password = admin123
-- staff01: password = staff123
UPDATE users SET password_hash = '$2a$10$AWH5Yqpjd8fMViRkQ/10YuiGQZrWF7UQ9say8glR6KRnQdqApMUxK' WHERE username = 'admin01';
UPDATE users SET password_hash = '$2a$10$Z1VH3.JnAQKyqmoTUYnHm.sqSRShcq4o1PNt2cyQq20LtY1.EZnpm' WHERE username = 'staff01';
