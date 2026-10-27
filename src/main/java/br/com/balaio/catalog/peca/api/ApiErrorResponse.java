package br.com.balaio.catalog.peca.api;

public record ApiErrorResponse(
        Object dados,
        ErroDetalheResponse erro
) {
}
