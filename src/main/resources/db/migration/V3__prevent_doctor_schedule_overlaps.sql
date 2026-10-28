-- New migration only: never rewrite an applied migration or delete old bookings.
-- Existing overlaps cause migration to stop; resolve those records explicitly.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE availability_slots
    ADD CONSTRAINT ex_doctor_active_slot_overlap
    EXCLUDE USING gist (
        provider_id WITH =,
        tsrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('AVAILABLE', 'BOOKED'));

-- Half-open ranges permit back-to-back sessions (09:00-09:30, 09:30-10:00).
-- BLOCKED slots remain as history but do not reserve the doctor's time.
