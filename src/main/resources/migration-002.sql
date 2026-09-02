-- =====================================================================
-- Migration 002 — revised role and dentist model
--
-- The clinic has three staff, not a finance department. Requiring a
-- separate administrator for routine work would mean reception cannot
-- operate when the owner is out, so the receptionist role absorbs the
-- operational duties (dentists, treatments, prices) and the administrator
-- role exists to create and deactivate staff accounts.
--
-- The compensating control is audit_log: every price change and bill is
-- attributed to a named user and timestamped.
--
-- Dentists become records rather than system users. Reception adds them
-- so their details flow into appointments and billing; they do not log in.
--
-- Run against sdcms_db.
-- =====================================================================

USE sdcms_db;

-- ---------------------------------------------------------------------
-- 1. Dentists hold their own identity
-- ---------------------------------------------------------------------

ALTER TABLE dentists
    ADD COLUMN full_name VARCHAR(100) NOT NULL DEFAULT '' AFTER dentist_id,
    ADD COLUMN contact_no VARCHAR(15) NULL AFTER specialization;

-- Carry across the names currently held on the linked user rows.
UPDATE dentists d
JOIN users u ON u.user_id = d.user_id
SET d.full_name = u.full_name;

-- The link to users becomes optional, then unused.
ALTER TABLE dentists
    DROP FOREIGN KEY fk_dentists_user;

ALTER TABLE dentists
    DROP INDEX uq_dentists_user;

ALTER TABLE dentists
    MODIFY COLUMN user_id BIGINT NULL;

UPDATE dentists SET user_id = NULL;

-- ---------------------------------------------------------------------
-- 2. Two login roles
-- ---------------------------------------------------------------------

-- Remove the dentist logins now that dentists are records.
DELETE FROM users WHERE role = 'DENTIST';

ALTER TABLE users
    MODIFY COLUMN role ENUM('ADMINISTRATOR','RECEPTIONIST') NOT NULL;

-- ---------------------------------------------------------------------
-- 3. Treatments and dentists can be retired rather than deleted
--    A treatment referenced by historical appointments must not be
--    removed, or those bills lose their meaning.
-- ---------------------------------------------------------------------

-- (is_active already exists on both tables; nothing to add.)

-- ---------------------------------------------------------------------
-- 4. Verify
-- ---------------------------------------------------------------------

SELECT user_id, username, full_name, role FROM users;
SELECT dentist_id, full_name, specialization, license_no, is_active FROM dentists;
