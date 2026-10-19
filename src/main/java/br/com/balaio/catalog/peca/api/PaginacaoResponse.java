package br.com.balaio.catalog.peca.api;

public record PaginacaoResponse(int pagina,int tamanho,long total,int totalPaginas){}