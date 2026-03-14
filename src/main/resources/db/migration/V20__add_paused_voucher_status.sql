-- Add PAUSED to voucher lifecycle
-- Status is stored as VARCHAR (converted in V3) — no enum type to update
-- This migration documents the new valid status value PAUSED
-- No structural change needed; Java enum updated in VoucherStatus.java

SELECT 1; -- placeholder to satisfy Flyway version tracking
