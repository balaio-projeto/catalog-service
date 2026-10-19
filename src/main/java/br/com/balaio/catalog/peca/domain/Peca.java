package br.com.balaio.catalog.peca.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record Peca(
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
        OffsetDateTime inativadoEm,
        Integer ordemCatalogo) {

    public Peca {
        historia = historia == null ? List.of() : List.copyOf(historia);
    }
}
