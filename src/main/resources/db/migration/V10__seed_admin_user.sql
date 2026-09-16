-- Administrator account for demos, loaded by hand: sign-up always creates CLIENTE.
-- Email: admin@plantalibre.local / password: Admin2026 (BCrypt, cost 12). Change it outside the TP.
INSERT INTO usuarios (nombre, email, telefono, password, rol, estado)
VALUES ('Administrador Planta Libre',
        'admin@plantalibre.local',
        '+54 11 0000 0000',
        '$2a$12$CCsMtvbghbEgrAEG1GhyJuDrZ4zVA8TkbqbjXWK.ncFcVpeUyJmZO',
        'ADMIN',
        'ACTIVO')
ON CONFLICT (email) DO NOTHING;
