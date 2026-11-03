package br.com.balaio.catalog.peca.application.port.out;

import java.util.UUID;

public interface ResolverArtesaoPort {
    String resolver(UUID contaId, String token);
}
