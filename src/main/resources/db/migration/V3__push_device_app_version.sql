ALTER TABLE push_devices
    ADD COLUMN app_version VARCHAR(40);

UPDATE push_devices
SET app_version = 'legacy build (version not reported)';

ALTER TABLE push_devices
    ALTER COLUMN app_version SET NOT NULL;
