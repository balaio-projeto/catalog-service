package br.com.balaio.catalog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class CatalogJwtTestSupport {
    private static final KeyPair CHAVES;
    private static final Path PUBLICA;

    static {
        try {
            var gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(3072);
            CHAVES = gerador.generateKeyPair();
            PUBLICA = Files.createTempFile("catalog-test-public-", ".pem");
            Files.writeString(PUBLICA, "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(CHAVES.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n", StandardCharsets.US_ASCII);
            PUBLICA.toFile().deleteOnExit();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("balaio.catalog.jwt.public-key-location", () -> PUBLICA.toUri().toString());
        registry.add("balaio.catalog.jwt.issuer", () -> "balaio-test");
        registry.add("balaio.catalog.jwt.audience", () -> "balaio-test-audience");
    }

    protected String token(UUID id, String papel) {
        var emissor = NimbusJwtEncoder.withKeyPair((java.security.interfaces.RSAPublicKey) CHAVES.getPublic(),
                (java.security.interfaces.RSAPrivateKey) CHAVES.getPrivate())
                .algorithm(SignatureAlgorithm.RS256).build();
        var agora = Instant.now();
        var claims = JwtClaimsSet.builder().subject(id.toString()).claim("papel", papel)
                .issuer("balaio-test").audience(List.of("balaio-test-audience"))
                .issuedAt(agora).expiresAt(agora.plusSeconds(3600)).id(UUID.randomUUID().toString()).build();
        return emissor.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }
}
