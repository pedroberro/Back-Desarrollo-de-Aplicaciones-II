-- The demo data used to leave 30% of its upcoming bookings PENDIENTE, back when that meant
-- "waiting for an administrator". Now PENDIENTE means a charge in flight, and these never had
-- one, so they stayed pending forever. Only demo customers' bookings that never entered the
-- charge queue are touched.
UPDATE reservas r
SET estado = 'CONFIRMADA'
FROM usuarios u
WHERE u.id = r.usuario_id
  AND u.email LIKE '%@demo.plantalibre.local'
  AND r.estado = 'PENDIENTE'
  AND r.cobro_solicitado_el IS NULL;
