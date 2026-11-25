package br.com.balaio.catalog.peca.application.port.out;

import java.util.List;

import br.com.balaio.catalog.peca.application.ArtesaoPublico;

public interface ConsultarArtesaosPort {
    List<ArtesaoPublico> listar();
}
