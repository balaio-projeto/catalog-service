package br.com.balaio.catalog.peca.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import br.com.balaio.catalog.peca.application.FiltroCatalogo;
import br.com.balaio.catalog.peca.application.OrdenacaoCatalogo;
import br.com.balaio.catalog.peca.application.port.in.ListarPecasUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/pecas")
@Tag(name = "Catálogo", description = "Consulta pública de peças")
public class PecasController {
    private final ListarPecasUseCase listarPecas;

    public PecasController(ListarPecasUseCase listarPecas) {
        this.listarPecas = listarPecas;
    }

    @GetMapping
    @Operation(summary = "Listar peças publicadas")
    public PecasListResponse listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tecnica,
            @RequestParam(required = false) String territorio,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String disponibilidade,
            @RequestParam(required = false) Boolean desconto,
            @RequestParam(required = false) String ordenar,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        var resultado = listarPecas.listar(
                new FiltroCatalogo(q, tecnica, territorio, categoria, tipo, disponibilidade, desconto),
                pagina,
                tamanho,
                mapearOrdenacao(ordenar));

        var pecas = resultado.pecas().stream().map(PecaResponse::from).toList();
        var paginacao = new PaginacaoResponse(
                resultado.pagina(),
                resultado.tamanho(),
                resultado.total(),
                resultado.totalPaginas());
        return new PecasListResponse(pecas, null, paginacao);
    }

    private OrdenacaoCatalogo mapearOrdenacao(String valor) {
        if (valor == null) {
            return OrdenacaoCatalogo.PADRAO;
        }

        return switch (valor) {
            case "preco-asc" -> OrdenacaoCatalogo.PRECO_ASC;
            case "preco-desc" -> OrdenacaoCatalogo.PRECO_DESC;
            case "avaliacao" -> OrdenacaoCatalogo.AVALIACAO;
            case "recentes" -> OrdenacaoCatalogo.RECENTES;
            default -> OrdenacaoCatalogo.PADRAO;
        };
    }
}
