package br.com.balaio.catalog.peca.application.port.in;

import java.util.UUID;
import br.com.balaio.catalog.peca.application.DadosPeca;
import br.com.balaio.catalog.peca.domain.Peca;

public interface AtualizarPecaUseCase {
    Peca atualizar(String slug, DadosPeca alteracoes, UUID contaId);
    Peca inativar(String slug, UUID contaId);
}
