ALTER TABLE attendance_events
    ALTER COLUMN face_count TYPE INTEGER
    USING face_count::INTEGER;
