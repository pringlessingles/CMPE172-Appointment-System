INSERT INTO users (id, email, password_hash, full_name, role) VALUES
    (1, 'maya.chen@doctor.example', '$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O', 'Dr. Maya Chen', 'PROVIDER'),
    (2, 'jordan.lee@doctor.example', '$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O', 'Dr. Jordan Lee', 'PROVIDER'),
    (3, 'sofia.reyes@example.com', '$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O', 'Sofia Reyes', 'CUSTOMER'),
    (4, 'admin@doctor.example', '$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O', 'Office Administrator', 'ADMIN'),
    (5, 'alex.morgan@example.com', '$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O', 'Alex Morgan', 'CUSTOMER');

INSERT INTO providers (id, user_id, specialty, active) VALUES
    (1, 1, 'Family Medicine', TRUE),
    (2, 2, 'Pediatrics', TRUE);

INSERT INTO services (id, provider_id, name, description, duration_minutes, price, active) VALUES
    (1, 1, 'General Consultation', 'Routine evaluation for a new or ongoing concern.', 30, 85.00, TRUE),
    (2, 1, 'Annual Physical', 'Yearly physical examination and health review.', 45, 120.00, TRUE),
    (3, 2, 'Vaccination Appointment', 'Review vaccine history and recommended immunizations.', 20, 40.00, TRUE),
    (4, 2, 'Blood Pressure Follow-up', 'Focused follow-up for blood-pressure monitoring.', 20, 45.00, TRUE);

INSERT INTO availability_slots (id, provider_id, service_id, starts_at, ends_at, status) VALUES
    (1, 1, 1, DATEADD('DAY', 1, CURRENT_DATE) + TIME '09:00:00', DATEADD('DAY', 1, CURRENT_DATE) + TIME '09:30:00', 'AVAILABLE'),
    (2, 1, 2, DATEADD('DAY', 1, CURRENT_DATE) + TIME '10:00:00', DATEADD('DAY', 1, CURRENT_DATE) + TIME '10:45:00', 'AVAILABLE'),
    (3, 2, 3, DATEADD('DAY', 1, CURRENT_DATE) + TIME '09:20:00', DATEADD('DAY', 1, CURRENT_DATE) + TIME '09:40:00', 'AVAILABLE'),
    (4, 2, 4, DATEADD('DAY', 1, CURRENT_DATE) + TIME '11:00:00', DATEADD('DAY', 1, CURRENT_DATE) + TIME '11:20:00', 'AVAILABLE'),
    (5, 1, 1, DATEADD('DAY', 2, CURRENT_DATE) + TIME '13:00:00', DATEADD('DAY', 2, CURRENT_DATE) + TIME '13:30:00', 'AVAILABLE'),
    (6, 2, 4, DATEADD('DAY', 2, CURRENT_DATE) + TIME '14:00:00', DATEADD('DAY', 2, CURRENT_DATE) + TIME '14:20:00', 'AVAILABLE'),
    (7, 1, 1, DATEADD('DAY', 3, CURRENT_DATE) + TIME '09:00:00', DATEADD('DAY', 3, CURRENT_DATE) + TIME '09:30:00', 'BLOCKED');

ALTER TABLE users ALTER COLUMN id RESTART WITH 6;
ALTER TABLE providers ALTER COLUMN id RESTART WITH 3;
ALTER TABLE services ALTER COLUMN id RESTART WITH 5;
ALTER TABLE availability_slots ALTER COLUMN id RESTART WITH 8;
