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
