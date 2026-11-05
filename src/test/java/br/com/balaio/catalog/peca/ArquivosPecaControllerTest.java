package br.com.balaio.catalog.peca;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import br.com.balaio.catalog.peca.api.ArquivosPecaController;
import br.com.balaio.catalog.peca.application.ErroPecaException;
import br.com.balaio.catalog.peca.application.port.out.GerenciarPecasPort;

class ArquivosPecaControllerTest {
    @Test
    void distingueFotoAusenteDeFalhaDoBanco() {
        var pecas = mock(GerenciarPecasPort.class);
        var controller = new ArquivosPecaController(pecas);
        assertThatThrownBy(() -> controller.obter("nao-e-uma-foto.png"))
                .isInstanceOf(ErroPecaException.class)
                .extracting("status").isEqualTo(404);

        UUID id = UUID.randomUUID();
        when(pecas.arquivo(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.obter(id + ".png"))
                .isInstanceOf(ErroPecaException.class)
                .extracting("status").isEqualTo(404);

        when(pecas.arquivo(id)).thenThrow(new DataAccessResourceFailureException("conexão perdida"));
        assertThatThrownBy(() -> controller.obter(id + ".png"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
