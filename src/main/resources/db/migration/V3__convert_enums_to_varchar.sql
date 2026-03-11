-- Convert PostgreSQL native enum columns to VARCHAR for JPA compatibility
-- Must drop CHECK constraints that reference the enum types first

-- Users table
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(50) USING role::text;

-- Campaigns table
ALTER TABLE campaigns ALTER COLUMN status TYPE VARCHAR(50) USING status::text;

-- Vouchers table - drop check constraint that references the enum type
ALTER TABLE vouchers DROP CONSTRAINT IF EXISTS vouchers_check1;
ALTER TABLE vouchers DROP CONSTRAINT IF EXISTS vouchers_check;
ALTER TABLE vouchers DROP CONSTRAINT IF EXISTS vouchers_discount_type_check;
ALTER TABLE vouchers ALTER COLUMN discount_type TYPE VARCHAR(50) USING discount_type::text;
ALTER TABLE vouchers ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
-- Re-add check constraint using VARCHAR comparison
ALTER TABLE vouchers ADD CONSTRAINT vouchers_percentage_check
    CHECK (discount_type != 'PERCENTAGE' OR discount_value <= 100);

-- Voucher distributions table
ALTER TABLE voucher_distributions ALTER COLUMN channel TYPE VARCHAR(50) USING channel::text;
ALTER TABLE voucher_distributions ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
