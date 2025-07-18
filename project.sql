-- ===========================
-- DROP and CREATE DATABASE
-- ===========================
DROP DATABASE IF EXISTS healthsure;
CREATE DATABASE healthsure;
USE healthsure;

-- ===========================
-- 1. Providers
-- ===========================
CREATE TABLE Providers (
  provider_id VARCHAR(20) PRIMARY KEY,
  provider_name VARCHAR(100) NOT NULL,
  hospital_name VARCHAR(100) NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  address VARCHAR(225) NOT NULL,
  city VARCHAR(225) NOT NULL,
  state VARCHAR(225) NOT NULL,
  zip_code VARCHAR(20) NOT NULL,
  status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===========================
-- 2. Doctors
-- ===========================
CREATE TABLE Doctors (
  doctor_id VARCHAR(20) PRIMARY KEY,
  provider_id VARCHAR(20),
  doctor_name VARCHAR(100) NOT NULL,
  qualification VARCHAR(255),
  specialization VARCHAR(100),
  license_no VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  address VARCHAR(225) NOT NULL,
  gender VARCHAR(10),
  password VARCHAR(255) NOT NULL,
  login_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
  doctor_status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'INACTIVE',
  FOREIGN KEY (provider_id) REFERENCES Providers(provider_id)
);

-- ===========================
-- 3. Doctor Availability
-- ===========================
CREATE TABLE Doctor_availability (
  availability_id VARCHAR(36) PRIMARY KEY,
  doctor_id VARCHAR(20) NOT NULL,
  available_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  slot_type ENUM('STANDARD', 'ADHOC') DEFAULT 'STANDARD',
  max_capacity INT NOT NULL,
  patient_window INT GENERATED ALWAYS AS (
    TIMESTAMPDIFF(MINUTE, start_time, end_time) / max_capacity
  ) STORED,
  is_recurring BOOLEAN DEFAULT FALSE,
  notes VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (doctor_id) REFERENCES Doctors(doctor_id)
);

-- ===========================
-- 4. Recipient
-- ===========================
CREATE TABLE Recipient (
  h_id VARCHAR(20) PRIMARY KEY,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  mobile VARCHAR(10) UNIQUE NOT NULL,
  user_name VARCHAR(100) UNIQUE NOT NULL,
  gender ENUM('MALE', 'FEMALE') NOT NULL,
  dob DATE NOT NULL,
  address VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  password VARCHAR(255) NOT NULL,
  email VARCHAR(150) UNIQUE NOT NULL,
  status ENUM('ACTIVE', 'INACTIVE', 'BLOCKED') DEFAULT 'ACTIVE',
  login_attempts INT DEFAULT 0,
  locked_until DATETIME DEFAULT NULL,
  last_login DATETIME DEFAULT NULL,
  password_updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ===========================
-- 5. Appointment
-- ===========================
CREATE TABLE Appointment (
  appointment_id VARCHAR(36) PRIMARY KEY,
  doctor_id VARCHAR(20) NOT NULL,
  h_id VARCHAR(20) NOT NULL,
  availability_id VARCHAR(36) NOT NULL,
  provider_id VARCHAR(20) NOT NULL,
  requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  booked_at TIMESTAMP NULL,
  cancelled_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  status ENUM('PENDING', 'BOOKED', 'CANCELLED', 'COMPLETED') DEFAULT 'PENDING',
  slot_no INT NOT NULL,
  start TIMESTAMP,
  end TIMESTAMP,
  notes TEXT,
  FOREIGN KEY (doctor_id) REFERENCES Doctors(doctor_id),
  FOREIGN KEY (h_id) REFERENCES Recipient(h_id),
  FOREIGN KEY (availability_id) REFERENCES Doctor_availability(availability_id),
  FOREIGN KEY (provider_id) REFERENCES Providers(provider_id)
);

-- ===========================
-- Sample Data
-- ===========================

-- Providers
INSERT INTO Providers (provider_id, provider_name, hospital_name, email, address, city, state, zip_code, status)
VALUES
('P1003','Dr. Alok Mehta','AIIMS','alok.m@aiims.edu','AIIMS Campus','Delhi','Delhi','110029','APPROVED'),
('P1005','Dr. Arjun Desai','Max Healthcare','arjun.d@max.com','Max Complex','Noida','Uttar Pradesh','201301','APPROVED');

-- Doctors
INSERT INTO Doctors (doctor_id, provider_id, doctor_name, qualification, specialization, license_no, email, address, gender, password, login_status, doctor_status)
VALUES
('D1003','P1003','Dr. Alok Nair','MBBS, DM','Neurology','LIC1003','alok.n@aiims.edu','South Campus, Delhi','MALE','pass123','APPROVED','ACTIVE'),
('D1005','P1005','Dr. Arjun Rao','MBBS, MS','Orthopedics','LIC1005','arjun.r@max.com','Sector 18, Noida','MALE','pass123','APPROVED','ACTIVE');

-- Recipients
INSERT INTO Recipient (h_id, first_name, last_name, mobile, user_name, gender, dob, address, password, email)
VALUES
('H1003','Robert','Brown','9876543212','robert.brown','MALE','1988-03-03','789 Pine St','pass123','sandhanmeher3@gmail.com'),
('H1004','Emily','Davis','9876543213','emily.davis','FEMALE','1995-04-04','234 Oak St','pass123','emily.davis@example.com'),
('H1005','Michael','Wilson','9876543214','michael.wilson','MALE','1975-05-15','456 Maple Ave','pass123','michael.wilson@example.com'),
('H1006','Sarah','Johnson','9876543215','sarah.johnson','FEMALE','1982-06-22','789 Elm St','pass123','sarah.johnson@example.com');

-- Doctor Availability
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes)
VALUES
-- Past availabilities
('A2001','D1003','2025-06-01','10:00:00','11:00:00','STANDARD',4,FALSE,'Past Neurology Check'),
('A2002','D1003','2025-06-15','14:00:00','15:00:00','ADHOC',3,FALSE,'Past Neuro Follow-up'),
('A2003','D1005','2025-06-10','09:00:00','10:00:00','STANDARD',4,FALSE,'Past Ortho Check'),
('A2004','D1005','2025-06-20','16:00:00','17:00:00','ADHOC',2,FALSE,'Past Ortho Review'),

-- Current and future availabilities
('A2005','D1003','2025-07-07','10:00:00','11:00:00','STANDARD',4,FALSE,'Morning Neurology Check'),
('A2006','D1005','2025-07-07','14:00:00','15:30:00','ADHOC',3,FALSE,'Afternoon Orthopedics Check'),
('A2007','D1003','2025-07-10','09:00:00','10:00:00','STANDARD',4,FALSE,'Upcoming Neuro'),
('A2008','D1003','2025-07-12','13:00:00','14:00:00','ADHOC',3,FALSE,'Upcoming Follow-up'),
('A2009','D1005','2025-07-15','11:00:00','12:00:00','STANDARD',4,FALSE,'Future Ortho'),
('A2010','D1005','2025-07-20','15:00:00','16:00:00','ADHOC',2,FALSE,'Future Review');

-- ===========================
-- Appointments with various statuses
-- ===========================

-- Past completed appointments
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, booked_at, completed_at, status, slot_no, start, end, notes)
VALUES
-- June appointments (completed)
('APPT001','D1003','H1003','A2001','P1003', '2025-06-01 08:00:00', '2025-06-01 08:05:00', '2025-06-01 10:30:00', 'COMPLETED', 1, '2025-06-01 10:00:00', '2025-06-01 10:15:00', 'Initial Neuro Consultation'),
('APPT002','D1003','H1004','A2001','P1003', '2025-06-01 08:10:00', '2025-06-01 08:15:00', '2025-06-01 10:45:00', 'COMPLETED', 2, '2025-06-01 10:15:00', '2025-06-01 10:30:00', 'Follow-up Visit'),
('APPT003','D1005','H1005','A2003','P1005', '2025-06-09 14:00:00', '2025-06-09 14:05:00', '2025-06-10 09:30:00', 'COMPLETED', 1, '2025-06-10 09:00:00', '2025-06-10 09:15:00', 'Knee Pain Evaluation'),
('APPT004','D1005','H1006','A2003','P1005', '2025-06-09 14:30:00', '2025-06-09 14:35:00', '2025-06-10 09:45:00', 'COMPLETED', 2, '2025-06-10 09:15:00', '2025-06-10 09:30:00', 'Shoulder Injury'),

-- Past cancelled appointments
('APPT005','D1003','H1005','A2002','P1003', '2025-06-14 10:00:00', '2025-06-14 10:05:00', NULL, 'CANCELLED', 1, '2025-06-15 14:00:00', '2025-06-15 14:20:00', 'Cancelled - Patient rescheduled'),
('APPT006','D1005','H1003','A2004','P1005', '2025-06-19 11:00:00', '2025-06-19 11:05:00', NULL, 'CANCELLED', 1, '2025-06-20 16:00:00', '2025-06-20 16:30:00', 'Cancelled - No longer needed');

-- Current pending appointments
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, status, slot_no, start, end, notes)
VALUES
('APPT101','D1003','H1003','A2005','P1003', NOW(), 'PENDING', 1, '2025-07-07 10:00:00', '2025-07-07 10:15:00', 'Initial Consultation'),
('APPT102','D1003','H1004','A2005','P1003', NOW(), 'PENDING', 2, '2025-07-07 10:15:00', '2025-07-07 10:30:00', 'Follow-up Visit'),
('APPT103','D1005','H1005','A2006','P1005', NOW(), 'PENDING', 1, '2025-07-07 14:00:00', '2025-07-07 14:30:00', 'Back Pain Evaluation'),
('APPT104','D1005','H1006','A2006','P1005', NOW(), 'PENDING', 2, '2025-07-07 14:30:00', '2025-07-07 15:00:00', 'Joint Pain');

-- Current booked appointments
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, booked_at, status, slot_no, start, end, notes)
VALUES
('APPT201','D1003','H1005','A2007','P1003', '2025-07-05 09:00:00', '2025-07-05 09:05:00', 'BOOKED', 1, '2025-07-10 09:00:00', '2025-07-10 09:15:00', 'MRI Results Review'),
('APPT202','D1003','H1006','A2007','P1003', '2025-07-05 09:30:00', '2025-07-05 09:35:00', 'BOOKED', 2, '2025-07-10 09:15:00', '2025-07-10 09:30:00', 'Headache Evaluation'),
('APPT203','D1005','H1003','A2009','P1005', '2025-07-10 10:00:00', '2025-07-10 10:05:00', 'BOOKED', 1, '2025-07-15 11:00:00', '2025-07-15 11:15:00', 'Post-Op Check'),
('APPT204','D1005','H1004','A2009','P1005', '2025-07-10 10:30:00', '2025-07-10 10:35:00', 'BOOKED', 2, '2025-07-15 11:15:00', '2025-07-15 11:30:00', 'Fracture Follow-up');

-- Future pending appointments
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, status, slot_no, start, end, notes)
VALUES
('APPT301','D1003','H1003','A2008','P1003', NOW(), 'PENDING', 1, '2025-07-12 13:00:00', '2025-07-12 13:20:00', 'Possible Neuro Consult'),
('APPT302','D1003','H1004','A2008','P1003', NOW(), 'PENDING', 2, '2025-07-12 13:20:00', '2025-07-12 13:40:00', 'Sleep Disorder'),
('APPT303','D1005','H1005','A2010','P1005', NOW(), 'PENDING', 1, '2025-07-20 15:00:00', '2025-07-20 15:30:00', 'Possible Surgery Consult'),
('APPT304','D1005','H1006','A2010','P1005', NOW(), 'PENDING', 2, '2025-07-20 15:30:00', '2025-07-20 16:00:00', 'Joint Replacement Discussion');

-- ===========================
-- Additional Appointment Data (25 records)
-- ===========================

-- Past COMPLETED appointments (5)
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, booked_at, completed_at, status, slot_no, start, end, notes)
VALUES
('APPT501','D1003','H1003','A2001','P1003','2025-06-01 08:00:00','2025-06-01 08:05:00','2025-06-01 10:30:00','COMPLETED',1,'2025-06-01 10:00:00','2025-06-01 10:15:00','Initial consultation'),
('APPT502','D1003','H1004','A2001','P1003','2025-06-01 08:10:00','2025-06-01 08:15:00','2025-06-01 10:45:00','COMPLETED',2,'2025-06-01 10:15:00','2025-06-01 10:30:00','Follow-up visit'),
('APPT503','D1005','H1005','A2003','P1005','2025-06-09 14:00:00','2025-06-09 14:05:00','2025-06-10 09:30:00','COMPLETED',1,'2025-06-10 09:00:00','2025-06-10 09:15:00','Knee pain evaluation'),
('APPT504','D1005','H1006','A2003','P1005','2025-06-09 14:30:00','2025-06-09 14:35:00','2025-06-10 09:45:00','COMPLETED',2,'2025-06-10 09:15:00','2025-06-10 09:30:00','Shoulder injury'),
('APPT505','D1003','H1005','A2002','P1003','2025-06-14 10:00:00','2025-06-14 10:05:00','2025-06-15 14:20:00','COMPLETED',1,'2025-06-15 14:00:00','2025-06-15 14:20:00','Neurological exam');

-- Past CANCELLED appointments (5)
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, booked_at, cancelled_at, status, slot_no, start, end, notes)
VALUES
('APPT506','D1005','H1003','A2004','P1005','2025-06-19 11:00:00','2025-06-19 11:05:00','2025-06-20 09:00:00','CANCELLED',1,'2025-06-20 16:00:00','2025-06-20 16:30:00','Cancelled - No longer needed'),
('APPT507','D1003','H1004','A2002','P1003','2025-06-14 10:30:00','2025-06-14 10:35:00','2025-06-15 10:00:00','CANCELLED',2,'2025-06-15 14:20:00','2025-06-15 14:40:00','Patient rescheduled'),
('APPT508','D1005','H1006','A2004','P1005','2025-06-19 11:30:00','2025-06-19 11:35:00','2025-06-20 09:30:00','CANCELLED',2,'2025-06-20 16:30:00','2025-06-20 17:00:00','Conflict with work'),
('APPT509','D1003','H1005','A2001','P1003','2025-06-01 08:20:00','2025-06-01 08:25:00','2025-06-01 09:00:00','CANCELLED',3,'2025-06-01 10:30:00','2025-06-01 10:45:00','Found another doctor'),
('APPT510','D1005','H1003','A2003','P1005','2025-06-09 15:00:00','2025-06-09 15:05:00','2025-06-10 08:00:00','CANCELLED',3,'2025-06-10 09:30:00','2025-06-10 09:45:00','Hospitalization');

-- Current PENDING appointments (5)
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, status, slot_no, start, end, notes)
VALUES
('APPT511','D1003','H1003','A2005','P1003',NOW(),'PENDING',1,'2025-07-07 10:00:00','2025-07-07 10:15:00','Initial consultation'),
('APPT512','D1003','H1004','A2005','P1003',NOW(),'PENDING',2,'2025-07-07 10:15:00','2025-07-07 10:30:00','Follow-up visit'),
('APPT513','D1005','H1005','A2006','P1005',NOW(),'PENDING',1,'2025-07-07 14:00:00','2025-07-07 14:30:00','Back pain evaluation'),
('APPT514','D1005','H1006','A2006','P1005',NOW(),'PENDING',2,'2025-07-07 14:30:00','2025-07-07 15:00:00','Joint pain'),
('APPT515','D1003','H1003','A2007','P1003',NOW(),'PENDING',3,'2025-07-10 09:30:00','2025-07-10 09:45:00','MRI results review');

-- Current BOOKED appointments (5)
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, booked_at, status, slot_no, start, end, notes)
VALUES
('APPT516','D1003','H1005','A2007','P1003','2025-07-05 09:00:00','2025-07-05 09:05:00','BOOKED',1,'2025-07-10 09:00:00','2025-07-10 09:15:00','MRI results review'),
('APPT517','D1003','H1006','A2007','P1003','2025-07-05 09:30:00','2025-07-05 09:35:00','BOOKED',2,'2025-07-10 09:15:00','2025-07-10 09:30:00','Headache evaluation'),
('APPT518','D1005','H1003','A2009','P1005','2025-07-10 10:00:00','2025-07-10 10:05:00','BOOKED',1,'2025-07-15 11:00:00','2025-07-15 11:15:00','Post-op check'),
('APPT519','D1005','H1004','A2009','P1005','2025-07-10 10:30:00','2025-07-10 10:35:00','BOOKED',2,'2025-07-15 11:15:00','2025-07-15 11:30:00','Fracture follow-up'),
('APPT520','D1003','H1005','A2008','P1003','2025-07-11 11:00:00','2025-07-11 11:05:00','BOOKED',1,'2025-07-12 13:00:00','2025-07-12 13:20:00','Neurological symptoms');

-- Future PENDING appointments (5)
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, status, slot_no, start, end, notes)
VALUES
('APPT521','D1003','H1003','A2008','P1003',NOW(),'PENDING',1,'2025-07-12 13:00:00','2025-07-12 13:20:00','Possible neuro consult'),
('APPT522','D1003','H1004','A2008','P1003',NOW(),'PENDING',2,'2025-07-12 13:20:00','2025-07-12 13:40:00','Sleep disorder'),
('APPT523','D1005','H1005','A2010','P1005',NOW(),'PENDING',1,'2025-07-20 15:00:00','2025-07-20 15:30:00','Possible surgery consult'),
('APPT524','D1005','H1006','A2010','P1005',NOW(),'PENDING',2,'2025-07-20 15:30:00','2025-07-20 16:00:00','Joint replacement discussion'),
('APPT525','D1003','H1003','A2007','P1003',NOW(),'PENDING',4,'2025-07-10 09:45:00','2025-07-10 10:00:00','Routine checkup');

-- ===========================
-- Add New Availability for D1003
-- ===========================
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes)
VALUES
('A2011','D1003','2025-07-25','09:00:00','11:00:00','STANDARD',8,FALSE,'New Friday morning slots');

-- ===========================
-- Add 10 New Appointments for Recipient H1003
-- (Mix of future PENDING, BOOKED, and CANCELLED)
-- ===========================

-- 4 New PENDING appointments for H1003
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, status, slot_no, start, end, notes)
VALUES
('APPT401','D1003','H1003','A2011','P1003', NOW(), 'PENDING', 1, '2025-07-25 09:00:00', '2025-07-25 09:15:00', 'New pending slot'),
('APPT402','D1003','H1003','A2011','P1003', NOW(), 'PENDING', 2, '2025-07-25 09:15:00', '2025-07-25 09:30:00', 'Another pending slot'),
('APPT403','D1005','H1003','A2010','P1005', NOW(), 'PENDING', 3, '2025-07-20 16:00:00', '2025-07-20 16:30:00', 'Future Ortho check-up'),
('APPT404','D1003','H1003','A2007','P1003', NOW(), 'PENDING', 4, '2025-07-10 09:45:00', '2025-07-10 10:00:00', 'Late slot for D1003');

-- 3 New BOOKED appointments for H1003
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, booked_at, status, slot_no, start, end, notes)
VALUES
('APPT405','D1003','H1003','A2011','P1003', '2025-07-23 10:00:00', '2025-07-23 10:05:00', 'BOOKED', 3, '2025-07-25 09:30:00', '2025-07-25 09:45:00', 'Booked follow-up'),
('APPT406','D1005','H1003','A2009','P1005', '2025-07-13 14:00:00', '2025-07-13 14:05:00', 'BOOKED', 3, '2025-07-15 11:30:00', '2025-07-15 11:45:00', 'Routine check with Dr. Rao'),
('APPT407','D1003','H1003','A2008','P1003', '2025-07-11 08:00:00', '2025-07-11 08:05:00', 'BOOKED', 3, '2025-07-12 13:40:00', '2025-07-12 14:00:00', 'Urgent Neuro appointment');

-- 3 New CANCELLED appointments for H1003 (future-dated cancellation to distinguish from past ones)
INSERT INTO Appointment (appointment_id, doctor_id, h_id, availability_id, provider_id, requested_at, booked_at, cancelled_at, status, slot_no, start, end, notes)
VALUES
('APPT408','D1003','H1003','A2011','P1003', '2025-07-24 10:00:00', '2025-07-24 10:05:00', NOW(), 'CANCELLED', 4, '2025-07-25 09:45:00', '2025-07-25 10:00:00', 'Cancelled by patient 2 days before'),
('APPT409','D1005','H1003','A2009','P1005', '2025-07-14 11:00:00', '2025-07-14 11:05:00', NOW(), 'CANCELLED', 4, '2025-07-15 11:45:00', '2025-07-15 12:00:00', 'Doctor unavailable'),
('APPT410','D1003','H1003','A2007','P1003', '2025-07-09 13:00:00', '2025-07-09 13:05:00', NOW(), 'CANCELLED', 1, '2025-07-10 09:00:00', '2025-07-10 09:15:00', 'Rescheduled to a later date.');

-- ===========================
-- Add 20 More Future Availabilities for Doctor D1003
-- ===========================

-- Starting from August 1, 2025

-- August 1, 2025 (2 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2012','D1003','2025-08-01','09:00:00','10:30:00','STANDARD',5,FALSE,'Morning slots for D1003'),
('A2013','D1003','2025-08-01','14:00:00','15:30:00','ADHOC',4,FALSE,'Afternoon adhoc for D1003');

-- August 2, 2025 (3 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2014','D1003','2025-08-02','09:30:00','10:30:00','STANDARD',3,FALSE,'Saturday morning availability'),
('A2015','D1003','2025-08-02','12:00:00','13:00:00','STANDARD',2,FALSE,'Saturday lunch-time slots'),
('A2016','D1003','2025-08-02','16:00:00','17:00:00','ADHOC',2,FALSE,'Saturday late afternoon');

-- August 4, 2025 (Monday - 2 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2017','D1003','2025-08-04','10:00:00','11:00:00','STANDARD',4,FALSE,'Monday standard slots'),
('A2018','D1003','2025-08-04','15:00:00','16:30:00','STANDARD',5,FALSE,'Monday afternoon');

-- August 5, 2025 (3 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2019','D1003','2025-08-05','08:30:00','09:30:00','ADHOC',3,FALSE,'Early Tuesday session'),
('A2020','D1003','2025-08-05','11:00:00','12:00:00','STANDARD',4,FALSE,'Mid-morning availability'),
('A2021','D1003','2025-08-05','17:00:00','18:00:00','STANDARD',3,FALSE,'Late Tuesday slots');

-- August 6, 2025 (2 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2022','D1003','2025-08-06','09:00:00','10:00:00','STANDARD',4,FALSE,'Wednesday morning'),
('A2023','D1003','2025-08-06','13:00:00','14:30:00','ADHOC',3,FALSE,'Wednesday afternoon adhoc');

-- August 7, 2025 (3 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2024','D1003','2025-08-07','10:00:00','11:00:00','STANDARD',4,FALSE,'Thursday slots'),
('A2025','D1003','2025-08-07','12:30:00','13:30:00','STANDARD',3,FALSE,'Thursday mid-day'),
('A2026','D1003','2025-08-07','15:00:00','16:00:00','STANDARD',4,FALSE,'Thursday late afternoon');

-- August 8, 2025 (2 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2027','D1003','2025-08-08','09:30:00','11:00:00','ADHOC',5,FALSE,'Friday morning adhoc'),
('A2028','D1003','2025-08-08','14:00:00','15:00:00','STANDARD',4,FALSE,'Friday afternoon');

-- August 9, 2025 (3 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2029','D1003','2025-08-09','09:00:00','10:00:00','STANDARD',3,FALSE,'Saturday early slots'),
('A2030','D1003','2025-08-09','11:00:00','12:00:00','STANDARD',2,FALSE,'Saturday mid-morning'),
('A2031','D1003','2025-08-09','14:00:00','15:00:00','ADHOC',2,FALSE,'Saturday afternoon adhoc');

-- August 11, 2025 (Monday - 2 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2032','D1003','2025-08-11','10:30:00','11:30:00','STANDARD',4,FALSE,'Monday late morning'),
('A2033','D1003','2025-08-11','16:00:00','17:00:00','STANDARD',3,FALSE,'Monday evening slots');

-- August 12, 2025 (3 slots)
INSERT INTO Doctor_availability (availability_id, doctor_id, available_date, start_time, end_time, slot_type, max_capacity, is_recurring, notes) VALUES
('A2034','D1003','2025-08-12','09:00:00','10:00:00','STANDARD',4,FALSE,'Tuesday morning'),
('A2035','D1003','2025-08-12','13:00:00','14:00:00','ADHOC',3,FALSE,'Tuesday mid-day adhoc'),
('A2036','D1003','2025-08-12','16:30:00','17:30:00','STANDARD',3,FALSE,'Tuesday late session');
