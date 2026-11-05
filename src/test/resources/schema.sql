DROP TABLE IF EXISTS peca_fotos;
DROP TABLE IF EXISTS peca_criacao_lock;
DROP TABLE IF EXISTS pecas;

CREATE TABLE pecas (
    slug VARCHAR(160) PRIMARY KEY,
    nome VARCHAR(240) NOT NULL,
    artesao VARCHAR(160) NOT NULL,
    territorio VARCHAR(160) NOT NULL,
    tecnica VARCHAR(160) NOT NULL,
    categoria VARCHAR(160) NOT NULL,
    tipo VARCHAR(160) NOT NULL,
    preco NUMERIC(12, 2) NOT NULL,
    desconto NUMERIC(5, 2) NOT NULL,
    disponibilidade VARCHAR(32) NOT NULL,
    prazo_producao_dias INTEGER,
    historia VARCHAR ARRAY NOT NULL,
    imagem VARCHAR(500) NOT NULL,
    avaliacao NUMERIC(3, 2),
    total_avaliacoes INTEGER,
    situacao VARCHAR(32) NOT NULL,
    inativado_em TIMESTAMP WITH TIME ZONE,
    ordem_catalogo INTEGER NOT NULL UNIQUE,
    texto_busca CLOB NOT NULL,
    proprietario_id UUID,
    criada_em TIMESTAMP WITH TIME ZONE
);

CREATE TABLE peca_fotos (
    id UUID PRIMARY KEY,
    peca_slug VARCHAR(160) NOT NULL REFERENCES pecas(slug) ON DELETE CASCADE,
    foto_id VARCHAR(160) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    ordem INTEGER NOT NULL,
    formato VARCHAR(16),
    conteudo BYTEA,
    UNIQUE (peca_slug, foto_id),
    UNIQUE (peca_slug, ordem)
);

CREATE TABLE peca_criacao_lock (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO peca_criacao_lock (id) VALUES (1);
