package br.com.balaio.catalog.peca.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import br.com.balaio.catalog.peca.application.port.out.ConsultarReservasDoArtesaoPort;
import br.com.balaio.catalog.peca.domain.PecaPainel;

@Repository
public class ReservasDoArtesaoPersistenceAdapter implements ConsultarReservasDoArtesaoPort {
    private final JdbcTemplate jdbc;

    public ReservasDoArtesaoPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Map<String, PecaPainel.Reserva> consultar(UUID proprietarioId) {
        var reservas = new HashMap<String, PecaPainel.Reserva>();
        jdbc.query("""
                SELECT r.slug, r.pedido_id, r.estado FROM reservas_peca r
                JOIN pecas p ON p.slug = r.slug
                WHERE p.proprietario_id = ? AND r.estado IN ('RESERVADA', 'CONSUMIDA')
                """, (RowCallbackHandler) rs -> reservas.put(rs.getString("slug"),
                        new PecaPainel.Reserva(rs.getString("pedido_id"), "CONSUMIDA".equals(rs.getString("estado")))),
                proprietarioId);
        return Map.copyOf(reservas);
    }
}
