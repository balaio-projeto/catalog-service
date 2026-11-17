package br.com.balaio.catalog.peca.application.port.out;

import java.util.Map;
import java.util.UUID;
import br.com.balaio.catalog.peca.domain.PecaPainel;

public interface ConsultarReservasDoArtesaoPort {
    Map<String, PecaPainel.Reserva> consultar(UUID proprietarioId);
}
