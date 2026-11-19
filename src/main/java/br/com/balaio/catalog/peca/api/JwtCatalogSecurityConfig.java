package br.com.balaio.catalog.peca.api;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class JwtCatalogSecurityConfig {
    @Bean
    JwtDecoder catalogJwtDecoder(@Value("${balaio.catalog.jwt.public-key-location}") String local,
            @Value("${balaio.catalog.jwt.issuer}") String emissor,
            @Value("${balaio.catalog.jwt.audience}") String audiencia,
            ResourceLoader resources) {
        if (local.isBlank() || emissor.isBlank() || audiencia.isBlank()) {
            throw new IllegalStateException("Configuração de JWT do catálogo incompleta.");
        }
        try {
            var recurso = resources.getResource(local);
            if (!recurso.exists() || recurso.contentLength() > 16384) {
                throw new IllegalArgumentException();
            }
            String pem;
            try (var input = recurso.getInputStream()) {
                pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII).strip();
            }
            String inicio = "-----BEGIN PUBLIC KEY-----";
            String fim = "-----END PUBLIC KEY-----";
            if (!pem.startsWith(inicio) || !pem.endsWith(fim)) {
                throw new IllegalArgumentException();
            }
            byte[] der = Base64.getDecoder().decode(pem.substring(inicio.length(), pem.length() - fim.length())
                    .replaceAll("\\s", ""));
            var chave = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            if (chave.getModulus().bitLength() < 3072) {
                throw new IllegalArgumentException();
            }
            var decoder = NimbusJwtDecoder.withPublicKey(chave).signatureAlgorithm(SignatureAlgorithm.RS256).build();
            decoder.setJwtValidator(new ValidadorClaimsJwt(emissor, audiencia, java.time.Clock.systemUTC()));
            return decoder;
        } catch (Exception exception) {
            throw new IllegalStateException("Chave pública JWT do catálogo inválida.", exception);
        }
    }

    @Bean
    SecurityFilterChain catalogSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pecas").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/pecas/*").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/pecas/*").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {})
                        .authenticationEntryPoint((request, response, error) -> erro(response, HttpStatus.UNAUTHORIZED,
                                "NAO_AUTENTICADO", "Autenticação necessária.")))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, error) -> erro(response,
                        HttpStatus.FORBIDDEN, "SEM_PERMISSAO", "Operação não permitida.")))
                .build();
    }

    private void erro(jakarta.servlet.http.HttpServletResponse response, HttpStatus status,
            String codigo, String mensagem) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"dados\":null,\"erro\":{\"codigo\":\"" + codigo
                + "\",\"mensagem\":\"" + mensagem + "\"}}");
    }
}
