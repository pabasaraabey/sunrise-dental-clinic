-- =====================================================================
-- Sunrise Dental Clinic Management System — Seed Data
-- Run AFTER schema.sql
--
-- Login credentials (development only — change before any real use):
--   admin     / Admin@123
--   reception / Recep@123
--   dentist1  / Dentist@123
--   dentist2  / Dentist@123
--   dentist3  / Dentist@123
--
-- Hashes are genuine BCrypt (cost 10). The $2a$ prefix is used because
-- jBCrypt 0.4 does not accept the $2b$ variant.
-- =====================================================================

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
INSERT INTO users (username, password_hash, full_name, role) VALUES
('admin',     '$2a$10$6TTC6LJRWCK6toyw3XF9EexFlQAkPO6rMC5ZyEfoLrJwXyEsml8Fq', 'Nimal Perera',        'ADMINISTRATOR'),
('reception', '$2a$10$UIV.A1O5NZmq69SayM/b2O0WE3xSDSOJSkp44qDizPFeOZ.9bR6MC', 'Kamala Silva',        'RECEPTIONIST'),
('dentist1',  '$2a$10$9xWVUyZY6Iy/BCWlnA590.kdYhdET4qFCG6EBfUZRPlxNODjR5Kmi', 'Dr. Ashan Fernando',  'DENTIST'),
('dentist2',  '$2a$10$9xWVUyZY6Iy/BCWlnA590.kdYhdET4qFCG6EBfUZRPlxNODjR5Kmi', 'Dr. Ruwani Jayasuriya','DENTIST'),
('dentist3',  '$2a$10$9xWVUyZY6Iy/BCWlnA590.kdYhdET4qFCG6EBfUZRPlxNODjR5Kmi', 'Dr. Sanjaya Bandara', 'DENTIST');

-- ---------------------------------------------------------------------
-- dentists
-- ---------------------------------------------------------------------
INSERT INTO dentists (user_id, license_no, specialization, available_from, available_to, consultation_room) VALUES
((SELECT user_id FROM users WHERE username = 'dentist1'), 'SLMC-DEN-4471', 'General Dentistry',  '08:00:00', '16:00:00', 'R-01'),
((SELECT user_id FROM users WHERE username = 'dentist2'), 'SLMC-DEN-5182', 'Orthodontics',       '10:00:00', '18:00:00', 'R-02'),
((SELECT user_id FROM users WHERE username = 'dentist3'), 'SLMC-DEN-6093', 'Oral Surgery',       '12:00:00', '20:00:00', 'R-03');

-- ---------------------------------------------------------------------
-- treatments — prices in LKR, representative of a private Colombo clinic
-- ---------------------------------------------------------------------
INSERT INTO treatments (name, description, base_cost, duration_minutes) VALUES
('Routine Check-up',    'Examination and oral health assessment',        2500.00, 30),
('Scaling and Polishing','Removal of plaque and surface stains',         4500.00, 45),
('Composite Filling',   'Tooth-coloured restoration of a decayed tooth',  6500.00, 45),
('Root Canal Treatment','Endodontic therapy, single canal',             28000.00, 90),
('Tooth Extraction',    'Simple extraction under local anaesthetic',      5500.00, 30),
('Surgical Extraction', 'Removal of impacted or broken tooth',           18000.00, 60),
('Dental Crown',        'Porcelain-fused-to-metal crown fitting',        35000.00, 60),
('Teeth Whitening',     'In-clinic bleaching procedure',                 22000.00, 60);

-- ---------------------------------------------------------------------
-- patients
-- Wimala Gunasekara is deliberately over 65 so the senior-citizen
-- discount path can be exercised without editing data.
-- ---------------------------------------------------------------------
INSERT INTO patients (full_name, address, contact_no, email, date_of_birth, gender) VALUES
('Sunil Rathnayake',    '42/3 Galle Road, Colombo 03',        '0771234567', 'sunil.r@example.com',   '1988-04-17', 'MALE'),
('Wimala Gunasekara',   '18 Temple Lane, Nugegoda',           '0712345678', NULL,                    '1952-11-02', 'FEMALE'),
('Dilhani Wickramasinghe','7A Flower Road, Colombo 07',       '0763456789', 'dilhani.w@example.com', '1995-07-23', 'FEMALE'),
('Tharindu Alwis',      '129 High Level Road, Maharagama',    '0704567890', 'tharindu.a@example.com','2001-01-09', 'MALE'),
('Mohamed Rizwan',      '55 Marine Drive, Dehiwala',          '0775678901', NULL,                    '1979-09-30', 'MALE');
