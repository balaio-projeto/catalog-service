package br.com.balaio.catalog.peca;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;
import br.com.balaio.catalog.peca.application.port.out.ResolverArtesaoPort;
import br.com.balaio.catalog.peca.application.port.out.FotoStoragePort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.balaio.catalog.peca.domain.Peca;
import br.com.balaio.catalog.peca.persistence.PecaJpaEntity;
import br.com.balaio.catalog.CatalogJwtTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class PecasControllerIntegrationTest extends CatalogJwtTestSupport {
    @Autowired
    private TestRestTemplate http;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean
    private ResolverArtesaoPort artesaos;

    @MockitoBean
    private FotoStoragePort fotoStorage;

    @Autowired
    private br.com.balaio.catalog.peca.persistence.LimpezaFotosStorage limpezaFotos;

    @BeforeEach
    void limparBanco() {
        jdbc.update("DELETE FROM limpeza_fotos_storage");
        transacao().executeWithoutResult(status ->
                entityManager.createQuery("delete from PecaJpaEntity").executeUpdate());
    }

    @Test
    void buscaIgnoraAcentosFiltraDescontoELimitaPagina() {
        persistir(peca("leao-imperial", "Leão Imperial", "1250", "20", 1, "publicada"));
        persistir(peca("leao-rascunho", "Leão rascunho", "500", "20", 2, "rascunho"));

        ResponseEntity<Map> resposta = http.getForEntity(
                "/api/v1/pecas?q=LEAO&desconto=true&tamanho=1&pagina=99", Map.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);

        Map corpo = resposta.getBody();

        assertThat(corpo)
                .containsKeys("dados", "erro", "paginacao");

        assertThat(corpo.get("dados")).isNotNull();
        assertThat(corpo.get("erro")).isNull();
        assertThat(corpo.get("paginacao")).isNotNull();
        List<Map> dados = (List<Map>) corpo.get("dados");
        assertThat(dados).hasSize(1);
        assertThat(dados.getFirst()).containsEntry("slug", "leao-imperial");
        Map paginacao = (Map) corpo.get("paginacao");
        assertThat(paginacao).containsEntry("pagina", 1).containsEntry("total", 1);
    }

    @Test
    void buscaHistoriaAposCriacaoEdicaoERemocaoSemConsultarPerfil() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        var corpo = new java.util.HashMap<String, Object>(dados("publicada", 100));
        corpo.put("historia", List.of("Memória do ofício", "Herança de gerações"));
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, corpo, Map.class)
                .getStatusCode().value()).isEqualTo(201);
        org.mockito.Mockito.clearInvocations(artesaos);
        for (String termo : List.of("MEMORIA", "heranca", "gerac", "vaso", "ceramica", "caruaru", "casa", "jarras")) {
            assertThat(buscar(termo)).extracting(peca -> peca.get("slug")).containsExactly("vaso-do-sertao");
        }
        assertThat(requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", jwt,
                Map.of("historia", List.of("Tradição renovada")), Map.class).getStatusCode().value()).isEqualTo(200);
        assertThat(buscar("memoria")).isEmpty();
        assertThat(buscar("TRADICAO")).hasSize(1);
        assertThat(requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", jwt,
                Map.of("historia", List.of()), Map.class).getStatusCode().value()).isEqualTo(200);
        assertThat(buscar("tradicao")).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(artesaos);
    }

    @Test
    void buscaTrataCuringasComoLiteraisEVazioComoListagem() {
        persistir(peca("especial", "Coleção 50%_\\ especial", "100", "0", 1, "publicada"));
        persistir(peca("comum", "Coleção comum", "100", "0", 2, "publicada"));
        for (String termo : List.of("%", "_", "\\")) {
            assertThat(buscar(termo)).extracting(peca -> peca.get("slug")).containsExactly("especial");
        }
        assertThat(buscar("termo-inexistente")).isEmpty();
        assertThat(buscar("")).hasSize(2);
        assertThat(buscar("   ")).hasSize(2);
    }

    private List<Map> buscar(String termo) {
        var uri = java.net.URI.create("/api/v1/pecas?q=" + java.net.URLEncoder.encode(
                termo, java.nio.charset.StandardCharsets.UTF_8));
        var resposta = http.getForEntity(uri, Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).containsEntry("erro", null).containsKey("paginacao");
        return (List<Map>) resposta.getBody().get("dados");
    }

    @Test
    void ordenaPeloPrecoComDesconto() {
        persistir(peca("preco-cheio", "Preço cheio", "80", "0", 1, "publicada"));
        persistir(peca("promocional", "Preço promocional", "100", "50", 2, "publicada"));

        ResponseEntity<Map> resposta = http.getForEntity("/api/v1/pecas?ordenar=preco-asc", Map.class);

        List<Map> dados = (List<Map>) resposta.getBody().get("dados");
        assertThat(dados.getFirst()).containsEntry("slug", "promocional");
    }

    @Test
    void faixaDePrecoUsaPrecoFinalComDescontoEValidaLimites() {
        persistir(peca("barata", "Barata", "40", "0", 1, "publicada"));
        persistir(peca("promocional", "Promocional", "200", "50", 2, "publicada"));
        persistir(peca("cara", "Cara", "300", "0", 3, "publicada"));

        assertThat(slugsDaListagem("/api/v1/pecas?precoMin=50&precoMax=100")).containsExactly("promocional");
        assertThat(slugsDaListagem("/api/v1/pecas?precoMin=100")).containsExactly("promocional", "cara");
        assertThat(slugsDaListagem("/api/v1/pecas?precoMax=99.99")).containsExactly("barata");
        assertThat(slugsDaListagem("/api/v1/pecas?precoMin=100&precoMax=100")).containsExactly("promocional");

        for (var invalida : Map.of("precoMin=-1", "precoMin", "precoMax=-0.01", "precoMax",
                "precoMin=200&precoMax=100", "precoMax").entrySet()) {
            ResponseEntity<Map> resposta = http.getForEntity("/api/v1/pecas?" + invalida.getKey(), Map.class);
            assertThat(resposta.getStatusCode().value()).isEqualTo(400);
            assertThat(resposta.getBody()).containsEntry("dados", null);
            assertThat((Map) resposta.getBody().get("erro")).containsEntry("codigo", "REQUISICAO_INVALIDA")
                    .containsEntry("campo", invalida.getValue());
        }
        ResponseEntity<Map> malformada = http.getForEntity("/api/v1/pecas?precoMax=caro", Map.class);
        assertThat(malformada.getStatusCode().value()).isEqualTo(400);
    }

    private List<String> slugsDaListagem(String caminho) {
        ResponseEntity<Map> resposta = http.getForEntity(caminho, Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        return ((List<Map>) resposta.getBody().get("dados")).stream().map(peca -> (String) peca.get("slug")).toList();
    }

    @Test
    void parametroPaginaInvalidoRetornaErroPadronizado() {
        ResponseEntity<Map> resposta = http.getForEntity(
                "/api/v1/pecas?pagina=abc",
                Map.class
        );

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);

        Map corpo = resposta.getBody();
        assertThat(corpo).containsKeys("dados", "erro");
        assertThat(corpo).containsEntry("dados", null);
        assertThat(corpo).doesNotContainKey("paginacao");

        Map erro = (Map) corpo.get("erro");
        assertThat(erro)
                .containsEntry("codigo", "REQUISICAO_INVALIDA")
                .containsEntry("mensagem", "Dados inválidos.");
    }

    @Test
    void rotaInexistenteRetornaErroPadronizado() {
        ResponseEntity<Map> resposta = http.getForEntity(
                "/api/v1/rota-inexistente",
                Map.class
        );

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);

        Map corpo = resposta.getBody();
        assertThat(corpo).containsKeys("dados", "erro");
        assertThat(corpo).containsEntry("dados", null);
        assertThat(corpo).doesNotContainKey("paginacao");

        Map erro = (Map) corpo.get("erro");
        assertThat(erro)
                .containsEntry("codigo", "RECURSO_NAO_ENCONTRADO")
                .containsEntry("mensagem", "Recurso não encontrado.");
    }

    @Test
    void criaPublicadaComDonoDoJwtERecusaEdicaoDeOutroArtesao() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var resposta = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), dados("publicada", 120), Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(201);
        Map criada = (Map) resposta.getBody().get("dados");
        assertThat(criada).containsEntry("artesao", "artesao-" + dono).containsEntry("slug", "vaso-do-sertao");
        assertThat(http.getForEntity("/api/v1/pecas", Map.class).getBody().get("dados").toString())
                .contains("vaso-do-sertao");

        var outro = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao",
                token(UUID.randomUUID(), "artesao"), Map.of("nome", "Mudado", "artesao", "invasor"), Map.class);
        assertThat(outro.getStatusCode().value()).isEqualTo(404);
        var propria = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao",
                token(dono, "artesao"), Map.of("nome", "Novo nome", "artesao", "invasor"), Map.class);
        assertThat(propria.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map) propria.getBody().get("dados"))).containsEntry("artesao", "artesao-" + dono);
    }

    @Test
    void excluiPecaPropriaSemPermitirAlterarDisponibilidadePorPatch() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        String caminho = "/api/v1/pecas/vaso-do-sertao";
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("publicada", 120), Map.class)
                .getStatusCode().value()).isEqualTo(201);
        assertThat(requisitar(HttpMethod.PATCH, caminho, jwt, Map.of("inativadoEm", "2026-01-01T00:00:00Z"), Map.class)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(requisitar(HttpMethod.DELETE, caminho, token(UUID.randomUUID(), "artesao"), null, Map.class)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(requisitar(HttpMethod.DELETE, caminho, token(UUID.randomUUID(), "comprador"), null, Map.class)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(requisitar(HttpMethod.DELETE, caminho, jwt, null, Map.class).getStatusCode().value())
                .isEqualTo(204);
        assertThat(requisitar(HttpMethod.DELETE, caminho, jwt, null, Map.class).getStatusCode().value())
                .isEqualTo(204);
        assertThat(http.getForEntity(caminho, Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(requisitar(HttpMethod.PATCH, caminho, jwt, Map.of("preco", 130), Map.class)
                .getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void reativaPecaPropriaInativadaDeVoltaNaVitrine() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        String caminho = "/api/v1/pecas/vaso-do-sertao";
        String reativacao = caminho + "/reativacao";
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("publicada", 120), Map.class)
                .getStatusCode().value()).isEqualTo(201);
        assertThat(requisitar(HttpMethod.DELETE, caminho, jwt, null, Map.class).getStatusCode().value())
                .isEqualTo(204);
        assertThat(http.getForEntity(caminho, Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(requisitar(HttpMethod.POST, reativacao, token(UUID.randomUUID(), "artesao"), null, Map.class)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(requisitar(HttpMethod.POST, reativacao, token(UUID.randomUUID(), "comprador"), null, Map.class)
                .getStatusCode().value()).isEqualTo(403);
        var reativada = requisitar(HttpMethod.POST, reativacao, jwt, null, Map.class);
        assertThat(reativada.getStatusCode().value()).isEqualTo(200);
        assertThat((Map) reativada.getBody().get("dados")).containsEntry("inativadoEm", null);
        assertThat(requisitar(HttpMethod.POST, reativacao, jwt, null, Map.class).getStatusCode().value())
                .isEqualTo(200);
        assertThat(http.getForEntity(caminho, Map.class).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void rascunhoNaoApareceEValidaPrecoPorCampo() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var rascunho = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), dados("rascunho", 0), Map.class);
        assertThat(rascunho.getStatusCode().value()).isEqualTo(201);
        assertThat(http.getForEntity("/api/v1/pecas", Map.class).getBody().get("dados").toString())
                .doesNotContain("vaso-do-sertao");
        var invalido = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", token(dono, "artesao"),
                Map.of("situacao", "publicada", "preco", 0), Map.class);
        assertThat(invalido.getStatusCode().value()).isEqualTo(400);
        assertThat((Map) invalido.getBody().get("erro")).containsEntry("campo", "preco");
    }

    @Test
    void criaComDescontoEEditaDescontoRefletindoEmOfertasEPrecoFinal() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        var comDesconto = new java.util.HashMap<String, Object>(dados("publicada", 100));
        comDesconto.put("desconto", 15);
        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, comDesconto, Map.class);
        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        assertThat(descontoDe((Map) criada.getBody().get("dados"))).isEqualByComparingTo("15");
        assertThat(slugsDaListagem("/api/v1/pecas?desconto=true")).containsExactly("vaso-do-sertao");
        assertThat(slugsDaListagem("/api/v1/pecas?precoMin=85&precoMax=85")).containsExactly("vaso-do-sertao");

        var editada = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", jwt,
                Map.of("desconto", 12.5), Map.class);
        assertThat(editada.getStatusCode().value()).isEqualTo(200);
        assertThat(descontoDe((Map) editada.getBody().get("dados"))).isEqualByComparingTo("12.5");
        assertThat(slugsDaListagem("/api/v1/pecas?precoMin=87.5&precoMax=87.5")).containsExactly("vaso-do-sertao");

        var semTocarNoDesconto = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", jwt,
                Map.of("nome", "Vaso renomeado"), Map.class);
        assertThat(descontoDe((Map) semTocarNoDesconto.getBody().get("dados"))).isEqualByComparingTo("12.5");

        var zerada = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", jwt, Map.of("desconto", 0), Map.class);
        assertThat(descontoDe((Map) zerada.getBody().get("dados"))).isEqualByComparingTo("0");
        assertThat(slugsDaListagem("/api/v1/pecas?desconto=true")).isEmpty();

        var semDesconto = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("publicada", 100), Map.class);
        assertThat(descontoDe((Map) semDesconto.getBody().get("dados"))).isEqualByComparingTo("0");
    }

    @Test
    void recusaDescontoForaDaFaixaComMaisDeDuasCasasOuQueZeraOPreco() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        for (Object invalido : List.of(-1, 90.01, 100, 10.555, "muito")) {
            var payload = new java.util.HashMap<String, Object>(dados("publicada", 100));
            payload.put("desconto", invalido);
            var resposta = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, payload, Map.class);
            assertThat(resposta.getStatusCode().value()).as("desconto %s", invalido).isEqualTo(400);
            assertThat((Map) resposta.getBody().get("erro")).containsEntry("codigo", "REQUISICAO_INVALIDA")
                    .containsEntry("campo", "desconto");
        }
        assertThat(slugsDaListagem("/api/v1/pecas")).isEmpty();

        var precoMinimo = new java.util.HashMap<String, Object>(dados("publicada", 100));
        precoMinimo.put("preco", 0.01);
        precoMinimo.put("desconto", 60);
        var zeraPreco = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, precoMinimo, Map.class);
        assertThat(zeraPreco.getStatusCode().value()).isEqualTo(400);
        assertThat((Map) zeraPreco.getBody().get("erro")).containsEntry("campo", "desconto");

        var noLimite = new java.util.HashMap<String, Object>(dados("publicada", 100));
        noLimite.put("desconto", 90);
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, noLimite, Map.class).getStatusCode().value())
                .isEqualTo(201);
        var acimaDoLimite = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", jwt,
                Map.of("desconto", 95), Map.class);
        assertThat(acimaDoLimite.getStatusCode().value()).isEqualTo(400);
        assertThat((Map) acimaDoLimite.getBody().get("erro")).containsEntry("campo", "desconto");
        var mantida = requisitar(HttpMethod.GET, "/api/v1/pecas/vaso-do-sertao", jwt, null, Map.class);
        assertThat(descontoDe((Map) mantida.getBody().get("dados"))).isEqualByComparingTo("90");
    }

    @Test
    void faixaDePrecoUsaPrecoFinalArredondadoComoNaReserva() {
        persistir(peca("quebrada", "Quebrada", "99.99", "15", 1, "publicada"));

        assertThat(slugsDaListagem("/api/v1/pecas?precoMin=84.99&precoMax=84.99")).containsExactly("quebrada");
        assertThat(slugsDaListagem("/api/v1/pecas?precoMin=85")).isEmpty();
    }

    private BigDecimal descontoDe(Map peca) {
        return new BigDecimal(String.valueOf(peca.get("desconto")));
    }

    @Test
    void fluxoCriacaoListagemDetalheEEdicaoRespeitaVisibilidade() {
        UUID dono = UUID.randomUUID();
        UUID outro = UUID.randomUUID();
        String slugArtesao = "artesao-" + dono;
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(slugArtesao);
        String jwt = token(dono, "artesao");
        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("rascunho", 100), Map.class);
        assertThat(criada.getStatusCode().value()).isEqualTo(201);

        String lista = "/api/v1/artesaos/" + slugArtesao + "/pecas";
        assertThat((List<?>) http.getForEntity(lista, Map.class).getBody().get("dados")).isEmpty();
        var propria = requisitar(HttpMethod.GET, lista + "/painel", jwt, null, Map.class);
        assertThat((List<?>) propria.getBody().get("dados")).hasSize(1);
        assertThat(((List<Map>) propria.getBody().get("dados")).getFirst())
                .containsEntry("estadoVenda", "rascunho").doesNotContainKey("vendidaEmPedido");
        var alheia = requisitar(HttpMethod.GET, lista, token(outro, "artesao"), null, Map.class);
        assertThat((List<?>) alheia.getBody().get("dados")).isEmpty();

        String detalhe = "/api/v1/pecas/vaso-do-sertao";
        assertThat(http.getForEntity(detalhe, Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(requisitar(HttpMethod.GET, detalhe, token(outro, "artesao"), null, Map.class)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(requisitar(HttpMethod.GET, detalhe, jwt, null, Map.class)
                .getStatusCode().value()).isEqualTo(200);

        var editada = requisitar(HttpMethod.PATCH, detalhe, jwt,
                Map.of("nome", "Vaso editado", "situacao", "publicada"), Map.class);
        assertThat(editada.getStatusCode().value()).isEqualTo(200);
        assertThat((Map) http.getForEntity(detalhe, Map.class).getBody().get("dados"))
                .containsEntry("nome", "Vaso editado");
        assertThat((List<?>) http.getForEntity(lista, Map.class).getBody().get("dados")).hasSize(1);
    }

    @Test
    void pecaExcluidaSaiDaListagemPublicaDoArtesao() {
        UUID dono = UUID.randomUUID();
        String slugArtesao = "artesao-" + dono;
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(slugArtesao);
        String jwt = token(dono, "artesao");
        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("publicada", 120), Map.class);
        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        String slugPeca = (String) ((Map<?, ?>) criada.getBody().get("dados")).get("slug");

        String lista = "/api/v1/artesaos/" + slugArtesao + "/pecas";
        assertThat((List<?>) http.getForEntity(lista, Map.class).getBody().get("dados")).hasSize(1);

        assertThat(requisitar(HttpMethod.DELETE, "/api/v1/pecas/" + slugPeca, jwt, null, Map.class)
                .getStatusCode().value()).isEqualTo(204);
        assertThat((List<?>) http.getForEntity(lista, Map.class).getBody().get("dados")).isEmpty();
    }

    @Test
    void exigeJwtEProibeComprador() {
        var semToken = requisitar(HttpMethod.POST, "/api/v1/pecas", null, dados("publicada", 100), Map.class);
        assertThat(semToken.getStatusCode().value()).isEqualTo(401);
        var comprador = requisitar(HttpMethod.POST, "/api/v1/pecas", token(UUID.randomUUID(), "comprador"),
                dados("publicada", 100), Map.class);
        assertThat(comprador.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void referenciasPublicasAlimentamCriacaoValida() {
        var resposta = http.getForEntity("/api/v1/referencias", Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        Map referencias = (Map) resposta.getBody().get("dados");
        assertThat(referencias).containsKeys("tecnicas", "territorios", "categorias", "tipos", "etiquetas");

        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var payload = new java.util.HashMap<String, Object>(dados("publicada", 100));
        payload.put("tecnica", ((List<?>) referencias.get("tecnicas")).getFirst());
        payload.put("territorio", ((List<?>) referencias.get("territorios")).getFirst());
        payload.put("categoria", ((List<?>) referencias.get("categorias")).getFirst());
        payload.put("tipo", ((List<?>) referencias.get("tipos")).getFirst());
        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), payload, Map.class);
        assertThat(criada.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void persisteMateriaisECalculaSeloDeAutenticidadeNoServidor() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var payload = new java.util.HashMap<String, Object>(dados("publicada", 100));
        payload.put("materiais", List.of("  Barro  ", "Pigmento natural"));

        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), payload, Map.class);
        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        Map dadosCriados = (Map) criada.getBody().get("dados");
        assertThat(dadosCriados).containsEntry("materiais", List.of("Barro", "Pigmento natural"))
                .containsEntry("autentica", true);

        var consultada = http.getForEntity("/api/v1/pecas/vaso-do-sertao", Map.class);
        assertThat(((Map) consultada.getBody().get("dados")))
                .containsEntry("materiais", List.of("Barro", "Pigmento natural"))
                .containsEntry("autentica", true);

        var editada = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", token(dono, "artesao"),
                Map.of("materiais", List.of("Madeira")), Map.class);
        assertThat(((Map) editada.getBody().get("dados")))
                .containsEntry("materiais", List.of("Madeira"))
                .containsEntry("autentica", true);
        var semMateriais = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", token(dono, "artesao"),
                Map.of("materiais", List.of()), Map.class);
        assertThat(((Map) semMateriais.getBody().get("dados")))
                .containsEntry("materiais", List.of())
                .containsEntry("autentica", false);
    }

    @Test
    void rejeitaMateriaisInvalidos() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var payload = new java.util.HashMap<String, Object>(dados("publicada", 100));
        payload.put("materiais", List.of("Barro", "Barro"));
        var resposta = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), payload, Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat((Map) resposta.getBody().get("erro")).containsEntry("campo", "materiais");
    }

    @Test
    void guardaFotosNoObjectStorageEServeCapaPelaUrlPublica() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var dados = new java.util.HashMap<String, Object>(dados("publicada", 100));
        String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==";
        dados.put("fotos", List.of(Map.of("id", "foto-1", "nome", "capa.png", "url", dataUri),
                Map.of("id", "foto-2", "nome", "detalhe.png", "url", dataUri)));
        byte[] conteudo = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==");
        org.mockito.Mockito.when(fotoStorage.obter(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.of(conteudo));
        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), dados, Map.class);
        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        var peca = (Map) criada.getBody().get("dados");
        var fotos = (List<Map>) peca.get("fotos");
        assertThat(fotos).extracting(foto -> foto.get("ordem")).containsExactly(0, 1);
        assertThat(peca).containsEntry("imagem", fotos.getFirst().get("url"));
        var arquivo = http.getForEntity((String) fotos.getFirst().get("url"), byte[].class);
        assertThat(arquivo.getStatusCode().value()).isEqualTo(200);
        assertThat(arquivo.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(arquivo.getBody()).isNotEmpty();
        var persistenciaFoto = jdbc.queryForMap("SELECT conteudo, storage_key FROM peca_fotos WHERE foto_id = ?", "foto-1");
        assertThat(persistenciaFoto.get("conteudo")).isNull();
        assertThat(persistenciaFoto.get("storage_key").toString()).startsWith("catalog/vaso-do-sertao/");
        String chaveSubstituida = jdbc.queryForObject("SELECT storage_key FROM peca_fotos WHERE foto_id = ?", String.class, "foto-2");
        org.mockito.Mockito.verify(fotoStorage, org.mockito.Mockito.times(2)).guardar(
                org.mockito.ArgumentMatchers.contains("catalog/vaso-do-sertao/"),
                org.mockito.ArgumentMatchers.eq("png"),
                org.mockito.ArgumentMatchers.argThat(bytes -> java.util.Arrays.equals(bytes, conteudo)));
        var edicao = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", token(dono, "artesao"),
                Map.of("fotos", List.of(Map.of("id", "foto-1", "nome", "outra-capa.png",
                        "url", fotos.getFirst().get("url")))), Map.class);
        assertThat(edicao.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map) edicao.getBody().get("dados")).get("imagem")).isEqualTo(fotos.getFirst().get("url"));
        limpezaFotos.executar();
        org.mockito.Mockito.verify(fotoStorage).remover(chaveSubstituida);
        assertThat(http.getForEntity((String) fotos.getFirst().get("url"), byte[].class).getStatusCode().value())
                .isEqualTo(200);

        String chaveStorage = persistenciaFoto.get("storage_key").toString();
        assertThat(requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", token(dono, "artesao"),
                Map.of("fotos", List.of()), Map.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity((String) fotos.getFirst().get("url"), byte[].class).getStatusCode().value())
                .isEqualTo(404);
        limpezaFotos.executar();
        org.mockito.Mockito.verify(fotoStorage).remover(chaveStorage);
    }

    @Test
    void preservaFilaDeLimpezaSeStorageFalhaERepeteAposRecuperacao() {
        String chave = "catalog/diagnostico/orfa.png";
        limpezaFotos.agendar(chave);
        org.mockito.Mockito.doThrow(new br.com.balaio.catalog.peca.application.FotoStorageIndisponivelException())
                .doNothing().when(fotoStorage).remover(chave);
        limpezaFotos.executar();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM limpeza_fotos_storage", Integer.class)).isEqualTo(1);
        limpezaFotos.executar();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM limpeza_fotos_storage", Integer.class)).isZero();
    }

    @Test
    void recusaFormatoTamanhoEQuantidadeDeFotosForaDoLimite() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        String png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==";

        var formatoInvalido = new java.util.HashMap<String, Object>(dados("publicada", 100));
        formatoInvalido.put("fotos", List.of(Map.of("id", "gif", "nome", "foto.gif",
                "url", "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==")));
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, formatoInvalido, Map.class)
                .getStatusCode().value()).isEqualTo(400);

        var quantidadeInvalida = new java.util.HashMap<String, Object>(dados("publicada", 100));
        quantidadeInvalida.put("fotos", java.util.stream.IntStream.range(0, 6)
                .mapToObj(indice -> Map.of("id", "foto-" + indice, "nome", "foto.png", "url", png)).toList());
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, quantidadeInvalida, Map.class)
                .getStatusCode().value()).isEqualTo(400);

        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        byte[] assinaturaPng = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};
        System.arraycopy(assinaturaPng, 0, grande, 0, assinaturaPng.length);
        var tamanhoInvalido = new java.util.HashMap<String, Object>(dados("publicada", 100));
        tamanhoInvalido.put("fotos", List.of(Map.of("id", "grande", "nome", "grande.png",
                "url", "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(grande))));
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, tamanhoInvalido, Map.class)
                .getStatusCode().value()).isEqualTo(400);
        org.mockito.Mockito.verifyNoInteractions(fotoStorage);
    }

    @Test
    void homonimasRecebemSlugsDiferentesEPrazoFracionarioEhRecusado() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        var primeira = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("publicada", 100), Map.class);
        var segunda = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("publicada", 100), Map.class);
        assertThat(((Map) primeira.getBody().get("dados"))).containsEntry("slug", "vaso-do-sertao");
        assertThat(((Map) segunda.getBody().get("dados"))).containsEntry("slug", "vaso-do-sertao-2");
        var sobEncomenda = new java.util.HashMap<String, Object>(dados("publicada", 100));
        sobEncomenda.put("disponibilidade", "encomenda");
        sobEncomenda.put("prazoProducaoDias", 2.5);
        var invalida = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, sobEncomenda, Map.class);
        assertThat(invalida.getStatusCode().value()).isEqualTo(400);
        assertThat((Map) invalida.getBody().get("erro")).containsEntry("campo", "prazoProducaoDias");
        var precoRuim = new java.util.HashMap<String, Object>(dados("publicada", 100));
        precoRuim.put("preco", "não é número");
        var erroPreco = requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, precoRuim, Map.class);
        assertThat(erroPreco.getStatusCode().value()).isEqualTo(400);
        assertThat((Map) erroPreco.getBody().get("erro")).containsEntry("campo", "preco");
    }

    @Test
    void criacoesSimultaneasComNomesIguaisEDiferentesRecebemSlugEOrdemUnicos() throws Exception {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        var iguais = criarAoMesmoTempo(jwt, "Peça simultânea", "Peça simultânea");
        assertThat(iguais).extracting(resposta -> ((Map) resposta.getBody().get("dados")).get("slug"))
                .containsExactlyInAnyOrder("peca-simultanea", "peca-simultanea-2");
        var diferentes = criarAoMesmoTempo(jwt, "Peça A", "Peça B");
        assertThat(diferentes).extracting(resposta -> ((Map) resposta.getBody().get("dados")).get("slug"))
                .containsExactlyInAnyOrder("peca-a", "peca-b");
        var ordens = transacao().execute(status -> entityManager
                .createQuery("select p.ordemCatalogo from PecaJpaEntity p", Integer.class).getResultList());
        assertThat(ordens).hasSize(4).doesNotHaveDuplicates();
    }

    @Test
    void relacionadasRespeitamTecnicaVisibilidadeOrdemELimite() {
        persistir(peca("atual", "Atual", "100", "0", 1, "publicada"));
        persistir(peca("segunda", "Segunda", "100", "0", 3, "publicada"));
        persistir(peca("primeira", "Primeira", "100", "0", 2, "publicada"));
        persistir(peca("rascunho", "Rascunho", "100", "0", 4, "rascunho"));
        persistir(peca("inativa", "Inativa", "100", "0", 5, "publicada"));
        persistir(peca("outra-tecnica", "Outra", "100", "0", 6, "publicada"));
        transacao().executeWithoutResult(status -> {
            entityManager.createNativeQuery("UPDATE pecas SET inativado_em = CURRENT_TIMESTAMP WHERE slug = 'inativa'").executeUpdate();
            entityManager.createNativeQuery("UPDATE pecas SET tecnica = 'Xilogravura' WHERE slug = 'outra-tecnica'").executeUpdate();
        });

        var resposta = http.getForEntity("/api/v1/pecas/atual/relacionadas", Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).containsEntry("erro", null).doesNotContainKey("paginacao");
        assertThat((List<Map>) resposta.getBody().get("dados"))
                .extracting(p -> p.get("slug")).containsExactly("primeira", "segunda");
        for (int limite : List.of(1, -1)) {
            var limitada = http.getForEntity("/api/v1/pecas/atual/relacionadas?limite=" + limite, Map.class);
            assertThat((List<Map>) limitada.getBody().get("dados"))
                    .extracting(p -> p.get("slug")).containsExactly("primeira");
        }
        var vazia = http.getForEntity("/api/v1/pecas/outra-tecnica/relacionadas", Map.class);
        assertThat(vazia.getStatusCode().value()).isEqualTo(200);
        assertThat((List<?>) vazia.getBody().get("dados")).isEmpty();
        for (String slug : List.of("inexistente", "rascunho", "inativa")) {
            var ausente = http.getForEntity("/api/v1/pecas/" + slug + "/relacionadas", Map.class);
            assertThat(ausente.getStatusCode().value()).isEqualTo(404);
            assertThat(ausente.getBody()).containsEntry("dados", null);
            assertThat((Map) ausente.getBody().get("erro")).containsEntry("codigo", "RECURSO_NAO_ENCONTRADO");
        }
    }

    @Test
    void relacionadasUsamLimitePadraoETetoDeCem() {
        for (int i = 0; i < 103; i++) {
            persistir(peca("peca-" + i, "Peça " + i, "100", "0", i + 1, "publicada"));
        }
        for (String query : List.of("", "?limite=0")) {
            var resposta = http.getForEntity("/api/v1/pecas/peca-0/relacionadas" + query, Map.class);
            assertThat((List<?>) resposta.getBody().get("dados")).hasSize(3);
        }
        var resposta = http.getForEntity("/api/v1/pecas/peca-0/relacionadas?limite=200", Map.class);
        assertThat((List<?>) resposta.getBody().get("dados")).hasSize(100);
        assertThat(http.getForEntity("/api/v1/pecas/peca-0/relacionadas?limite=abc", Map.class)
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void referenciasPersistidasMantemOrdemEPassamAValerNaCriacaoSemListasJava() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        // Inserção fora da ordem física para conferir a ordem de curadoria.
        jdbc.update("INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tecnicas', 'Tecnica B', 102)");
        jdbc.update("INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES ('tecnicas', 'Tecnica A', 101)");
        try {
            var payload = new java.util.HashMap<String, Object>(dados("publicada", 100));
            payload.put("tecnica", "Tecnica A");
            assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), payload, Map.class)
                    .getStatusCode().value()).isEqualTo(201);
            var resposta = http.getForEntity("/api/v1/referencias", Map.class);
            assertThat(resposta.getStatusCode().value()).isEqualTo(200);
            var tecnicas = (List<String>) ((Map) resposta.getBody().get("dados")).get("tecnicas");
            assertThat(tecnicas).containsSubsequence("Tecnica A", "Tecnica B");
            assertThat(tecnicas.indexOf("Tecnica A")).isLessThan(tecnicas.indexOf("Tecnica B"));
            var detalhe = http.getForEntity("/api/v1/pecas/vaso-do-sertao", Map.class);
            assertThat((Map) detalhe.getBody().get("dados")).containsEntry("tecnica", "Tecnica A");
        } finally {
            jdbc.update("DELETE FROM referencias_catalogo WHERE grupo = 'tecnicas' AND valor IN ('Tecnica A', 'Tecnica B')");
        }
    }

    @Test
    void referenciasSemDadosRetornamListasVaziasSemFallbackFicticio() {
        var anteriores = jdbc.queryForList("SELECT grupo, valor, ordem FROM referencias_catalogo");
        jdbc.update("DELETE FROM referencias_catalogo");
        try {
            var resposta = http.getForEntity("/api/v1/referencias", Map.class);
            assertThat(resposta.getStatusCode().value()).isEqualTo(200);
            assertThat(resposta.getBody()).containsEntry("erro", null).doesNotContainKey("paginacao");
            var referencias = (Map) resposta.getBody().get("dados");
            assertThat(referencias).hasSize(5);
            for (String grupo : List.of("tecnicas", "territorios", "categorias", "tipos", "etiquetas")) {
                assertThat((List<?>) referencias.get(grupo)).isEmpty();
            }
            var invalida = requisitar(HttpMethod.POST, "/api/v1/pecas", token(UUID.randomUUID(), "artesao"),
                    dados("publicada", 100), Map.class);
            assertThat(invalida.getStatusCode().value()).isEqualTo(400);
            assertThat((Map) invalida.getBody().get("erro")).containsEntry("campo", "tecnica");
        } finally {
            anteriores.forEach(linha -> jdbc.update(
                    "INSERT INTO referencias_catalogo (grupo, valor, ordem) VALUES (?, ?, ?)",
                    linha.get("grupo"), linha.get("valor"), linha.get("ordem")));
        }
    }

    @Test
    void historicoPreservaNarrativaNaOrdemInclusiveAposInativacao() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var payload = new java.util.HashMap<String, Object>(dados("publicada", 100));
        var narrativa = List.of("Origem", "Preparação", "Acabamento");
        payload.put("historia", narrativa);
        String jwt = token(dono, "artesao");
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, payload, Map.class)
                .getStatusCode().value()).isEqualTo(201);
        String caminho = "/api/v1/pecas/vaso-do-sertao";
        var antes = http.getForEntity(caminho + "/historico", Map.class);
        assertThat(antes.getStatusCode().value()).isEqualTo(200);
        assertThat((Map) antes.getBody().get("dados")).containsEntry("historia", narrativa);
        assertThat(requisitar(HttpMethod.DELETE, caminho, jwt, null, Map.class).getStatusCode().value()).isEqualTo(204);
        assertThat(http.getForEntity(caminho, Map.class).getStatusCode().value()).isEqualTo(404);
        var depois = http.getForEntity(caminho + "/historico", Map.class);
        assertThat(depois.getStatusCode().value()).isEqualTo(200);
        assertThat(depois.getBody()).containsEntry("erro", null).doesNotContainKey("paginacao");
        assertThat((Map) depois.getBody().get("dados")).containsEntry("historia", narrativa);

        persistir(peca("rascunho", "Rascunho", "100", "0", 2, "rascunho"));
        for (String slug : List.of("inexistente", "rascunho")) {
            var ausente = http.getForEntity("/api/v1/pecas/" + slug + "/historico", Map.class);
            assertThat(ausente.getStatusCode().value()).isEqualTo(404);
            assertThat(ausente.getBody()).containsEntry("dados", null);
            assertThat((Map) ausente.getBody().get("erro")).containsEntry("codigo", "RECURSO_NAO_ENCONTRADO");
        }
    }

    @Test
    void disponibilidadeValidaPrazoPreservaAlteracaoParcialELimpaPrazoAoSairDeEncomenda() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("publicada", 100), Map.class)
                .getStatusCode().value()).isEqualTo(201);
        org.mockito.Mockito.clearInvocations(artesaos);
        String caminho = "/api/v1/pecas/vaso-do-sertao";
        for (var alteracao : List.of(Map.of("disponibilidade", "encomenda"),
                Map.of("disponibilidade", "encomenda", "prazoProducaoDias", 0),
                Map.of("disponibilidade", "encomenda", "prazoProducaoDias", 121),
                Map.of("disponibilidade", "encomenda", "prazoProducaoDias", 1.5))) {
            var invalida = requisitar(HttpMethod.PATCH, caminho, jwt, alteracao, Map.class);
            assertThat(invalida.getStatusCode().value()).isEqualTo(400);
            assertThat((Map) invalida.getBody().get("erro")).containsEntry("campo", "prazoProducaoDias");
        }
        var encomenda = requisitar(HttpMethod.PATCH, caminho, jwt,
                Map.of("disponibilidade", "encomenda", "prazoProducaoDias", 1), Map.class);
        assertThat(encomenda.getStatusCode().value()).isEqualTo(200);
        assertThat((Map) encomenda.getBody().get("dados")).containsEntry("disponibilidade", "encomenda")
                .containsEntry("prazoProducaoDias", 1);
        var prazo = requisitar(HttpMethod.PATCH, caminho, jwt, Map.of("prazoProducaoDias", 120), Map.class);
        assertThat(prazo.getStatusCode().value()).isEqualTo(200);
        assertThat((Map) prazo.getBody().get("dados")).containsEntry("disponibilidade", "encomenda")
                .containsEntry("prazoProducaoDias", 120);
        var unica = requisitar(HttpMethod.PATCH, caminho, jwt, Map.of("disponibilidade", "unica"), Map.class);
        assertThat(unica.getStatusCode().value()).isEqualTo(200);
        assertThat((Map) unica.getBody().get("dados")).containsEntry("disponibilidade", "unica")
                .containsEntry("prazoProducaoDias", null);
        assertThat((Map) http.getForEntity(caminho, Map.class).getBody().get("dados"))
                .containsEntry("disponibilidade", "unica");
        assertThat(requisitar(HttpMethod.PATCH, caminho, token(UUID.randomUUID(), "artesao"),
                Map.of("disponibilidade", "disponivel"), Map.class).getStatusCode().value()).isEqualTo(404);
        for (String papel : List.of("comprador", "admin", "administrador")) {
            assertThat(requisitar(HttpMethod.PATCH, caminho, token(dono, papel),
                    Map.of("disponibilidade", "disponivel"), Map.class).getStatusCode().value()).isEqualTo(403);
        }
        org.mockito.Mockito.verifyNoInteractions(artesaos);
    }

    @Test
    void painelMostraInativasSoAoDonoSemPermitirReativacaoPorPatch() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        assertThat(requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, dados("publicada", 100), Map.class)
                .getStatusCode().value()).isEqualTo(201);
        String painel = "/api/v1/artesaos/artesao-" + dono + "/pecas/painel";
        var lista = requisitar(HttpMethod.GET, painel, jwt, null, Map.class);
        assertThat(((List<Map>) lista.getBody().get("dados")).getFirst())
                .containsEntry("estadoVenda", "disponivel").doesNotContainKey("vendidaEmPedido");
        String caminho = "/api/v1/pecas/vaso-do-sertao";
        assertThat(requisitar(HttpMethod.DELETE, caminho, jwt, null, Map.class).getStatusCode().value()).isEqualTo(204);
        var propria = requisitar(HttpMethod.GET, painel, jwt, null, Map.class);
        Map inativa = ((List<Map>) propria.getBody().get("dados")).getFirst();
        assertThat(inativa).containsEntry("estadoVenda", "inativada");
        assertThat(inativa.get("inativadoEm")).isNotNull();
        var publicaMesmoAutenticada = requisitar(HttpMethod.GET,
                "/api/v1/artesaos/artesao-" + dono + "/pecas", jwt, null, Map.class);
        assertThat((List<Map>) publicaMesmoAutenticada.getBody().get("dados"))
                .noneSatisfy(peca -> assertThat(peca.get("inativadoEm")).isNotNull());
        for (String outroToken : List.of(token(UUID.randomUUID(), "artesao"), token(dono, "comprador"),
                token(dono, "admin"))) {
            assertThat(requisitar(HttpMethod.GET, painel, outroToken, null, Map.class)
                    .getStatusCode().value()).isEqualTo(403);
        }
        assertThat(http.getForEntity(painel, Map.class).getStatusCode().value()).isEqualTo(401);
        assertThat(requisitar(HttpMethod.HEAD, painel, null, null, Map.class).getStatusCode().value()).isEqualTo(401);
        assertThat(requisitar(HttpMethod.HEAD, painel, token(UUID.randomUUID(), "artesao"), null, Map.class)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(requisitar(HttpMethod.HEAD, painel, jwt, null, Map.class).getStatusCode().value()).isEqualTo(200);
        assertThat(requisitar(HttpMethod.PATCH, caminho, jwt, Map.of("disponibilidade", "disponivel"), Map.class)
                .getStatusCode().value()).isEqualTo(409);
    }

    /** TC05 (TS02): comprador e outro artesão não leem o painel de peças do artesão. */
    @Test
    void compradorEOutroArtesaoNaoLeemPainelDePecas() {
        UUID dono = UUID.randomUUID();
        UUID outro = UUID.randomUUID();
        String slugArtesao = "artesao-" + dono;
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(slugArtesao);
        String slugPeca = criarPeca(dono, "publicada", Map.of("nome", "Vaso do dono"));
        String painel = "/api/v1/artesaos/" + slugArtesao + "/pecas/painel";

        assertThat(requisitar(HttpMethod.GET, painel, token(UUID.randomUUID(), "comprador"), null, Map.class)
                .getStatusCode().value()).isEqualTo(403);
        var deOutroArtesao = requisitar(HttpMethod.GET, painel, token(outro, "artesao"), null, Map.class);
        assertThat(deOutroArtesao.getStatusCode().value()).isIn(403, 404);
        assertThat(String.valueOf(deOutroArtesao.getBody())).doesNotContain(slugPeca);
        assertThat(requisitar(HttpMethod.GET, painel, null, null, Map.class).getStatusCode().value()).isEqualTo(401);
    }

    /** TC22 (TS04): visitante vê só a peça publicada; o dono vê rascunho e publicada. */
    @Test
    void visitanteVeSoPublicadaEDonoVeRascunhoEPublicada() {
        UUID dono = UUID.randomUUID();
        String slugArtesao = "artesao-" + dono;
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(slugArtesao);
        String publicada = criarPeca(dono, "publicada", Map.of("nome", "Vaso publicado"));
        String rascunho = criarPeca(dono, "rascunho", Map.of("nome", "Vaso rascunho"));

        String lista = "/api/v1/artesaos/" + slugArtesao + "/pecas";
        String visitante = http.getForEntity(lista, String.class).getBody();
        assertThat(visitante).contains(publicada).doesNotContain(rascunho);

        String donoVe = requisitar(HttpMethod.GET, lista + "/painel", token(dono, "artesao"), null, Map.class)
                .getBody().toString();
        assertThat(donoVe).contains(publicada, rascunho);
    }

    /** TC37 (TS22): técnica, materiais e história são gravados e aparecem na página da peça. */
    @Test
    void tecnicaMateriaisEHistoriaSaoGravadosEExibidosNaPeca() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var payload = new java.util.HashMap<String, Object>(dados("publicada", 100));
        payload.put("nome", "Peça com história");
        payload.put("tecnica", "Cerâmica & Barro");
        payload.put("historia", List.of("Primeiro parágrafo da história.", "Segundo parágrafo."));
        payload.put("materiais", List.of("Linha de algodão"));
        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), payload, Map.class);
        assertThat(criada.getStatusCode().value()).as(String.valueOf(criada.getBody())).isEqualTo(201);
        String slug = (String) ((Map<?, ?>) criada.getBody().get("dados")).get("slug");

        Map<String, Object> detalhe = (Map<String, Object>) http.getForEntity("/api/v1/pecas/" + slug, Map.class).getBody().get("dados");
        assertThat(detalhe).containsEntry("tecnica", "Cerâmica & Barro")
                .containsEntry("materiais", List.of("Linha de algodão"))
                .containsEntry("historia", List.of("Primeiro parágrafo da história.", "Segundo parágrafo."));
    }

    /** TC39 (TS23): edição de preço aparece no detalhe e na vitrine; preço inválido não altera a peça. */
    @Test
    void edicaoDePrecoAparecenoDetalheEnaVitrine() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        String slug = criarPeca(dono, "publicada", Map.of("nome", "Peça de preço"));

        var editada = requisitar(HttpMethod.PATCH, "/api/v1/pecas/" + slug, jwt, Map.of("preco", 150), Map.class);
        assertThat(editada.getStatusCode().value()).isEqualTo(200);
        assertThat(precoDe("/api/v1/pecas/" + slug, slug)).isEqualByComparingTo("150");
        assertThat(precoNaVitrine(slug)).isEqualByComparingTo("150");

        var invalida = requisitar(HttpMethod.PATCH, "/api/v1/pecas/" + slug, jwt, Map.of("preco", 0), Map.class);
        assertThat(invalida.getStatusCode().value()).isEqualTo(400);
        assertThat(precoDe("/api/v1/pecas/" + slug, slug)).isEqualByComparingTo("150");
    }

    /** TC44 (TS25): peça inativada não é mais acessível pela URL pública. */
    @Test
    void pecaInativaNaoEhPublicaPelaUrl() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        String slug = criarPeca(dono, "publicada", Map.of("nome", "Peça inativada"));
        assertThat(http.getForEntity("/api/v1/pecas/" + slug, Map.class).getStatusCode().value()).isEqualTo(200);

        assertThat(requisitar(HttpMethod.DELETE, "/api/v1/pecas/" + slug, jwt, null, Map.class)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(http.getForEntity("/api/v1/pecas/" + slug, Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(requisitar(HttpMethod.GET, "/api/v1/pecas/" + slug, token(UUID.randomUUID(), "artesao"), null, Map.class)
                .getStatusCode().value()).isEqualTo(404);
    }

    private String criarPeca(UUID dono, String situacao, Map<String, Object> ajustes) {
        var payload = new java.util.HashMap<String, Object>(dados(situacao, 100));
        payload.putAll(ajustes);
        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), payload, Map.class);
        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        return (String) ((Map<?, ?>) criada.getBody().get("dados")).get("slug");
    }

    private java.math.BigDecimal precoDe(String caminho, String slug) {
        var dados = (Map<?, ?>) http.getForEntity(caminho, Map.class).getBody().get("dados");
        return new java.math.BigDecimal(String.valueOf(dados.get("preco")));
    }

    private java.math.BigDecimal precoNaVitrine(String slug) {
        var lista = (List<?>) http.getForEntity("/api/v1/pecas", Map.class).getBody().get("dados");
        var peca = lista.stream().map(Map.class::cast).filter(p -> slug.equals(p.get("slug"))).findFirst().orElseThrow();
        return new java.math.BigDecimal(String.valueOf(peca.get("preco")));
    }

    private List<ResponseEntity<Map>> criarAoMesmoTempo(String jwt, String nomeA, String nomeB) throws Exception {
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> criarAposSinal(inicio, jwt, nomeA));
            var segunda = executor.submit(() -> criarAposSinal(inicio, jwt, nomeB));
            inicio.countDown();
            var respostas = List.of(primeira.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));
            assertThat(respostas).allSatisfy(resposta -> assertThat(resposta.getStatusCode().value()).isEqualTo(201));
            return respostas;
        }
    }

    private ResponseEntity<Map> criarAposSinal(CountDownLatch inicio, String jwt, String nome) throws Exception {
        inicio.await();
        var payload = new java.util.HashMap<String, Object>(dados("publicada", 100));
        payload.put("nome", nome);
        return requisitar(HttpMethod.POST, "/api/v1/pecas", jwt, payload, Map.class);
    }

    private Map<String, Object> dados(String situacao, int preco) {
        return Map.ofEntries(Map.entry("nome", "Vaso do Sertão"), Map.entry("tecnica", "Cerâmica & Barro"),
                Map.entry("territorio", "Caruaru"), Map.entry("categoria", "Casa & Mesa"),
                Map.entry("tipo", "Jarras & Vasos"), Map.entry("historia", List.of("História")),
                Map.entry("preco", preco), Map.entry("disponibilidade", "disponivel"),
                Map.entry("situacao", situacao), Map.entry("fotos", List.of()), Map.entry("artesao", "forjado"));
    }

    private ResponseEntity<Map> requisitar(HttpMethod metodo, String caminho, String jwt, Object corpo, Class<Map> tipo) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) headers.setBearerAuth(jwt);
        return http.exchange(caminho, metodo, new HttpEntity<>(corpo, headers), tipo);
    }

    private void persistir(Peca peca) {
        transacao().executeWithoutResult(status -> {
            entityManager.persist(PecaJpaEntity.fromDomain(peca));
            entityManager.flush();
            entityManager.clear();
        });
    }

    private TransactionTemplate transacao() {
        return new TransactionTemplate(transactionManager);
    }

    private Peca peca(String slug, String nome, String preco, String desconto, int ordem, String situacao) {
        return new Peca(slug, nome, "artesao", "Agreste", "Cerâmica & Barro", "Decoração & Arte",
                "Esculturas & Bonecos", new BigDecimal(preco), new BigDecimal(desconto), "disponivel", null,
                List.of("História."), "/fotos/" + slug + ".svg", new BigDecimal("4.8"), 10, situacao, null, ordem);
    }
}
