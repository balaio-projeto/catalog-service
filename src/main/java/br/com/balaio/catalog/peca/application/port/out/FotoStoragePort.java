package br.com.balaio.catalog.peca.application.port.out;

import java.util.Optional;

/** Armazenamento durável de binários fora do banco relacional. */
public interface FotoStoragePort {
    void guardar(String chave, String formato, byte[] conteudo);

    Optional<byte[]> obter(String chave);

    void remover(String chave);
}
