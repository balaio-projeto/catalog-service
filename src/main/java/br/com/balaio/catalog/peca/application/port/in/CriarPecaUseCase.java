package br.com.balaio.catalog.peca.application.port.in;

import java.util.UUID;
import br.com.balaio.catalog.peca.application.DadosPeca;
import br.com.balaio.catalog.peca.domain.Peca;

public interface CriarPecaUseCase {
    Peca criar(DadosPeca dados, UUID contaId, String token);
}
