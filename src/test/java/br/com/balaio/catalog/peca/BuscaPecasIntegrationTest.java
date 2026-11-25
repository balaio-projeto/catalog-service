package br.com.balaio.catalog.peca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.balaio.catalog.CatalogJwtTestSupport;
import br.com.balaio.catalog.peca.application.ArtesaoPublico;
import br.com.balaio.catalog.peca.application.port.out.ConsultarArtesaosPort;
import br.com.balaio.catalog.peca.domain.Peca;
import br.com.balaio.catalog.peca.persistence.PecaJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class BuscaPecasIntegrationTest extends CatalogJwtTestSupport {
    private static final List<ArtesaoPublico> ARTESAOS = List.of(
            new ArtesaoPublico("artesao-1", "Severina Ramos", "Ateliê Barro Vivo"),
            new ArtesaoPublico("artesao-2", "João Vaqueiro", "Couraria do Sertão"));

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;
    @MockitoBean ConsultarArtesaosPort artesaos;

    @BeforeEach
    void prepararCatalogo() {
        when(artesaos.listar()).thenReturn(ARTESAOS);
        jdbc.update("DELETE FROM limpeza_fotos_storage");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from PecaJpaEntity").executeUpdate();
            entityManager.persist(PecaJpaEntity.fromDomain(peca("vaso-severina", "Vaso Bojudo", "artesao-1",
                    "Tracunhaém", "Cerâmica & Barro", "Jarras & Vasos", "disponivel", "200", "10", 1,
                    "Queimado em forno a lenha.")));
            entityManager.persist(PecaJpaEntity.fromDomain(peca("toalha-severina", "Toalha Florida", "artesao-1",
                    "Cariri", "Bordado & Renda", "Toalhas & Rendas", "encomenda", "300", "0", 2,
                    "Ponto aranha nas pontas.")));
            entityManager.persist(PecaJpaEntity.fromDomain(peca("gibao-joao", "Gibão de Couro", "artesao-2",
                    "Pajeú", "Couro Autoral", "Couro: Bolsas, Cintos & Gibões", "disponivel", "500", "0", 3,
                    "Armadura do vaqueiro.")));
        });
    }

    @Test
    void encontraPecasPeloNomeDoArtesaoEDoAtelieSemAcentoNemMaiusculas() {
        assertThat(slugs(Map.of("q", "Severina"))).containsExactly("vaso-severina", "toalha-severina");
        assertThat(slugs(Map.of("q", "ATELIE BARRO VIVO"))).containsExactly("vaso-severina", "toalha-severina");
        assertThat(slugs(Map.of("q", "joão"))).containsExactly("gibao-joao");
        assertThat(slugs(Map.of("q", "couraria sertao"))).containsExactly("gibao-joao");
    }

    @Test
    void exigeQueTodasAsPalavrasCasemEmAlgumCampo() {
        assertThat(slugs(Map.of("q", "severina toalha"))).containsExactly("toalha-severina");
        assertThat(slugs(Map.of("q", "toalha de renda do cariri"))).containsExactly("toalha-severina");
        assertThat(slugs(Map.of("q", "vaso de ceramica"))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "severina inexistente"))).isEmpty();
    }

    @Test
    void encontraPorTecnicaTerritorioTipoHistoriaEPlural() {
        assertThat(slugs(Map.of("q", "ceramica"))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "Tracunhaém"))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "tracunhaem"))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "jarras"))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "vasos"))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "gibões"))).containsExactly("gibao-joao");
        assertThat(slugs(Map.of("q", "forno lenha"))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "pajeu"))).containsExactly("gibao-joao");
    }

    @Test
    void combinaTermoComFiltrosOrdenacaoEPaginacao() {
        assertThat(slugs(Map.of("q", "severina", "tecnica", "Bordado & Renda"))).containsExactly("toalha-severina");
        assertThat(slugs(Map.of("q", "severina", "territorio", "Tracunhaém"))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "severina", "tipo", "Toalhas & Rendas"))).containsExactly("toalha-severina");
        assertThat(slugs(Map.of("q", "severina", "desconto", true))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "severina", "disponibilidade", "encomenda"))).containsExactly("toalha-severina");
        assertThat(slugs(Map.of("q", "severina", "precoMax", 200))).containsExactly("vaso-severina");
        assertThat(slugs(Map.of("q", "severina", "ordenar", "preco-desc")))
                .containsExactly("toalha-severina", "vaso-severina");

        var segunda = consultar(Map.of("q", "severina", "ordenar", "preco-desc", "tamanho", 1, "pagina", 2));
        assertThat(slugsDaResposta(segunda)).containsExactly("vaso-severina");
        assertThat((Map) segunda.get("paginacao")).containsEntry("pagina", 2).containsEntry("total", 2)
                .containsEntry("totalPaginas", 2);
    }

    @Test
    void semPerfilDisponivelContinuaBuscandoNosCamposDaPeca() {
        when(artesaos.listar()).thenReturn(List.of());

        assertThat(slugs(Map.of("q", "Severina"))).isEmpty();
        assertThat(slugs(Map.of("q", "ceramica tracunhaem"))).containsExactly("vaso-severina");
    }

    private List<String> slugs(Map<String, Object> parametros) {
        return slugsDaResposta(consultar(parametros));
    }

    private List<String> slugsDaResposta(Map resposta) {
        return ((List<Map>) resposta.get("dados")).stream().map(peca -> (String) peca.get("slug")).toList();
    }

    private Map consultar(Map<String, Object> parametros) {
        var valores = new HashMap<>(parametros);
        var uri = UriComponentsBuilder.fromPath("/api/v1/pecas");
        valores.forEach(uri::queryParam);
        URI endereco = uri.build().encode().toUri();
        var resposta = http.getForEntity(endereco, Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).containsEntry("erro", null);
        return resposta.getBody();
    }

    private static Peca peca(String slug, String nome, String artesao, String territorio, String tecnica,
            String tipo, String disponibilidade, String preco, String desconto, int ordem, String historia) {
        return new Peca(slug, nome, artesao, territorio, tecnica, "Casa & Mesa", tipo, new BigDecimal(preco),
                new BigDecimal(desconto), disponibilidade, "encomenda".equals(disponibilidade) ? 15 : null,
                List.of(historia), "/fotos/" + slug + ".svg", new BigDecimal("4.8"), 10, "publicada", null, ordem);
    }
}
