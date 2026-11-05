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

    @MockitoBean
    private ResolverArtesaoPort artesaos;

    @BeforeEach
    void limparBanco() {
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
    void ordenaPeloPrecoComDesconto() {
        persistir(peca("preco-cheio", "Preço cheio", "80", "0", 1, "publicada"));
        persistir(peca("promocional", "Preço promocional", "100", "50", 2, "publicada"));

        ResponseEntity<Map> resposta = http.getForEntity("/api/v1/pecas?ordenar=preco-asc", Map.class);

        List<Map> dados = (List<Map>) resposta.getBody().get("dados");
        assertThat(dados.getFirst()).containsEntry("slug", "promocional");
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
        var propria = requisitar(HttpMethod.GET, lista, jwt, null, Map.class);
        assertThat((List<?>) propria.getBody().get("dados")).hasSize(1);
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
    void guardaFotosNoBancoEServeCapaPelaUrlPublica() {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        var dados = new java.util.HashMap<String, Object>(dados("publicada", 100));
        dados.put("fotos", List.of(Map.of("id", "foto-1", "nome", "capa.png",
                "url", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==")));
        var criada = requisitar(HttpMethod.POST, "/api/v1/pecas", token(dono, "artesao"), dados, Map.class);
        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        var peca = (Map) criada.getBody().get("dados");
        var fotos = (List<Map>) peca.get("fotos");
        assertThat(fotos).hasSize(1);
        assertThat(peca).containsEntry("imagem", fotos.getFirst().get("url"));
        var arquivo = http.getForEntity((String) fotos.getFirst().get("url"), byte[].class);
        assertThat(arquivo.getStatusCode().value()).isEqualTo(200);
        assertThat(arquivo.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(arquivo.getBody()).isNotEmpty();
        var edicao = requisitar(HttpMethod.PATCH, "/api/v1/pecas/vaso-do-sertao", token(dono, "artesao"),
                Map.of("fotos", List.of(Map.of("id", "foto-1", "nome", "outra-capa.png",
                        "url", fotos.getFirst().get("url")))), Map.class);
        assertThat(edicao.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map) edicao.getBody().get("dados")).get("imagem")).isEqualTo(fotos.getFirst().get("url"));
        assertThat(http.getForEntity((String) fotos.getFirst().get("url"), byte[].class).getStatusCode().value())
                .isEqualTo(200);
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
