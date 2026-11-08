package br.com.balaio.catalog.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.balaio.catalog.CatalogJwtTestSupport;

@EnabledIfEnvironmentVariable(named = "CATALOG_POSTGRES_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "balaio.internal.token=0123456789abcdef0123456789abcdef")
@AutoConfigureTestRestTemplate
@Import(ReservasPostgresTest.Postgres.class)
class ReservasPostgresTest extends CatalogJwtTestSupport {
    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean @ServiceConnection PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservaService reservas;

    @Test
    void reservaConcorrenteLiberacaoAntigaEExpiracao() throws Exception {
        String slug = jdbc.query("SELECT slug FROM pecas WHERE disponibilidade = 'unica' AND situacao = 'publicada' AND inativado_em IS NULL LIMIT 1",
                (rs, row) -> rs.getString(1)).getFirst();
        UUID dono = UUID.randomUUID();
        jdbc.update("UPDATE pecas SET proprietario_id = ? WHERE slug = ?", dono, slug);
        var cotacaoHeaders = new HttpHeaders();
        cotacaoHeaders.add("X-Balaio-Service-Token", "0123456789abcdef0123456789abcdef");
        var cotacao = http.exchange("/internal/v1/pecas/" + slug + "/cotacao", HttpMethod.GET,
                new HttpEntity<>(cotacaoHeaders), Map.class);
        assertThat(cotacao.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map) cotacao.getBody().get("dados")).get("proprietarioId")).isEqualTo(dono.toString());
        var semToken = http.postForEntity("/internal/v1/reservas",
                Map.of("pedidoId", "intruso", "slug", slug, "chave", "intruso"), Map.class);
        assertThat(semToken.getStatusCode().value()).isEqualTo(403);
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { inicio.await(); return criar("pedido-a", slug); });
            var b = executor.submit(() -> { inicio.await(); return criar("pedido-b", slug); });
            inicio.countDown();
            var primeira = a.get(30, TimeUnit.SECONDS);
            var segunda = b.get(30, TimeUnit.SECONDS);
            assertThat(new int[]{primeira.status(), segunda.status()}).containsExactlyInAnyOrder(201, 409);
            var vencedora = primeira.status() == 201 ? primeira : segunda;
            String pedido = primeira.status() == 201 ? "pedido-a" : "pedido-b";
            UUID id = UUID.fromString((String) vencedora.dados().get("id"));
            assertThat(criar(pedido, slug).dados().get("id")).isEqualTo(id.toString());
            String outroSlug = jdbc.query("SELECT slug FROM pecas WHERE slug <> ? AND disponibilidade = 'unica' AND situacao = 'publicada' AND inativado_em IS NULL LIMIT 1",
                    (rs, row) -> rs.getString(1), slug).getFirst();
            var mesmaChave = trocar("/internal/v1/reservas", Map.of("pedidoId", "pedido-intruso",
                    "slug", outroSlug, "chave", "reservar:" + pedido + ":" + slug));
            assertThat(mesmaChave.status()).isEqualTo(409);
            assertThat(mesmaChave.erro().get("codigo")).isEqualTo("RESERVA_CONFLITANTE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reservas_peca WHERE pedido_id = 'pedido-intruso'", Integer.class)).isZero();
            assertThat(liberar(id, "outro-pedido").status()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT inativado_em IS NOT NULL FROM pecas WHERE slug = ?", Boolean.class, slug)).isTrue();
            var editarHeaders = new HttpHeaders();
            editarHeaders.setBearerAuth(token(dono, "artesao"));
            var editar = http.exchange("/api/v1/pecas/" + slug, HttpMethod.PATCH,
                    new HttpEntity<>(Map.of("preco", 1), editarHeaders), Map.class);
            assertThat(editar.getStatusCode().value()).isEqualTo(409);

            assertThat(liberar(id, pedido).status()).isEqualTo(200);
            assertThat(liberar(id, pedido).status()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT inativado_em IS NULL FROM pecas WHERE slug = ?", Boolean.class, slug)).isTrue();
            jdbc.update("UPDATE pecas SET preco = preco + 1 WHERE slug = ?", slug);
            assertThat(liberar(id, pedido).dados().get("preco")).isEqualTo(vencedora.dados().get("preco"));

            var posterior = criar("pedido-posterior", slug);
            assertThat(posterior.status()).isEqualTo(201);
            assertThat(liberar(id, pedido).status()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT inativado_em IS NOT NULL FROM pecas WHERE slug = ?", Boolean.class, slug)).isTrue();

            UUID novoId = UUID.fromString((String) posterior.dados().get("id"));
            assertThat(confirmar(novoId, "pedido-posterior").dados().get("estado")).isEqualTo("CONSUMIDA");
            assertThat(confirmar(novoId, "pedido-posterior").dados().get("estado")).isEqualTo("CONSUMIDA");
            jdbc.update("UPDATE reservas_peca SET expira_em = now() - interval '1 second' WHERE id = ?", novoId);
            reservas.expirar();
            assertThat(jdbc.queryForObject("SELECT estado FROM reservas_peca WHERE id = ?", String.class, novoId)).isEqualTo("CONSUMIDA");
            assertThat(jdbc.queryForObject("SELECT inativado_em IS NOT NULL FROM pecas WHERE slug = ?", Boolean.class, slug)).isTrue();
            assertThat(liberar(novoId, "pedido-posterior").status()).isEqualTo(200);
            var expiravel = criar("pedido-expiravel", slug);
            assertThat(expiravel.status()).isEqualTo(201);
            UUID expiravelId = UUID.fromString((String) expiravel.dados().get("id"));
            jdbc.update("UPDATE reservas_peca SET expira_em = now() - interval '1 second' WHERE id = ?", expiravelId);
            reservas.expirar();
            assertThat(jdbc.queryForObject("SELECT estado FROM reservas_peca WHERE id = ?", String.class, expiravelId)).isEqualTo("EXPIRADA");
            assertThat(jdbc.queryForObject("SELECT inativado_em IS NULL FROM pecas WHERE slug = ?", Boolean.class, slug)).isTrue();
            assertThat(criar("pedido-apos-expiracao", slug).status()).isEqualTo(201);
            assertThat(confirmar(expiravelId, "pedido-expiravel").status()).isEqualTo(409);
            assertThat(liberar(expiravelId, "pedido-expiravel").dados().get("estado")).isEqualTo("EXPIRADA");
            assertThat(jdbc.queryForObject("SELECT inativado_em IS NOT NULL FROM pecas WHERE slug = ?", Boolean.class, slug)).isTrue();
        }
    }

    private Resultado criar(String pedido, String slug) {
        return trocar("/internal/v1/reservas", Map.of("pedidoId", pedido, "slug", slug, "chave", "reservar:" + pedido + ":" + slug));
    }

    private Resultado liberar(UUID id, String pedido) {
        return trocar("/internal/v1/reservas/" + id + "/liberar", Map.of("pedidoId", pedido, "chave", "liberar:" + id));
    }

    private Resultado confirmar(UUID id, String pedido) {
        return trocar("/internal/v1/reservas/" + id + "/confirmar", Map.of("pedidoId", pedido, "chave", "confirmar:" + id));
    }

    private Resultado trocar(String caminho, Map<String, String> corpo) {
        var headers = new HttpHeaders();
        headers.add("X-Balaio-Service-Token", "0123456789abcdef0123456789abcdef");
        var resposta = http.exchange(caminho, HttpMethod.POST, new HttpEntity<>(corpo, headers), Map.class);
        Map dados = resposta.getBody() == null ? null : (Map) resposta.getBody().get("dados");
        Map erro = resposta.getBody() == null ? null : (Map) resposta.getBody().get("erro");
        return new Resultado(resposta.getStatusCode().value(), dados, erro);
    }

    private record Resultado(int status, Map dados, Map erro) { }
}
