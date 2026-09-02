-- =====================================================================
-- Sunrise Dental Clinic Management System — Schema
-- Target: MySQL 8.0
-- Run against: sdcms_db  (and sdcms_test for the DAO test suite)
-- =====================================================================

-- Dropped in reverse dependency order so foreign keys do not block removal.
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS bills;
DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS treatments;
DROP TABLE IF EXISTS dentists;
DROP TABLE IF EXISTS patients;
DROP TABLE IF EXISTS users;

-- ---------------------------------------------------------------------
-- users — every person who can log in
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id         BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(60)  NOT NULL,   -- BCrypt output is always 60 chars
    full_name       VARCHAR(100) NOT NULL,
    role            ENUM('ADMINISTRATOR','RECEPTIONIST') NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- dentists
--
-- Records rather than system users. Reception maintains these so names and
-- specialisations flow through to appointments and receipts; dentists do
-- not log in. Retired with is_active rather than deleted, because
-- appointments reference them.
-- ---------------------------------------------------------------------
CREATE TABLE dentists (
    dentist_id        BIGINT       NOT NULL AUTO_INCREMENT,
    full_name         VARCHAR(100) NOT NULL,
    license_no        VARCHAR(30)  NOT NULL,
    specialization    VARCHAR(80)  NOT NULL,
    contact_no        VARCHAR(15)  NULL,
    available_from    TIME         NOT NULL DEFAULT '08:00:00',
    available_to      TIME         NOT NULL DEFAULT '20:00:00',
    consultation_room VARCHAR(10)  NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,

    PRIMARY KEY (dentist_id),
    CONSTRAINT uq_dentists_license UNIQUE (license_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- patients
-- Age is NOT stored — it is derived from date_of_birth, because a stored
-- age is correct only on the day it is written and senior-citizen
-- discount eligibility depends on it.
-- ---------------------------------------------------------------------
CREATE TABLE patients (
    patient_id      BIGINT       NOT NULL AUTO_INCREMENT,
    full_name       VARCHAR(100) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    contact_no      VARCHAR(15)  NOT NULL,
    email           VARCHAR(100) NULL,
    date_of_birth   DATE         NOT NULL,
    gender          ENUM('MALE','FEMALE','OTHER') NOT NULL,
    medical_notes   TEXT         NULL,
    registered_date DATE         NOT NULL DEFAULT (CURRENT_DATE),

    PRIMARY KEY (patient_id),
    CONSTRAINT uq_patients_contact UNIQUE (contact_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- treatments — prices live here, never hard-coded in Java, so the
-- administrator can revise them without a redeployment
-- ---------------------------------------------------------------------
CREATE TABLE treatments (
    treatment_id     BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(80)   NOT NULL,
    description      VARCHAR(255)  NULL,
    base_cost        DECIMAL(10,2) NOT NULL,
    duration_minutes INT           NOT NULL DEFAULT 30,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,

    PRIMARY KEY (treatment_id),
    CONSTRAINT uq_treatments_name UNIQUE (name),
    CONSTRAINT ck_treatments_cost CHECK (base_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- appointments
--
-- uq_dentist_slot is the single most important constraint in this schema.
-- The service layer also checks availability before inserting, but that
-- check is not atomic: two concurrent requests can both pass it before
-- either commits. This constraint makes the storage engine reject the
-- second insert, which is what actually guarantees no double booking.
-- ---------------------------------------------------------------------
CREATE TABLE appointments (
    appointment_no   VARCHAR(20)  NOT NULL,
    patient_id       BIGINT       NOT NULL,
    dentist_id       BIGINT       NOT NULL,
    treatment_id     BIGINT       NOT NULL,
    appointment_date DATE         NOT NULL,
    appointment_time TIME         NOT NULL,
    status           ENUM('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW')
                                  NOT NULL DEFAULT 'SCHEDULED',
    notes            VARCHAR(255) NULL,
    created_by       BIGINT       NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (appointment_no),

    CONSTRAINT fk_apt_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_apt_dentist FOREIGN KEY (dentist_id)
        REFERENCES dentists (dentist_id) ON DELETE RESTRICT,
    CONSTRAINT fk_apt_treatment FOREIGN KEY (treatment_id)
        REFERENCES treatments (treatment_id) ON DELETE RESTRICT,
    CONSTRAINT fk_apt_creator FOREIGN KEY (created_by)
        REFERENCES users (user_id) ON DELETE RESTRICT,

    CONSTRAINT uq_dentist_slot
        UNIQUE (dentist_id, appointment_date, appointment_time),

    INDEX idx_apt_date_status (appointment_date, status),
    INDEX idx_apt_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- bills
--
-- Amounts are stored rather than recalculated. This is a deliberate
-- departure from strict normalisation: a bill records what was actually
-- charged at a point in time. If the administrator later revises a
-- treatment price, historical bills must still show what the patient
-- genuinely paid.
--
-- DECIMAL, never FLOAT/DOUBLE — binary floating point cannot represent
-- decimal fractions exactly, and that drift is the billing error this
-- system exists to remove.
-- ---------------------------------------------------------------------
CREATE TABLE bills (
    bill_id          VARCHAR(20)   NOT NULL,
    appointment_no   VARCHAR(20)   NOT NULL,
    consultation_fee DECIMAL(10,2) NOT NULL,
    treatment_cost   DECIMAL(10,2) NOT NULL,
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    tax_amount       DECIMAL(10,2) NOT NULL,
    total_amount     DECIMAL(10,2) NOT NULL,
    payment_status   ENUM('PENDING','PAID','REFUNDED') NOT NULL DEFAULT 'PENDING',
    payment_method   ENUM('CASH','CARD','INSURANCE') NULL,
    issued_by        BIGINT        NOT NULL,
    issued_date      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (bill_id),
    CONSTRAINT uq_bills_appointment UNIQUE (appointment_no),
    CONSTRAINT fk_bills_appointment FOREIGN KEY (appointment_no)
        REFERENCES appointments (appointment_no) ON DELETE CASCADE,
    CONSTRAINT fk_bills_issuer FOREIGN KEY (issued_by)
        REFERENCES users (user_id) ON DELETE RESTRICT,

    INDEX idx_bills_issued (issued_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- audit_log — not required by the scenario, but patient records are
-- sensitive personal data and a clinical system that cannot say who
-- viewed or changed a record is difficult to defend.
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    log_id      BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    action      VARCHAR(50) NOT NULL,
    entity_name VARCHAR(50) NOT NULL,
    entity_id   VARCHAR(30) NULL,
    timestamp   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address  VARCHAR(45) NULL,

    PRIMARY KEY (log_id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,

    INDEX idx_audit_time (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
