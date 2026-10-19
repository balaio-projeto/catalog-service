package br.com.balaio.catalog.peca.application.port.in;

import br.com.balaio.catalog.peca.application.FiltroCatalogo;
import br.com.balaio.catalog.peca.application.OrdenacaoCatalogo;
import br.com.balaio.catalog.peca.application.PaginaPecas;

public interface ListarPecasUseCase {
    PaginaPecas listar(FiltroCatalogo filtro, Integer paginaSolicitada, Integer tamanhoSolicitado,
            OrdenacaoCatalogo ordenacao);
}
