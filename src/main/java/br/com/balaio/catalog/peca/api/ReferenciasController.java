package br.com.balaio.catalog.peca.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.balaio.catalog.peca.domain.ReferenciasCatalogo;

@RestController
@RequestMapping("/api/v1/referencias")
public class ReferenciasController {
    @GetMapping
    public Resposta consultar() {
        return new Resposta(new Dados(ReferenciasCatalogo.TECNICAS, ReferenciasCatalogo.TERRITORIOS,
                ReferenciasCatalogo.CATEGORIAS, ReferenciasCatalogo.TIPOS, ReferenciasCatalogo.ETIQUETAS), null);
    }

    public record Dados(List<String> tecnicas, List<String> territorios, List<String> categorias,
            List<String> tipos, List<String> etiquetas) {
    }

    public record Resposta(Dados dados, Object erro) {
    }
}
