-- Treat max_usage_total = 0 and max_usage_per_customer = 0 as "unlimited" (same as NULL).
-- Prior behavior rejected all redemptions when limit was 0, which is semantically wrong.

CREATE OR REPLACE FUNCTION increment_voucher_usage()
RETURNS TRIGGER AS $$
DECLARE
    v_voucher vouchers%ROWTYPE;
    v_customer_usage_count INT;
BEGIN
    SELECT * INTO v_voucher FROM vouchers WHERE id = NEW.voucher_id FOR UPDATE;

    -- Check max_usage_total (NULL or 0 = unlimited)
    IF v_voucher.max_usage_total IS NOT NULL
       AND v_voucher.max_usage_total > 0
       AND v_voucher.current_usage_count >= v_voucher.max_usage_total THEN
        RAISE EXCEPTION 'Voucher % da het luot su dung (max: %)', v_voucher.code, v_voucher.max_usage_total;
    END IF;

    -- Check max_usage_per_customer (NULL or 0 = unlimited)
    IF v_voucher.max_usage_per_customer IS NOT NULL
       AND v_voucher.max_usage_per_customer > 0 THEN
        SELECT COUNT(*) INTO v_customer_usage_count
        FROM voucher_usages
        WHERE voucher_id = NEW.voucher_id AND customer_id = NEW.customer_id;

        IF v_customer_usage_count >= v_voucher.max_usage_per_customer THEN
            RAISE EXCEPTION 'Khach hang da su dung voucher % het so lan cho phep (max: %)',
                v_voucher.code, v_voucher.max_usage_per_customer;
        END IF;
    END IF;

    UPDATE vouchers
    SET current_usage_count = current_usage_count + 1
    WHERE id = NEW.voucher_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
