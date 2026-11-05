package br.com.balaio.catalog.peca.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.balaio.catalog.peca.application.port.out.GerenciarPecasPort;
import br.com.balaio.catalog.peca.domain.Peca;

@Service
public class ConsultarPecaService {
    private final GerenciarPecasPort pecas;

    public ConsultarPecaService(GerenciarPecasPort pecas) {
        this.pecas = pecas;
    }

    public Peca obter(String slug, UUID proprietarioId) {
        return pecas.obter(slug).filter(peca -> visivel(peca, proprietarioId))
                .orElseThrow(() -> new ErroPecaException(404, "RECURSO_NAO_ENCONTRADO", null,
                        "Peça não encontrada."));
    }

    public List<Peca> listarPorArtesao(String slug, UUID proprietarioId) {
        return pecas.buscarPorArtesao(slug, proprietarioId);
    }

    private boolean visivel(Peca peca, UUID proprietarioId) {
        return proprietarioId != null && proprietarioId.equals(peca.proprietarioId())
                || "publicada".equals(peca.situacao()) && peca.inativadoEm() == null;
    }
}
