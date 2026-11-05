package br.com.balaio.catalog.peca.api;

public record ErroDetalheResponse(
        String codigo,
        String mensagem,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String campo
) {
    public ErroDetalheResponse(String codigo, String mensagem) {
        this(codigo, mensagem, null);
    }
}
