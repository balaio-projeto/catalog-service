package br.com.balaio.catalog.peca.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import br.com.balaio.catalog.peca.application.port.out.ConsultarReferenciasPort;
import br.com.balaio.catalog.peca.domain.ReferenciasCatalogo;

@Repository
public class ReferenciasPersistenceAdapter implements ConsultarReferenciasPort {
    private final JdbcTemplate jdbc;

    public ReferenciasPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReferenciasCatalogo consultar() {
        var grupos = new HashMap<String, List<String>>();
        jdbc.query("SELECT grupo, valor FROM referencias_catalogo ORDER BY grupo, ordem",
                (RowCallbackHandler) rs -> grupos.computeIfAbsent(rs.getString("grupo"), chave -> new ArrayList<>())
                        .add(rs.getString("valor")));
        return new ReferenciasCatalogo(grupos.getOrDefault("tecnicas", List.of()),
                grupos.getOrDefault("territorios", List.of()), grupos.getOrDefault("categorias", List.of()),
                grupos.getOrDefault("tipos", List.of()), grupos.getOrDefault("etiquetas", List.of()));
    }
}
