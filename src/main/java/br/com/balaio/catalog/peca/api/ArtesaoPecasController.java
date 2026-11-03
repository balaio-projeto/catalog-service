package br.com.balaio.catalog.peca.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.balaio.catalog.peca.application.ConsultarPecaService;

@RestController
@RequestMapping("/api/v1/artesaos/{slug}/pecas")
public class ArtesaoPecasController {
    private final ConsultarPecaService consultar;

    public ArtesaoPecasController(ConsultarPecaService consultar) {
        this.consultar = consultar;
    }

    @GetMapping
    public PecasListResponse listar(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        var dono = PecasController.proprietario(jwt);
        if (dono != null && !slug.equals("artesao-" + dono)) dono = null;
        var dados = consultar.listarPorArtesao(slug, dono).stream().map(PecaResponse::from).toList();
        return new PecasListResponse(dados, null, null);
    }
}
