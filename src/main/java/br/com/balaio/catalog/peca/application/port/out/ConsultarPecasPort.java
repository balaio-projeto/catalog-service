package br.com.balaio.catalog.peca.application.port.out;

import java.util.List;
import br.com.balaio.catalog.peca.application.FiltroCatalogo;
import br.com.balaio.catalog.peca.application.OrdenacaoCatalogo;
import br.com.balaio.catalog.peca.domain.Peca;

public interface ConsultarPecasPort {
    long contar(FiltroCatalogo filtro);

    List<Peca> buscar(FiltroCatalogo filtro, int indicePagina, int tamanho, OrdenacaoCatalogo ordenacao);
}
