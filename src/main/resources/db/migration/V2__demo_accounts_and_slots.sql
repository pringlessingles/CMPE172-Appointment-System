-- Coursework demo accounts only. Shared password: Appointment123!
-- This migration runs once; restarting does not change dates or overwrite data.
INSERT INTO users (id,email,password_hash,full_name,role) VALUES
(1,'maya.chen@doctor.example','$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O','Dr. Maya Chen','PROVIDER'),
(2,'jordan.lee@doctor.example','$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O','Dr. Jordan Lee','PROVIDER'),
(3,'sofia.reyes@example.com','$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O','Sofia Reyes','CUSTOMER'),
(4,'admin@doctor.example','$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O','Office Administrator','ADMIN'),
(5,'alex.morgan@example.com','$2a$10$IyP4k.pfeHisQJo7eDA6qenJX.wBZOQrE5NemnID7IxYiceGYvF3O','Alex Morgan','CUSTOMER');
INSERT INTO providers (id,user_id,specialty) VALUES (1,1,'Family Medicine'),(2,2,'Pediatrics');
INSERT INTO services (id,provider_id,name,description,duration_minutes,price) VALUES
(1,1,'General Consultation','Routine evaluation for a new or ongoing concern.',30,85.00),
(2,1,'Annual Physical','Yearly physical examination and health review.',45,120.00),
(3,2,'Vaccination Appointment','Review vaccine history and recommended immunizations.',20,40.00),
(4,2,'Blood Pressure Follow-up','Focused follow-up for blood-pressure monitoring.',20,45.00);
INSERT INTO availability_slots (id,provider_id,service_id,starts_at,ends_at,status) VALUES
(1,1,1,CURRENT_DATE + 1 + TIME '09:00',CURRENT_DATE + 1 + TIME '09:30','AVAILABLE'),
(2,1,2,CURRENT_DATE + 1 + TIME '10:00',CURRENT_DATE + 1 + TIME '10:45','AVAILABLE'),
(3,2,3,CURRENT_DATE + 1 + TIME '09:20',CURRENT_DATE + 1 + TIME '09:40','AVAILABLE'),
(4,2,4,CURRENT_DATE + 1 + TIME '11:00',CURRENT_DATE + 1 + TIME '11:20','AVAILABLE'),
(5,1,1,CURRENT_DATE + 2 + TIME '13:00',CURRENT_DATE + 2 + TIME '13:30','AVAILABLE'),
(6,2,4,CURRENT_DATE + 2 + TIME '14:00',CURRENT_DATE + 2 + TIME '14:20','AVAILABLE'),
(7,1,1,CURRENT_DATE + 3 + TIME '09:00',CURRENT_DATE + 3 + TIME '09:30','BLOCKED');
ALTER TABLE users ALTER COLUMN id RESTART WITH 6;
ALTER TABLE providers ALTER COLUMN id RESTART WITH 3;
ALTER TABLE services ALTER COLUMN id RESTART WITH 5;
ALTER TABLE availability_slots ALTER COLUMN id RESTART WITH 8;
