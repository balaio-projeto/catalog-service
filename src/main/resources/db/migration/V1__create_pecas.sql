CREATE TABLE pecas (
 slug VARCHAR(160) PRIMARY KEY, nome VARCHAR(240) NOT NULL, artesao VARCHAR(160) NOT NULL,
 territorio VARCHAR(160) NOT NULL, tecnica VARCHAR(160) NOT NULL, categoria VARCHAR(160) NOT NULL,
 tipo VARCHAR(160) NOT NULL, preco NUMERIC(12,2) NOT NULL CHECK(preco>0),
 desconto NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK(desconto>=0 AND desconto<=100),
 disponibilidade VARCHAR(32) NOT NULL, prazo_producao_dias INTEGER, historia TEXT[] NOT NULL,
 imagem VARCHAR(500) NOT NULL, avaliacao NUMERIC(3,2), total_avaliacoes INTEGER,
 situacao VARCHAR(32) NOT NULL DEFAULT 'publicada', inativado_em TIMESTAMPTZ,
 ordem_catalogo INTEGER NOT NULL UNIQUE, texto_busca TEXT NOT NULL
);
CREATE INDEX idx_pecas_situacao_disponibilidade ON pecas(situacao,disponibilidade);
CREATE INDEX idx_pecas_categoria ON pecas(categoria);
CREATE INDEX idx_pecas_territorio ON pecas(territorio);