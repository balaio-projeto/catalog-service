package br.com.balaio.catalog.peca.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import br.com.balaio.catalog.peca.application.ArtesaoPublico;

class ArtesaosPublicosHttpAdapterTest {
    private final AtomicInteger chamadas = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> corpo = new AtomicReference<>("""
            {"dados":[
              {"slug":"artesao-1","nome":"Severina","atelie":"Ateliê Barro Vivo"},
              {"slug":"artesao-2","nome":"Sem ateliê"},
              {"nome":"Sem slug"}
            ],"erro":null}
            """);
    private HttpServer servidor;
    private RelogioAjustavel relogio;
    private ArtesaosPublicosHttpAdapter adapter;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/v1/artesaos", troca -> {
            chamadas.incrementAndGet();
            byte[] resposta = corpo.get().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status.get(), resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();
        relogio = new RelogioAjustavel(Instant.parse("2026-11-25T12:00:00Z"));
        adapter = new ArtesaosPublicosHttpAdapter(RestClient.builder(),
                "http://127.0.0.1:" + servidor.getAddress().getPort(), relogio);
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    @Test
    void listaArtesaosPublicosIgnorandoItensSemSlugEReaproveitaDentroDaValidade() {
        assertThat(adapter.listar()).containsExactly(
                new ArtesaoPublico("artesao-1", "Severina", "Ateliê Barro Vivo"),
                new ArtesaoPublico("artesao-2", "Sem ateliê", ""));
        relogio.avancar(ArtesaosPublicosHttpAdapter.VALIDADE.minusSeconds(1));
        adapter.listar();
        assertThat(chamadas).hasValue(1);

        corpo.set("{\"dados\":[{\"slug\":\"artesao-3\",\"nome\":\"Novo\",\"atelie\":\"Casa Nova\"}]}");
        relogio.avancar(ArtesaosPublicosHttpAdapter.VALIDADE);
        assertThat(adapter.listar()).extracting(ArtesaoPublico::slug).containsExactly("artesao-3");
        assertThat(chamadas).hasValue(2);
    }

    @Test
    void falhaDoPerfilMantemUltimaListaConhecidaEEsperaAntesDeTentarDeNovo() {
        adapter.listar();
        status.set(503);
        relogio.avancar(ArtesaosPublicosHttpAdapter.VALIDADE);

        assertThat(adapter.listar()).extracting(ArtesaoPublico::slug).containsExactly("artesao-1", "artesao-2");
        adapter.listar();
        assertThat(chamadas).hasValue(2);

        status.set(200);
        relogio.avancar(ArtesaosPublicosHttpAdapter.ESPERA_APOS_FALHA);
        adapter.listar();
        assertThat(chamadas).hasValue(3);
    }

    @Test
    void perfilForaDoArRetornaListaVazia() {
        servidor.stop(0);

        assertThat(adapter.listar()).isEmpty();
    }

    private static final class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant agora) {
            this.agora = agora;
        }

        void avancar(java.time.Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
