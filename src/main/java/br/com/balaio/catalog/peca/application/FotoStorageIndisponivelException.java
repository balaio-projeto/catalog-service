package br.com.balaio.catalog.peca.application;

public class FotoStorageIndisponivelException extends RuntimeException {
    public FotoStorageIndisponivelException() {
        super("Não foi possível acessar o armazenamento de fotos. Tente novamente.");
    }
}
