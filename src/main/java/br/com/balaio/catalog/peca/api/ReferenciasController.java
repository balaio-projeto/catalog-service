package br.com.balaio.catalog.peca.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.balaio.catalog.peca.application.ConsultarReferenciasService;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/referencias")
public class ReferenciasController {
    private final ConsultarReferenciasService referencias;

    public ReferenciasController(ConsultarReferenciasService referencias) {
        this.referencias = referencias;
    }

    @GetMapping
    @Operation(summary = "Consultar referências curadas e persistidas no catálogo")
    public Resposta consultar() {
        var dados = referencias.consultar();
        return new Resposta(new Dados(dados.tecnicas(), dados.territorios(),
                dados.categorias(), dados.tipos(), dados.etiquetas()), null);
    }

    public record Dados(List<String> tecnicas, List<String> territorios, List<String> categorias,
            List<String> tipos, List<String> etiquetas) {
    }

    public record Resposta(Dados dados, Object erro) {
    }
}
