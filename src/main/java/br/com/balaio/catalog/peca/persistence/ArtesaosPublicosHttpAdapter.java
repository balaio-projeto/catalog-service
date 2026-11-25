package br.com.balaio.catalog.peca.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.com.balaio.catalog.peca.application.ArtesaoPublico;
import br.com.balaio.catalog.peca.application.port.out.ConsultarArtesaosPort;

@Component
public class ArtesaosPublicosHttpAdapter implements ConsultarArtesaosPort {
    static final Duration VALIDADE = Duration.ofSeconds(30);
    static final Duration ESPERA_APOS_FALHA = Duration.ofSeconds(10);

    private final RestClient client;
    private final Clock relogio;
    private volatile Instantaneo instantaneo = new Instantaneo(List.of(), Instant.MIN);

    @Autowired
    public ArtesaosPublicosHttpAdapter(RestClient.Builder builder,
            @Value("${balaio.catalog.profile-url}") String url) {
        this(builder, url, Clock.systemUTC());
    }

    ArtesaosPublicosHttpAdapter(RestClient.Builder builder, String url, Clock relogio) {
        var requisicoes = new SimpleClientHttpRequestFactory();
        requisicoes.setConnectTimeout(1000);
        requisicoes.setReadTimeout(1500);
        this.client = builder.baseUrl(url).requestFactory(requisicoes).build();
        this.relogio = relogio;
    }

    @Override
    public List<ArtesaoPublico> listar() {
        var atual = instantaneo;
        if (relogio.instant().isBefore(atual.expiraEm())) {
            return atual.artesaos();
        }
        synchronized (this) {
            atual = instantaneo;
            var agora = relogio.instant();
            if (agora.isBefore(atual.expiraEm())) {
                return atual.artesaos();
            }
            try {
                instantaneo = new Instantaneo(consultar(), agora.plus(VALIDADE));
            } catch (RuntimeException exception) {
                instantaneo = new Instantaneo(atual.artesaos(), agora.plus(ESPERA_APOS_FALHA));
            }
            return instantaneo.artesaos();
        }
    }

    private List<ArtesaoPublico> consultar() {
        Map<?, ?> corpo = client.get().uri("/api/v1/artesaos").retrieve().body(Map.class);
        if (corpo == null || !(corpo.get("dados") instanceof List<?> dados)) {
            throw new IllegalStateException("Resposta de artesãos sem lista de dados.");
        }
        return dados.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(artesao -> artesao.get("slug") instanceof String slug && !slug.isBlank())
                .map(artesao -> new ArtesaoPublico((String) artesao.get("slug"), texto(artesao.get("nome")),
                        texto(artesao.get("atelie"))))
                .toList();
    }

    private static String texto(Object valor) {
        return valor instanceof String texto ? texto : "";
    }

    private record Instantaneo(List<ArtesaoPublico> artesaos, Instant expiraEm) {
    }
}
