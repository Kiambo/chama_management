-- The managing role is called TREASURER (it was RECORDER), at the treasurer's request.
ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
UPDATE app_user SET role = 'TREASURER' WHERE role = 'RECORDER';
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK (role IN ('TREASURER', 'MEMBER'));

-- Profile details (for later: password reset and notifications).
ALTER TABLE app_user ADD COLUMN email VARCHAR(254);
ALTER TABLE app_user ADD COLUMN phone VARCHAR(30);
ALTER TABLE app_user ADD COLUMN password_changed_at TIMESTAMPTZ;
