package br.com.balaio.catalog.peca.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
        Integer ordemCatalogo,
        UUID proprietarioId,
        OffsetDateTime criadaEm,
        List<FotoPeca> fotos) {

    public Peca {
        historia = historia == null ? List.of() : List.copyOf(historia);
        fotos = fotos == null ? List.of() : List.copyOf(fotos);
    }

    public Peca(String slug, String nome, String artesao, String territorio, String tecnica,
            String categoria, String tipo, BigDecimal preco, BigDecimal desconto,
            String disponibilidade, Integer prazoProducaoDias, List<String> historia,
            String imagem, BigDecimal avaliacao, Integer totalAvaliacoes, String situacao,
            OffsetDateTime inativadoEm, Integer ordemCatalogo) {
        this(slug, nome, artesao, territorio, tecnica, categoria, tipo, preco, desconto,
                disponibilidade, prazoProducaoDias, historia, imagem, avaliacao,
                totalAvaliacoes, situacao, inativadoEm, ordemCatalogo, null, null, List.of());
    }
}
