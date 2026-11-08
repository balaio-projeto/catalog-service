ALTER TABLE reservas_peca DROP CONSTRAINT reservas_peca_estado_check;
ALTER TABLE reservas_peca ADD CONSTRAINT reservas_peca_estado_check
    CHECK (estado IN ('RESERVADA', 'CONSUMIDA', 'LIBERADA', 'EXPIRADA'));
DROP INDEX uq_reserva_ativa_peca;
CREATE UNIQUE INDEX uq_reserva_ativa_peca ON reservas_peca(slug)
    WHERE estado IN ('RESERVADA', 'CONSUMIDA');
