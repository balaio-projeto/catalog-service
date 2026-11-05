package br.com.balaio.catalog.peca.api;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import br.com.balaio.catalog.peca.application.port.out.GerenciarPecasPort;
import br.com.balaio.catalog.peca.application.ErroPecaException;

@RestController
@RequestMapping("/api/v1/arquivos/pecas")
public class ArquivosPecaController {
    private static final Pattern NOME = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp)$");
    private final GerenciarPecasPort pecas;

    public ArquivosPecaController(GerenciarPecasPort pecas) { this.pecas = pecas; }

    @GetMapping("/{nome}")
    public ResponseEntity<byte[]> obter(@PathVariable String nome) {
        if (!NOME.matcher(nome).matches()) throw naoEncontrada();
        int ponto = nome.lastIndexOf('.');
        UUID id;
        try {
            id = UUID.fromString(nome.substring(0, ponto));
        } catch (IllegalArgumentException exception) {
            throw naoEncontrada();
        }
        var arquivo = pecas.arquivo(id).orElseThrow(this::naoEncontrada);
        String extensao = arquivo.formato().equals("jpeg") ? "jpg" : arquivo.formato();
        if (!nome.substring(ponto + 1).equals(extensao)) throw naoEncontrada();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/" + arquivo.formato()))
                .body(arquivo.conteudo());
    }

    private ErroPecaException naoEncontrada() {
        return new ErroPecaException(404, "RECURSO_NAO_ENCONTRADO", null, "Foto não encontrada.");
    }
}
