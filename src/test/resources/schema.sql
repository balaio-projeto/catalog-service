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
    texto_busca CLOB NOT NULL
);
