package br.com.balaio.catalog.peca.api;

import java.math.BigDecimal;
import java.util.List;
import br.com.balaio.catalog.peca.domain.Peca;
import br.com.balaio.catalog.peca.domain.FotoPeca;

public record PecaResponse(
        String slug,
        String nome,
        String artesao,
        String territorio,
        String tecnica,
        String categoria,
        String tipo,
        BigDecimal preco,
        BigDecimal desconto,
        String disponibilidade,
        Integer prazoProducaoDias,
        List<String> historia,
        String imagem,
        BigDecimal avaliacao,
        Integer totalAvaliacoes,
        String situacao,
        List<FotoPeca> fotos,
        List<String> ordemFotos,
        String criadaEm) {

    public static PecaResponse from(Peca peca) {
        return new PecaResponse(
                peca.slug(),
                peca.nome(),
                peca.artesao(),
                peca.territorio(),
                peca.tecnica(),
                peca.categoria(),
                peca.tipo(),
                peca.preco(),
                peca.desconto(),
                peca.disponibilidade(),
                peca.prazoProducaoDias(),
                peca.historia(),
                peca.imagem(),
                peca.avaliacao(),
                peca.totalAvaliacoes(),
                peca.situacao(),
                peca.fotos(),
                peca.fotos().stream().map(FotoPeca::id).toList(),
                peca.criadaEm() == null ? null : peca.criadaEm().toString());
    }
}
