package br.com.balaio.catalog.peca.application;

public record FiltroCatalogo(
        String q,
        String tecnica,
        String territorio,
        String categoria,
        String tipo,
        String disponibilidade,
        Boolean desconto) {
}
