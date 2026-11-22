ALTER TABLE peca_fotos ADD COLUMN storage_key VARCHAR(500);

ALTER TABLE peca_fotos ADD CONSTRAINT ck_peca_fotos_binario_externo
    CHECK (storage_key IS NULL OR conteudo IS NULL);

COMMENT ON COLUMN peca_fotos.conteudo IS
    'Compatibilidade temporária com fotos legadas; novas fotos são armazenadas no object storage.';
