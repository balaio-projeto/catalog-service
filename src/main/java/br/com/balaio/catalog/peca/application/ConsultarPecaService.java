package br.com.balaio.catalog.peca.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Peca historico(String slug) {
        return pecas.obter(slug).filter(peca -> "publicada".equals(peca.situacao()))
                .orElseThrow(() -> new ErroPecaException(404, "RECURSO_NAO_ENCONTRADO", null,
                        "Peça não encontrada."));
    }

    @Transactional(readOnly = true)
    public List<Peca> relacionadas(String slug, Integer limiteSolicitado) {
        var peca = obter(slug, null);
        int limite = limiteSolicitado == null || limiteSolicitado == 0 ? 3
                : Math.min(100, Math.max(1, limiteSolicitado));
        return pecas.buscarRelacionadas(slug, peca.tecnica(), limite);
    }

    private boolean visivel(Peca peca, UUID proprietarioId) {
        return proprietarioId != null && proprietarioId.equals(peca.proprietarioId())
                || "publicada".equals(peca.situacao()) && peca.inativadoEm() == null;
    }
}
