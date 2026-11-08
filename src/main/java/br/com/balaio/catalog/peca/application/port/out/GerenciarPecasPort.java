package br.com.balaio.catalog.peca.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.catalog.peca.domain.Peca;
import br.com.balaio.catalog.peca.domain.FotoPeca;

public interface GerenciarPecasPort {
    void bloquearCriacao();
    void bloquearPeca(String slug);
    boolean possuiReservaAtiva(String slug);
    boolean existe(String slug);
    int proximaOrdem();
    Optional<Peca> obter(String slug);
    List<Peca> buscarPorArtesao(String slug, UUID proprietarioId);
    Peca salvar(Peca peca, List<FotoGravacao> fotos);
    Optional<ArquivoFoto> arquivo(UUID id);

    record FotoGravacao(FotoPeca foto, String formato, byte[] conteudo) {
    }
    record ArquivoFoto(String formato, byte[] conteudo) {
    }
}
