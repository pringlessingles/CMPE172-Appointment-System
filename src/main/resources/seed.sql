INSERT INTO users (id, email, password_hash, full_name, role) VALUES
    (1, 'maya.chen@clinic.example', '{noop}milestone1', 'Dr. Maya Chen', 'PROVIDER'),
    (2, 'jordan.lee@clinic.example', '{noop}milestone1', 'Jordan Lee, NP', 'PROVIDER'),
    (3, 'sofia.reyes@example.com', '{noop}milestone1', 'Sofia Reyes', 'CUSTOMER'),
    (4, 'admin@clinic.example', '{noop}milestone1', 'Clinic Administrator', 'ADMIN');

INSERT INTO providers (id, user_id, specialty, active) VALUES
    (1, 1, 'Family Medicine', TRUE),
    (2, 2, 'Preventive Care', TRUE);

INSERT INTO services (id, provider_id, name, description, duration_minutes, price, active) VALUES
    (1, 1, 'Primary Care Visit', 'Routine evaluation for a new or ongoing concern.', 30, 85.00, TRUE),
    (2, 1, 'Annual Wellness Exam', 'Preventive annual health review and screening plan.', 45, 120.00, TRUE),
    (3, 2, 'Vaccination Consultation', 'Review vaccine history and recommended immunizations.', 20, 40.00, TRUE),
    (4, 2, 'Blood Pressure Follow-up', 'Focused follow-up for blood-pressure monitoring.', 20, 45.00, TRUE);

INSERT INTO availability_slots (id, provider_id, service_id, starts_at, ends_at, status) VALUES
    (1, 1, 1, '2026-10-05 09:00:00', '2026-10-05 09:30:00', 'AVAILABLE'),
    (2, 1, 2, '2026-10-05 10:00:00', '2026-10-05 10:45:00', 'AVAILABLE'),
    (3, 2, 3, '2026-10-05 09:20:00', '2026-10-05 09:40:00', 'AVAILABLE'),
    (4, 2, 4, '2026-10-05 11:00:00', '2026-10-05 11:20:00', 'AVAILABLE'),
    (5, 1, 1, '2026-10-06 13:00:00', '2026-10-06 13:30:00', 'AVAILABLE'),
    (6, 2, 4, '2026-10-06 14:00:00', '2026-10-06 14:20:00', 'AVAILABLE'),
    (7, 1, 1, '2026-10-07 09:00:00', '2026-10-07 09:30:00', 'BLOCKED');

