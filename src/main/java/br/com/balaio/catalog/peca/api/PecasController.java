package br.com.balaio.catalog.peca.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;
import br.com.balaio.catalog.peca.application.DadosPeca;
import br.com.balaio.catalog.peca.application.ConsultarPecaService;
import br.com.balaio.catalog.peca.application.ErroPecaException;
import br.com.balaio.catalog.peca.application.port.in.CriarPecaUseCase;
import br.com.balaio.catalog.peca.application.port.in.AtualizarPecaUseCase;
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
    private final CriarPecaUseCase criarPeca;
    private final AtualizarPecaUseCase atualizarPeca;
    private final ConsultarPecaService consultarPeca;

    public PecasController(ListarPecasUseCase listarPecas, CriarPecaUseCase criarPeca,
            AtualizarPecaUseCase atualizarPeca, ConsultarPecaService consultarPeca) {
        this.listarPecas = listarPecas;
        this.criarPeca = criarPeca;
        this.atualizarPeca = atualizarPeca;
        this.consultarPeca = consultarPeca;
    }

    @PostMapping
    public ResponseEntity<PecaItemResponse> criar(@RequestBody DadosPeca dados,
            @AuthenticationPrincipal Jwt jwt, @RequestHeader("Authorization") String authorization) {
        UUID dono = exigirArtesao(jwt);
        var criada = criarPeca.criar(dados, dono, authorization.substring(7));
        return ResponseEntity.status(HttpStatus.CREATED).body(new PecaItemResponse(PecaResponse.from(criada), null));
    }

    @PatchMapping("/{slug}")
    public PecaItemResponse atualizar(@PathVariable String slug, @RequestBody DadosPeca dados,
            @AuthenticationPrincipal Jwt jwt) {
        UUID dono = exigirArtesao(jwt);
        return new PecaItemResponse(PecaResponse.from(atualizarPeca.atualizar(slug, dados, dono)), null);
    }

    private UUID exigirArtesao(Jwt jwt) {
        if (jwt == null || !"artesao".equals(jwt.getClaimAsString("papel")))
            throw new ErroPecaException(403, "SEM_PERMISSAO", null, "Operação permitida apenas a artesãos.");
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/{slug}")
    public PecaItemResponse obter(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return new PecaItemResponse(PecaResponse.from(consultarPeca.obter(slug, proprietario(jwt))), null);
    }

    static UUID proprietario(Jwt jwt) {
        return jwt != null && "artesao".equals(jwt.getClaimAsString("papel"))
                ? UUID.fromString(jwt.getSubject()) : null;
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
