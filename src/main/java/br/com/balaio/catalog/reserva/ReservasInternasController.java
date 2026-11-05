package br.com.balaio.catalog.reserva;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.balaio.catalog.peca.application.ErroPecaException;

@RestController
@RequestMapping("/internal/v1/reservas")
public class ReservasInternasController {
    private final ReservaService reservas;
    private final String segredo;

    public ReservasInternasController(ReservaService reservas,
            @Value("${balaio.internal.token:}") String segredo) {
        this.reservas = reservas;
        this.segredo = segredo;
    }

    @PostMapping
    public ResponseEntity<Resposta> criar(@RequestHeader(value = "X-Balaio-Service-Token", required = false) String token,
            @RequestBody ReservaService.Criar comando) {
        autorizar(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(new Resposta(reservas.reservar(comando), null));
    }

    @PostMapping("/{id}/liberar")
    public Resposta liberar(@RequestHeader(value = "X-Balaio-Service-Token", required = false) String token,
            @PathVariable UUID id, @RequestBody ReservaService.Liberar comando) {
        autorizar(token);
        return new Resposta(reservas.liberar(id, comando), null);
    }

    @GetMapping("/{id}")
    public Resposta consultar(@RequestHeader(value = "X-Balaio-Service-Token", required = false) String token,
            @RequestHeader("X-Balaio-Pedido-Id") String pedidoId, @PathVariable UUID id) {
        autorizar(token);
        return new Resposta(reservas.consultar(id, pedidoId), null);
    }

    private void autorizar(String token) {
        if (segredo.length() < 32 || token == null || !MessageDigest.isEqual(
                segredo.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new ErroPecaException(403, "SEM_PERMISSAO", null, "Operação interna não permitida.");
    }

    public record Resposta(ReservaService.Reserva dados, Object erro) { }
}
