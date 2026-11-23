package br.com.balaio.catalog.peca;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.balaio.catalog.CatalogJwtTestSupport;

@EnabledIfEnvironmentVariable(named = "CATALOG_POSTGRES_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(PecasPostgresConcurrencyTest.Postgres.class)
class FiltrosPaginacaoPostgresTest extends CatalogJwtTestSupport {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limparCenarios() {
        jdbc.update("DELETE FROM pecas WHERE slug LIKE 'pi180-%'");
    }

    @Test
    void combinaTodosOsFiltrosPorIntersecaoESomenteComPecasPublicas() {
        inserir("alvo", Map.of());
        inserir("outro-texto", Map.of("texto_busca", "sem correspondencia"));
        inserir("outra-tecnica", Map.of("tecnica", "Madeira"));
        inserir("outro-territorio", Map.of("territorio", "Cariri"));
        inserir("outra-categoria", Map.of("categoria", "Vestuário"));
        inserir("outro-tipo", Map.of("tipo", "Vasos"));
        inserir("outra-disponibilidade", Map.of("disponibilidade", "encomenda"));
        inserir("sem-desconto", Map.of("desconto", 0));
        inserir("rascunho", Map.of("situacao", "rascunho"));
        inserir("inativa", Map.of("inativado_em", OffsetDateTime.now()));
        var filtros = Map.<String, Object>of("tecnica", "Cerâmica & Barro", "territorio", "Agreste",
                "categoria", "Decoração & Arte", "tipo", "Esculturas & Bonecos",
                "disponibilidade", "unica", "desconto", true);
        var resposta = consultar(filtros);
        assertThat(slugs(resposta)).containsExactly("pi180-alvo");
        assertThat((Map) resposta.get("paginacao")).containsEntry("total", 1);
        for (String campo : filtros.keySet()) {
            var semUmFiltro = new HashMap<>(filtros);
            semUmFiltro.remove(campo);
            assertThat(slugs(consultar(semUmFiltro))).hasSize(2).contains("pi180-alvo");
        }
        var semExigirDesconto = new HashMap<>(filtros);
        semExigirDesconto.put("desconto", false);
        assertThat(slugs(consultar(semExigirDesconto))).containsExactly("pi180-alvo", "pi180-sem-desconto");
        assertThat(slugs(consultar(Map.of("tecnica", "nao-existe")))).isEmpty();
        assertThat(slugs(consultar(Map.of("tecnica", "   ")))).hasSize(7);
    }

    @Test
    void ordenaPorPrecoFinalAvaliacaoERecentesComDesempateEstavel() {
        inserir("a", Map.of("preco", 300, "desconto", 50, "avaliacao", 4.5));
        inserir("b", Map.of("preco", 150, "desconto", 0, "avaliacao", 5));
        var semAvaliacao = new HashMap<String, Object>();
        semAvaliacao.put("preco", 100);
        semAvaliacao.put("desconto", 0);
        semAvaliacao.put("avaliacao", null);
        inserir("c", semAvaliacao);
        inserir("d", Map.of("preco", 500, "desconto", 0, "avaliacao", 5));
        inserir("e", Map.of("preco", 200, "desconto", 0, "avaliacao", 0));
        assertThat(slugs(consultar(Map.of("ordenar", "preco-asc"))))
                .containsExactly("pi180-c", "pi180-a", "pi180-b", "pi180-e", "pi180-d");
        assertThat(slugs(consultar(Map.of("ordenar", "preco-desc"))))
                .containsExactly("pi180-d", "pi180-e", "pi180-a", "pi180-b", "pi180-c");
        assertThat(slugs(consultar(Map.of("ordenar", "avaliacao"))))
                .containsExactly("pi180-b", "pi180-d", "pi180-a", "pi180-e", "pi180-c");
        assertThat(slugs(consultar(Map.of("ordenar", "recentes"))))
                .containsExactly("pi180-e", "pi180-d", "pi180-c", "pi180-b", "pi180-a");
        assertThat(slugs(consultar(Map.of("ordenar", "desconhecida"))))
                .containsExactlyElementsOf(slugs(consultar(Map.of())));
        assertThat(slugs(consultar(Map.of("ordenar", "preco-asc", "tamanho", 1, "pagina", 2))))
                .containsExactly("pi180-a");
        assertThat(slugs(consultar(Map.of("ordenar", "preco-asc", "tamanho", 1, "pagina", 3))))
                .containsExactly("pi180-b");
    }

    @Test
    void faixaDePrecoFiltraPeloPrecoFinalCombinandoComFiltrosEPaginacao() {
        inserir("abaixo", Map.of("preco", 49.99, "desconto", 0));
        inserir("limite-inferior", Map.of("preco", 100, "desconto", 50));
        inserir("meio", Map.of("preco", 120, "desconto", 0));
        inserir("limite-superior", Map.of("preco", 200, "desconto", 0));
        inserir("acima-pelo-desconto", Map.of("preco", 250, "desconto", 10));
        inserir("meio-encomenda", Map.of("preco", 150, "desconto", 0, "disponibilidade", "encomenda"));
        inserir("meio-inativa", Map.of("preco", 150, "desconto", 0, "inativado_em", OffsetDateTime.now()));
        var faixa = Map.<String, Object>of("precoMin", 50, "precoMax", 200, "ordenar", "preco-asc");
        assertThat(slugs(consultar(faixa)))
                .containsExactly("pi180-limite-inferior", "pi180-meio", "pi180-meio-encomenda", "pi180-limite-superior");
        var combinada = new HashMap<>(faixa);
        combinada.put("disponibilidade", "unica");
        combinada.put("tamanho", 2);
        combinada.put("pagina", 2);
        var pagina = consultar(combinada);
        assertThat(slugs(pagina)).containsExactly("pi180-limite-superior");
        assertThat((Map) pagina.get("paginacao")).containsEntry("total", 3).containsEntry("totalPaginas", 2);
        assertThat(slugs(consultar(Map.of("precoMin", "225.01")))).isEmpty();
        assertThat(slugs(consultar(Map.of("precoMin", "200.01", "precoMax", "224.99")))).isEmpty();
        assertThat(slugs(consultar(Map.of("precoMin", "225")))).containsExactly("pi180-acima-pelo-desconto");
        assertThat(slugs(consultar(Map.of("precoMax", "49.99")))).containsExactly("pi180-abaixo");
        var invertida = http.getForEntity("/api/v1/pecas?precoMin=300&precoMax=100", Map.class);
        assertThat(invertida.getStatusCode().value()).isEqualTo(400);
        assertThat((Map) invertida.getBody().get("erro")).containsEntry("campo", "precoMax");
    }

    @Test
    void precoFinalComDescontoQuebradoEhArredondadoComoNaReserva() {
        inserir("quebrado", Map.of("preco", new BigDecimal("99.99"), "desconto", new BigDecimal("15")));
        inserir("meio-centavo", Map.of("preco", new BigDecimal("10.10"), "desconto", new BigDecimal("12.5")));

        assertThat(slugs(consultar(Map.of("precoMin", "84.99", "precoMax", "84.99")))).containsExactly("pi180-quebrado");
        assertThat(slugs(consultar(Map.of("precoMin", "8.84", "precoMax", "8.84")))).containsExactly("pi180-meio-centavo");
    }

    @Test
    void paginaPreservaMetadadosEAjustaLimitesInclusiveSemResultado() {
        for (String nome : List.of("a", "b", "c")) inserir(nome, Map.of());
        var primeira = consultar(Map.of("pagina", 0, "tamanho", 2));
        assertThat(slugs(primeira)).containsExactly("pi180-a", "pi180-b");
        assertThat((Map) primeira.get("paginacao")).containsEntry("pagina", 1).containsEntry("tamanho", 2)
                .containsEntry("total", 3).containsEntry("totalPaginas", 2);
        var ultima = consultar(Map.of("pagina", Integer.MAX_VALUE, "tamanho", 2));
        assertThat(slugs(ultima)).containsExactly("pi180-c");
        assertThat((Map) ultima.get("paginacao")).containsEntry("pagina", 2);
        assertThat((Map) consultar(Map.of("tamanho", -1)).get("paginacao"))
                .containsEntry("tamanho", 1).containsEntry("totalPaginas", 3);
        assertThat((Map) consultar(Map.of("tamanho", 0)).get("paginacao"))
                .containsEntry("tamanho", 3).containsEntry("totalPaginas", 1);
        var vazia = consultar(Map.of("q", "nenhuma-correspondencia", "pagina", 99, "tamanho", 10));
        assertThat(slugs(vazia)).isEmpty();
        assertThat((Map) vazia.get("paginacao")).containsEntry("pagina", 1).containsEntry("tamanho", 10)
                .containsEntry("total", 0).containsEntry("totalPaginas", 1);
    }

    @Test
    void limitaTamanhoACemSemCarregarTodosOsResultadosNaResposta() {
        for (int i = 0; i < 105; i++) inserir("item-" + i, Map.of());
        for (var parametros : List.of(Map.<String, Object>of(), Map.<String, Object>of("tamanho", Integer.MAX_VALUE))) {
            var resposta = consultar(parametros);
            assertThat(slugs(resposta)).hasSize(100);
            assertThat((Map) resposta.get("paginacao")).containsEntry("tamanho", 100)
                    .containsEntry("total", 105).containsEntry("totalPaginas", 2);
        }
        assertThat(slugs(consultar(Map.of("pagina", 2, "tamanho", 100)))).hasSize(5);
    }

    @Test
    void parametrosMalformadosRetornamErroDeContrato() {
        for (String query : List.of("pagina=abc", "tamanho=1.5", "desconto=talvez")) {
            var resposta = http.getForEntity("/api/v1/pecas?" + query, Map.class);
            assertThat(resposta.getStatusCode().value()).isEqualTo(400);
            assertThat(resposta.getBody()).containsEntry("dados", null);
            assertThat((Map) resposta.getBody().get("erro")).containsEntry("codigo", "REQUISICAO_INVALIDA");
        }
    }

    private Map consultar(Map<String, Object> parametros) {
        var valores = new HashMap<String, Object>();
        valores.put("q", "pi180");
        valores.putAll(parametros);
        var uri = UriComponentsBuilder.fromPath("/api/v1/pecas");
        valores.forEach(uri::queryParam);
        var resposta = http.getForEntity(uri.build().encode().toUri(), Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).containsEntry("erro", null).containsKey("paginacao");
        return resposta.getBody();
    }

    private List<String> slugs(Map resposta) {
        return ((List<Map>) resposta.get("dados")).stream().map(peca -> (String) peca.get("slug")).toList();
    }

    private void inserir(String nome, Map<String, Object> alteracoes) {
        var valores = new HashMap<String, Object>(Map.of("tecnica", "Cerâmica & Barro", "territorio", "Agreste",
                "categoria", "Decoração & Arte", "tipo", "Esculturas & Bonecos", "disponibilidade", "unica",
                "desconto", 10, "preco", 100, "avaliacao", new BigDecimal("4.8"),
                "situacao", "publicada", "texto_busca", "pi180"));
        valores.putAll(alteracoes);
        jdbc.update("""
                INSERT INTO pecas (slug, nome, artesao, territorio, tecnica, categoria, tipo, preco, desconto,
                    disponibilidade, historia, imagem, avaliacao, situacao, inativado_em, ordem_catalogo, texto_busca)
                VALUES (?, ?, 'artesao', ?, ?, ?, ?, ?, ?, ?, ARRAY['História'], '/foto.webp', ?, ?, ?,
                    (SELECT max(ordem_catalogo) + 1 FROM pecas), ?)
                """, "pi180-" + nome, nome, valores.get("territorio"), valores.get("tecnica"),
                valores.get("categoria"), valores.get("tipo"), valores.get("preco"), valores.get("desconto"),
                valores.get("disponibilidade"), valores.get("avaliacao"), valores.get("situacao"),
                valores.get("inativado_em"), valores.get("texto_busca"));
    }
}
