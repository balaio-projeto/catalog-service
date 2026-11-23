package br.com.balaio.catalog.peca.application;

import java.math.BigDecimal;

public record FiltroCatalogo(
        String q,
        String tecnica,
        String territorio,
        String categoria,
        String tipo,
        String disponibilidade,
        Boolean desconto,
        BigDecimal precoMin,
        BigDecimal precoMax) {

    public FiltroCatalogo(String q, String tecnica, String territorio, String categoria, String tipo,
            String disponibilidade, Boolean desconto) {
        this(q, tecnica, territorio, categoria, tipo, disponibilidade, desconto, null, null);
    }
}
