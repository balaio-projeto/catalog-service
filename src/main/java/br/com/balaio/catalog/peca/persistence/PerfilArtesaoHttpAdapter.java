package br.com.balaio.catalog.peca.persistence;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import br.com.balaio.catalog.peca.application.ErroPecaException;
import br.com.balaio.catalog.peca.application.port.out.ResolverArtesaoPort;

@Component
public class PerfilArtesaoHttpAdapter implements ResolverArtesaoPort {
    private final RestClient client;

    public PerfilArtesaoHttpAdapter(RestClient.Builder builder, @Value("${balaio.catalog.profile-url}") String url) {
        var requisicoes = new SimpleClientHttpRequestFactory();
        requisicoes.setConnectTimeout(2000);
        requisicoes.setReadTimeout(2000);
        this.client = builder.baseUrl(url).requestFactory(requisicoes).build();
    }

    @Override
    public String resolver(UUID contaId, String token) {
        try {
            Map<?, ?> corpo = client.get().uri("/api/v1/artesao/me")
                    .header("Authorization", "Bearer " + token).retrieve().body(Map.class);
            if (corpo != null && corpo.get("dados") instanceof Map<?, ?> dados
                    && contaId.toString().equals(dados.get("id"))
                    && ("artesao-" + contaId).equals(dados.get("slug"))) {
                return (String) dados.get("slug");
            }
        } catch (RuntimeException exception) {
            // Não expor dados do token nem da resposta remota.
        }
        throw new ErroPecaException(503, "PERFIL_INDISPONIVEL", null,
                "Não foi possível confirmar o ateliê do artesão.");
    }
}
