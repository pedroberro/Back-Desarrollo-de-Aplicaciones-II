-- The password seeded in V10 is written in that file, so anyone with the code could sign in as
-- the administrator. This replaces it with a random one (BCrypt, cost 12) that lives outside
-- the repository, and moves password_changed_at so sessions opened with the old one end.
UPDATE usuarios
SET password = '$2a$12$8yEs3/flGqQgQppfNS8YiOS.dNy1WwYBaEdFMJHYALI070JHwWELi',
    password_changed_at = date_trunc('second', NOW())
WHERE email = 'admin@plantalibre.local';
