-- =====================================================================
-- V2: mark the generated demonstration business.
-- Design correction to Chapter 4: section 4.7.3 requires a "Sample data" banner on
-- every page when seeded demonstration data is used (decision D11). The banner needs
-- a stored flag, so business gains is_demo_data. Real businesses always have FALSE.
-- =====================================================================
ALTER TABLE business ADD COLUMN is_demo_data BOOLEAN NOT NULL DEFAULT FALSE;
