DROP TABLE IF EXISTS referencias_catalogo;
DROP TABLE IF EXISTS reservas_peca;
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
    criada_em TIMESTAMP WITH TIME ZONE,
    materiais JSON NOT NULL DEFAULT '[]',
    autentica BOOLEAN NOT NULL DEFAULT FALSE
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

CREATE TABLE reservas_peca (
    id UUID PRIMARY KEY,
    pedido_id VARCHAR(160),
    slug VARCHAR(160) NOT NULL REFERENCES pecas(slug),
    estado VARCHAR(32) NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE referencias_catalogo (
    grupo VARCHAR(32) NOT NULL,
    valor VARCHAR(160) NOT NULL,
    ordem INTEGER NOT NULL,
    PRIMARY KEY (grupo, valor),
    UNIQUE (grupo, ordem),
    CHECK (grupo IN ('tecnicas', 'territorios', 'categorias', 'tipos', 'etiquetas'))
);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tecnicas', 'Cerâmica & Barro', 1);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tecnicas', 'Bordado & Renda (Renascença)', 2);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tecnicas', 'Escultura em Madeira', 3);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tecnicas', 'Couro Autoral', 4);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tecnicas', 'Xilogravura', 5);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('territorios', 'Mata Norte', 1);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('territorios', 'Cariri', 2);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('territorios', 'Pajeú', 3);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('territorios', 'Petrolina', 4);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('territorios', 'Vale do Ipojuca', 5);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('territorios', 'Caruaru', 6);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('territorios', 'Alto do Moura, Caruaru', 7);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('categorias', 'Decoração & Arte', 1);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('categorias', 'Casa & Mesa', 2);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('categorias', 'Vestuário & Acessórios', 3);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tipos', 'Jarras & Vasos', 1);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tipos', 'Esculturas & Bonecos', 2);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tipos', 'Mesa & Cozinha', 3);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tipos', 'Toalhas & Rendas', 4);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tipos', 'Couro: Bolsas, Cintos & Gibões', 5);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tipos', 'Gravuras & Cordéis', 6);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('etiquetas', 'ceramica', 1);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('etiquetas', 'renascenca', 2);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('etiquetas', 'xilogravura', 3);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('etiquetas', 'couro', 4);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('etiquetas', 'madeira', 5);
INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('etiquetas', 'passoapasso', 6);
