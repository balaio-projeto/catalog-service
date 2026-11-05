package br.com.balaio.catalog.peca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.balaio.catalog.CatalogJwtTestSupport;
import br.com.balaio.catalog.peca.application.port.out.ResolverArtesaoPort;

@EnabledIfEnvironmentVariable(named = "CATALOG_POSTGRES_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(PecasPostgresConcurrencyTest.Postgres.class)
class PecasPostgresConcurrencyTest extends CatalogJwtTestSupport {
    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired TestRestTemplate http;
    @MockitoBean ResolverArtesaoPort artesaos;

    @Test
    void migrationEConcorrenciaComNomesIguaisEDiferentes() throws Exception {
        UUID dono = UUID.randomUUID();
        when(artesaos.resolver(org.mockito.ArgumentMatchers.eq(dono), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("artesao-" + dono);
        String jwt = token(dono, "artesao");
        var iguais = criarAoMesmoTempo(jwt, "Barro concorrente", "Barro concorrente");
        assertThat(iguais).containsExactlyInAnyOrder("barro-concorrente", "barro-concorrente-2");
        var diferentes = criarAoMesmoTempo(jwt, "Barro azul", "Barro vermelho");
        assertThat(diferentes).containsExactlyInAnyOrder("barro-azul", "barro-vermelho");

        var lista = http.getForEntity("/api/v1/pecas?tamanho=100", Map.class);
        assertThat(lista.getStatusCode().value()).isEqualTo(200);
        var dados = (List<Map>) lista.getBody().get("dados");
        assertThat(dados).extracting(peca -> peca.get("slug"))
                .containsAll(iguais).containsAll(diferentes);
    }

    private List<String> criarAoMesmoTempo(String jwt, String nomeA, String nomeB) throws Exception {
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> criarAposSinal(inicio, jwt, nomeA));
            var segunda = executor.submit(() -> criarAposSinal(inicio, jwt, nomeB));
            inicio.countDown();
            return List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS));
        }
    }

    private String criarAposSinal(CountDownLatch inicio, String jwt, String nome) throws Exception {
        inicio.await();
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", nome);
        corpo.put("tecnica", "Cerâmica & Barro");
        corpo.put("territorio", "Caruaru");
        corpo.put("categoria", "Casa & Mesa");
        corpo.put("tipo", "Jarras & Vasos");
        corpo.put("preco", 100);
        corpo.put("situacao", "publicada");
        corpo.put("fotos", List.of());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt);
        var resposta = http.exchange("/api/v1/pecas", HttpMethod.POST, new HttpEntity<>(corpo, headers), Map.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(201);
        return (String) ((Map) resposta.getBody().get("dados")).get("slug");
    }
}
