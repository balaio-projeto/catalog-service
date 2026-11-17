package br.com.balaio.catalog.peca.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.balaio.catalog.peca.application.port.out.ConsultarReferenciasPort;
import br.com.balaio.catalog.peca.domain.ReferenciasCatalogo;

@Service
public class ConsultarReferenciasService {
    private final ConsultarReferenciasPort referencias;

    public ConsultarReferenciasService(ConsultarReferenciasPort referencias) {
        this.referencias = referencias;
    }

    @Transactional(readOnly = true)
    public ReferenciasCatalogo consultar() {
        return referencias.consultar();
    }
}
