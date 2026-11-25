package br.com.balaio.catalog.peca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

@EnabledIfEnvironmentVariable(named = "CATALOG_POSTGRES_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(PecasPostgresConcurrencyTest.Postgres.class)
class BuscaPecasPostgresTest extends CatalogJwtTestSupport {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;
    @MockitoBean ConsultarArtesaosPort artesaos;

    @BeforeEach
    void prepararCatalogo() {
        when(artesaos.listar()).thenReturn(List.of(
                new ArtesaoPublico("busca-pg-artesao", "Severina Ramos", "Ateliê Barro Vivo")));
        jdbc.update("DELETE FROM pecas WHERE slug LIKE 'busca-pg-%'");
        int ordem = jdbc.queryForObject("SELECT COALESCE(MAX(ordem_catalogo), 0) + 1 FROM pecas", Integer.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.persist(PecaJpaEntity.fromDomain(peca("vaso", "Vaso Bojudo", "Tracunhaém",
                    "Cerâmica & Barro", "Jarras & Vasos", "disponivel", "200", "10", ordem)));
            entityManager.persist(PecaJpaEntity.fromDomain(peca("toalha", "Toalha Florida", "Cariri",
                    "Bordado & Renda", "Toalhas & Rendas", "encomenda", "300", "0", ordem + 1)));
        });
    }

    @Test
    void buscaPorArtesaoAtelieEVariasPalavrasCombinandoFiltrosNoPostgres() {
        assertThat(slugs(Map.of("q", "SEVERINA"))).containsExactly("busca-pg-vaso", "busca-pg-toalha");
        assertThat(slugs(Map.of("q", "atelie barro vivo"))).containsExactly("busca-pg-vaso", "busca-pg-toalha");
        assertThat(slugs(Map.of("q", "severina Tracunhaém"))).containsExactly("busca-pg-vaso");
        assertThat(slugs(Map.of("q", "severina toalhas"))).containsExactly("busca-pg-toalha");
        assertThat(slugs(Map.of("q", "severina", "tecnica", "Bordado & Renda"))).containsExactly("busca-pg-toalha");
        assertThat(slugs(Map.of("q", "severina", "desconto", true))).containsExactly("busca-pg-vaso");
        assertThat(slugs(Map.of("q", "severina", "ordenar", "preco-desc", "tamanho", 1, "pagina", 2)))
                .containsExactly("busca-pg-vaso");
    }

    private List<String> slugs(Map<String, Object> parametros) {
        var uri = UriComponentsBuilder.fromPath("/api/v1/pecas");
        parametros.forEach(uri::queryParam);
        var resposta = http.getForEntity(uri.build().encode().toUri(), Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        return ((List<Map>) resposta.getBody().get("dados")).stream().map(peca -> (String) peca.get("slug")).toList();
    }

    private Peca peca(String sufixo, String nome, String territorio, String tecnica, String tipo,
            String disponibilidade, String preco, String desconto, int ordem) {
        String slug = "busca-pg-" + sufixo;
        return new Peca(slug, nome, "busca-pg-artesao", territorio, tecnica, "Casa & Mesa", tipo,
                new BigDecimal(preco), new BigDecimal(desconto), disponibilidade,
                "encomenda".equals(disponibilidade) ? 15 : null, List.of("Feita à mão."), "/fotos/" + slug + ".svg",
                new BigDecimal("4.8"), 10, "publicada", null, ordem);
    }
}
