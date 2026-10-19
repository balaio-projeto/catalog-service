package br.com.balaio.catalog.peca.application;

import java.util.List;
import br.com.balaio.catalog.peca.domain.Peca;

public record PaginaPecas(List<Peca> pecas, int pagina, int tamanho, long total, int totalPaginas) {
    public PaginaPecas {
        pecas = List.copyOf(pecas);
    }
}
