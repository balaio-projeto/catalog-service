package br.com.balaio.catalog.peca.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.balaio.catalog.peca.application.port.in.ListarPecasUseCase;
import br.com.balaio.catalog.peca.application.port.out.ConsultarPecasPort;

@Service
@Transactional(readOnly = true)
public class ListarPecasService implements ListarPecasUseCase {
    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final ConsultarPecasPort consultarPecas;

    public ListarPecasService(ConsultarPecasPort consultarPecas) {
        this.consultarPecas = consultarPecas;
    }

    @Override
    public PaginaPecas listar(FiltroCatalogo filtro, Integer paginaSolicitada, Integer tamanhoSolicitado,
            OrdenacaoCatalogo ordenacao) {
        long total = consultarPecas.contar(filtro);
        int tamanho = resolverTamanho(tamanhoSolicitado, total);
        int totalPaginas = Math.max(1, (int) ((total + tamanho - 1) / tamanho));
        int pagina = Math.min(paginaSolicitada == null ? 1 : Math.max(1, paginaSolicitada), totalPaginas);

        var pecas = consultarPecas.buscar(filtro, pagina - 1, tamanho, ordenacao);
        return new PaginaPecas(pecas, pagina, tamanho, total, totalPaginas);
    }

    private int resolverTamanho(Integer tamanhoSolicitado, long total) {
        if (tamanhoSolicitado == null || tamanhoSolicitado == 0) {
            return (int) Math.max(1, Math.min(TAMANHO_MAXIMO_PAGINA, total));
        }
        return Math.max(1, Math.min(TAMANHO_MAXIMO_PAGINA, tamanhoSolicitado));
    }
}
