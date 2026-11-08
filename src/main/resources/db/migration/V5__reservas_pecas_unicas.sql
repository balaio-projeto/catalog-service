CREATE TABLE reservas_peca (
    id UUID PRIMARY KEY,
    pedido_id VARCHAR(160) NOT NULL,
    slug VARCHAR(160) NOT NULL REFERENCES pecas(slug),
    preco NUMERIC(12,2) NOT NULL,
    artesao VARCHAR(160) NOT NULL,
    proprietario_id UUID,
    chave VARCHAR(160) NOT NULL UNIQUE,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('RESERVADA', 'LIBERADA', 'EXPIRADA')),
    expira_em TIMESTAMPTZ NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (pedido_id, slug)
);
CREATE UNIQUE INDEX uq_reserva_ativa_peca ON reservas_peca(slug) WHERE estado = 'RESERVADA';
CREATE INDEX idx_reserva_expiracao ON reservas_peca(expira_em) WHERE estado = 'RESERVADA';
