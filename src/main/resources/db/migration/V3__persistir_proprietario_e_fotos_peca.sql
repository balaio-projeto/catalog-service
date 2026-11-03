ALTER TABLE pecas ADD COLUMN proprietario_id UUID;
ALTER TABLE pecas ADD COLUMN criada_em TIMESTAMPTZ;
ALTER TABLE pecas DROP CONSTRAINT pecas_preco_check;
ALTER TABLE pecas ADD CONSTRAINT ck_pecas_preco_publicacao CHECK
    (preco >= 0 AND (situacao <> 'publicada' OR preco > 0));
CREATE INDEX idx_pecas_proprietario ON pecas(proprietario_id);

CREATE TABLE peca_fotos (
    id UUID PRIMARY KEY,
    peca_slug VARCHAR(160) NOT NULL REFERENCES pecas(slug) ON DELETE CASCADE,
    foto_id VARCHAR(160) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    ordem INTEGER NOT NULL,
    formato VARCHAR(16),
    conteudo BYTEA,
    CONSTRAINT uk_peca_foto_id UNIQUE (peca_slug, foto_id),
    CONSTRAINT uk_peca_foto_ordem UNIQUE (peca_slug, ordem)
);
