package br.com.balaio.catalog.reserva;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.catalog.peca.application.ErroPecaException;

@Service
public class ReservaService {
    private final JdbcTemplate jdbc;

    public ReservaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Criar(String pedidoId, String slug, String chave) { }
    public record Liberar(String pedidoId, String chave) { }
    public record Confirmar(String pedidoId, String chave) { }
    public record Reserva(UUID id, String pedidoId, String slug, String estado,
                          OffsetDateTime expiraEm, BigDecimal preco, String artesao, UUID proprietarioId) { }
    public record Cotacao(String slug, BigDecimal preco, String disponibilidade, String artesao,
                          UUID proprietarioId) { }

    @Transactional(readOnly = true)
    public Cotacao cotar(String slug) {
        if (vazio(slug) || slug.length() > 160)
            throw erro(400, "REQUISICAO_INVALIDA", "Peça inválida.");
        return jdbc.query("SELECT slug, round(preco * (1 - desconto / 100.0), 2) AS preco, disponibilidade, artesao, proprietario_id FROM pecas WHERE slug = ? AND situacao = 'publicada' AND inativado_em IS NULL",
                (rs, row) -> new Cotacao(rs.getString("slug"), rs.getBigDecimal("preco"),
                        rs.getString("disponibilidade"), rs.getString("artesao"),
                        (UUID) rs.getObject("proprietario_id")), slug).stream().findFirst()
                .orElseThrow(() -> erro(409, "PECA_INDISPONIVEL", "Peça indisponível."));
    }

    @Transactional
    public Reserva reservar(Criar comando) {
        if (comando == null || vazio(comando.pedidoId()) || vazio(comando.slug()) || vazio(comando.chave())
                || comando.pedidoId().length() > 160 || comando.slug().length() > 160 || comando.chave().length() > 160)
            throw erro(400, "REQUISICAO_INVALIDA", "Informe pedido, peça e chave idempotente.");
        // Serializa também comandos com a mesma chave para slugs diferentes.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", comando.chave());
        var mesmaChave = buscarPorChave(comando.chave());
        if (mesmaChave != null && (!mesmaChave.pedidoId().equals(comando.pedidoId())
                || !mesmaChave.slug().equals(comando.slug())))
            throw erro(409, "RESERVA_CONFLITANTE", "Chave já vinculada a outro pedido ou peça.");
        var peca = jdbc.query("SELECT slug, round(preco * (1 - desconto / 100.0), 2) AS preco, artesao, proprietario_id, disponibilidade, situacao, inativado_em FROM pecas WHERE slug = ? FOR UPDATE",
                (rs, row) -> new Peca(rs.getString("slug"), rs.getBigDecimal("preco"), rs.getString("artesao"),
                        (UUID) rs.getObject("proprietario_id"),
                        rs.getString("disponibilidade"), rs.getString("situacao"),
                        rs.getObject("inativado_em", OffsetDateTime.class)), comando.slug()).stream().findFirst()
                .orElseThrow(() -> erro(404, "RECURSO_NAO_ENCONTRADO", "Peça não encontrada."));
        if (!"unica".equals(peca.disponibilidade()))
            throw erro(409, "RESERVA_NAO_APLICAVEL", "Esta peça não usa reserva exclusiva.");
        boolean expirou = expirarDaPeca(peca.slug());
        var anterior = buscarPorPedido(comando.pedidoId(), comando.slug());
        if (anterior != null) {
            if (anterior.chave().equals(comando.chave())
                    && ("RESERVADA".equals(anterior.estado()) || "CONSUMIDA".equals(anterior.estado())))
                return resposta(anterior);
            throw erro(409, "RESERVA_CONFLITANTE", "Reserva anterior incompatível ou expirada.");
        }
        if (!"publicada".equals(peca.situacao()) || (peca.inativadoEm() != null && !expirou))
            throw erro(409, "PECA_INDISPONIVEL", "Peça indisponível.");
        var id = UUID.randomUUID();
        var expiraEm = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15);
        jdbc.update("INSERT INTO reservas_peca(id, pedido_id, slug, preco, artesao, proprietario_id, chave, estado, expira_em) VALUES (?, ?, ?, ?, ?, ?, ?, 'RESERVADA', ?)",
                id, comando.pedidoId(), comando.slug(), peca.preco(), peca.artesao(), peca.proprietarioId(), comando.chave(), expiraEm);
        jdbc.update("UPDATE pecas SET inativado_em = now() WHERE slug = ?", comando.slug());
        return new Reserva(id, comando.pedidoId(), comando.slug(), "RESERVADA", expiraEm, peca.preco(), peca.artesao(), peca.proprietarioId());
    }

    @Transactional
    public Reserva liberar(UUID id, Liberar comando) {
        if (id == null || comando == null || vazio(comando.pedidoId()) || vazio(comando.chave())
                || comando.pedidoId().length() > 160 || comando.chave().length() > 160)
            throw erro(400, "REQUISICAO_INVALIDA", "Informe pedido e chave idempotente.");
        var slug = jdbc.query("SELECT slug FROM reservas_peca WHERE id = ?", (rs, row) -> rs.getString(1), id)
                .stream().findFirst().orElseThrow(() -> erro(404, "RESERVA_NAO_ENCONTRADA", "Reserva não encontrada."));
        jdbc.query("SELECT slug FROM pecas WHERE slug = ? FOR UPDATE", (rs, row) -> rs.getString(1), slug);
        var reserva = buscarPorId(id);
        if (!reserva.pedidoId().equals(comando.pedidoId()) || !comando.chave().equals("liberar:" + id))
            throw erro(409, "RESERVA_CONFLITANTE", "Pedido ou chave de liberação incompatível.");
        if ("RESERVADA".equals(reserva.estado()) || "CONSUMIDA".equals(reserva.estado())) {
            jdbc.update("UPDATE reservas_peca SET estado = 'LIBERADA' WHERE id = ?", id);
            jdbc.update("UPDATE pecas SET inativado_em = NULL WHERE slug = ?", slug);
            reserva = new Linha(reserva.id(), reserva.pedidoId(), reserva.slug(), reserva.preco(), reserva.artesao(),
                    reserva.proprietarioId(), reserva.chave(), "LIBERADA", reserva.expiraEm());
        }
        return resposta(reserva);
    }

    @Transactional
    public Reserva confirmar(UUID id, Confirmar comando) {
        if (id == null || comando == null || vazio(comando.pedidoId()) || vazio(comando.chave())
                || comando.pedidoId().length() > 160 || comando.chave().length() > 160)
            throw erro(400, "REQUISICAO_INVALIDA", "Informe pedido e chave idempotente.");
        var slug = jdbc.query("SELECT slug FROM reservas_peca WHERE id = ?", (rs, row) -> rs.getString(1), id)
                .stream().findFirst().orElseThrow(() -> erro(404, "RESERVA_NAO_ENCONTRADA", "Reserva não encontrada."));
        jdbc.query("SELECT slug FROM pecas WHERE slug = ? FOR UPDATE", (rs, row) -> rs.getString(1), slug);
        var reserva = buscarPorId(id);
        if (!reserva.pedidoId().equals(comando.pedidoId()) || !comando.chave().equals("confirmar:" + id))
            throw erro(409, "RESERVA_CONFLITANTE", "Pedido ou chave de confirmação incompatível.");
        if ("CONSUMIDA".equals(reserva.estado())) return resposta(reserva);
        if (!"RESERVADA".equals(reserva.estado()) || !reserva.expiraEm().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
            throw erro(409, "RESERVA_EXPIRADA", "Reserva indisponível para confirmação.");
        jdbc.update("UPDATE reservas_peca SET estado = 'CONSUMIDA' WHERE id = ?", id);
        return resposta(new Linha(reserva.id(), reserva.pedidoId(), reserva.slug(), reserva.preco(), reserva.artesao(),
                reserva.proprietarioId(), reserva.chave(), "CONSUMIDA", reserva.expiraEm()));
    }

    @Transactional(readOnly = true)
    public Reserva consultar(UUID id, String pedidoId) {
        var reserva = buscarPorId(id);
        if (!reserva.pedidoId().equals(pedidoId))
            throw erro(404, "RESERVA_NAO_ENCONTRADA", "Reserva não encontrada.");
        return resposta(reserva);
    }

    @Scheduled(fixedDelayString = "${balaio.reservas.expiracao-intervalo-ms:60000}")
    @Transactional
    public void expirar() {
        List<String> slugs = jdbc.query("SELECT DISTINCT slug FROM reservas_peca WHERE estado = 'RESERVADA' AND expira_em <= now() LIMIT 100",
                (rs, row) -> rs.getString(1));
        for (String slug : slugs) {
            jdbc.query("SELECT slug FROM pecas WHERE slug = ? FOR UPDATE", (rs, row) -> rs.getString(1), slug);
            expirarDaPeca(slug);
        }
    }

    private boolean expirarDaPeca(String slug) {
        int alteradas = jdbc.update("UPDATE reservas_peca SET estado = 'EXPIRADA' WHERE slug = ? AND estado = 'RESERVADA' AND expira_em <= now()", slug);
        if (alteradas > 0) jdbc.update("UPDATE pecas SET inativado_em = NULL WHERE slug = ?", slug);
        return alteradas > 0;
    }

    private Linha buscarPorPedido(String pedidoId, String slug) {
        return jdbc.query("SELECT id, pedido_id, slug, preco, artesao, proprietario_id, chave, estado, expira_em FROM reservas_peca WHERE pedido_id = ? AND slug = ?",
                (rs, row) -> linha(rs), pedidoId, slug).stream().findFirst().orElse(null);
    }

    private Linha buscarPorChave(String chave) {
        return jdbc.query("SELECT id, pedido_id, slug, preco, artesao, proprietario_id, chave, estado, expira_em FROM reservas_peca WHERE chave = ?",
                (rs, row) -> linha(rs), chave).stream().findFirst().orElse(null);
    }

    private Linha buscarPorId(UUID id) {
        return jdbc.query("SELECT id, pedido_id, slug, preco, artesao, proprietario_id, chave, estado, expira_em FROM reservas_peca WHERE id = ?",
                (rs, row) -> linha(rs), id).stream().findFirst()
                .orElseThrow(() -> erro(404, "RESERVA_NAO_ENCONTRADA", "Reserva não encontrada."));
    }

    private Linha linha(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Linha((UUID) rs.getObject("id"), rs.getString("pedido_id"), rs.getString("slug"),
                rs.getBigDecimal("preco"), rs.getString("artesao"), (UUID) rs.getObject("proprietario_id"),
                rs.getString("chave"), rs.getString("estado"), rs.getObject("expira_em", OffsetDateTime.class));
    }

    private Reserva resposta(Linha linha) {
        return new Reserva(linha.id(), linha.pedidoId(), linha.slug(), linha.estado(), linha.expiraEm(),
                linha.preco(), linha.artesao(), linha.proprietarioId());
    }

    private boolean vazio(String valor) { return valor == null || valor.isBlank(); }
    private ErroPecaException erro(int status, String codigo, String mensagem) {
        return new ErroPecaException(status, codigo, null, mensagem);
    }
    private record Peca(String slug, BigDecimal preco, String artesao, UUID proprietarioId, String disponibilidade,
                        String situacao, OffsetDateTime inativadoEm) { }
    private record Linha(UUID id, String pedidoId, String slug, BigDecimal preco, String artesao,
                        UUID proprietarioId, String chave, String estado, OffsetDateTime expiraEm) { }
}
