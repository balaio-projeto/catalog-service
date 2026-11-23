package br.com.balaio.catalog.peca.persistence;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.balaio.catalog.peca.application.FiltroCatalogo;
import br.com.balaio.catalog.peca.application.FotoStorageIndisponivelException;
import br.com.balaio.catalog.peca.application.OrdenacaoCatalogo;
import br.com.balaio.catalog.peca.application.port.out.ConsultarPecasPort;
import br.com.balaio.catalog.peca.application.port.out.FotoStoragePort;
import br.com.balaio.catalog.peca.application.port.out.GerenciarPecasPort;
import br.com.balaio.catalog.peca.domain.Peca;
import br.com.balaio.catalog.peca.domain.FotoPeca;

@Repository
public class PecasPersistenceAdapter implements ConsultarPecasPort, GerenciarPecasPort {
    private static final String PREFIXO_URL_FOTO = "/api/v1/arquivos/pecas/";
    private final SpringDataPecaRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<FotoStoragePort> storageProvider;
    private final LimpezaFotosStorage limpeza;

    public PecasPersistenceAdapter(SpringDataPecaRepository repository, JdbcTemplate jdbc,
            ObjectProvider<FotoStoragePort> storageProvider, LimpezaFotosStorage limpeza) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.storageProvider = storageProvider;
        this.limpeza = limpeza;
    }

    @Override
    public long contar(FiltroCatalogo filtro) {
        return repository.count(PecaSpecifications.catalogo(filtro));
    }

    @Override
    public List<Peca> buscar(FiltroCatalogo filtro, int indicePagina, int tamanho, OrdenacaoCatalogo ordenacao) {
        var pagina = repository.findAll(
                PecaSpecifications.catalogo(filtro),
                PageRequest.of(indicePagina, tamanho, ordenar(ordenacao)));
        return pagina.getContent().stream().map(entity -> comFotos(entity.toDomain())).toList();
    }

    @Override
    public void bloquearCriacao() {
        jdbc.queryForObject("SELECT id FROM peca_criacao_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }

    @Override
    public void bloquearPeca(String slug) {
        jdbc.query("SELECT slug FROM pecas WHERE slug = ? FOR UPDATE", (rs, row) -> rs.getString(1), slug);
    }

    @Override
    public boolean possuiReservaAtiva(String slug) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM reservas_peca WHERE slug = ? AND estado IN ('RESERVADA', 'CONSUMIDA')",
                Integer.class, slug);
        return total != null && total > 0;
    }

    @Override
    public boolean existe(String slug) { return repository.existsById(slug); }

    @Override
    public int proximaOrdem() {
        Integer ordem = jdbc.queryForObject("SELECT COALESCE(MAX(ordem_catalogo), 0) + 1 FROM pecas", Integer.class);
        return ordem == null ? 1 : ordem;
    }

    @Override
    public Optional<Peca> obter(String slug) {
        return repository.findById(slug).map(entity -> comFotos(entity.toDomain()));
    }

    @Override
    public List<Peca> buscarRelacionadas(String slug, String tecnica, int limite) {
        var filtro = PecaSpecifications.catalogo(new FiltroCatalogo(null, tecnica, null, null, null, null, null))
                .and((root, query, criteria) -> criteria.notEqual(root.get("slug"), slug));
        var relacionadas = repository.findBy(filtro,
                consulta -> consulta.sortBy(Sort.by("ordemCatalogo")).limit(limite).all());
        return relacionadas.stream().map(entity -> comFotos(entity.toDomain())).toList();
    }

    @Override
    public List<Peca> buscarPorArtesao(String slug, UUID proprietarioId) {
        org.springframework.data.jpa.domain.Specification<PecaJpaEntity> filtro =
                (root, query, criteria) -> criteria.equal(root.get("artesao"), slug);
        if (proprietarioId == null) {
            filtro = filtro.and(PecaSpecifications.catalogo(
                    new FiltroCatalogo(null, null, null, null, null, null, null)));
        } else {
            filtro = filtro.and((root, query, criteria) ->
                    criteria.equal(root.get("proprietarioId"), proprietarioId));
        }
        return repository.findAll(filtro, Sort.by(Sort.Order.desc("ordemCatalogo")))
                .stream().map(entity -> comFotos(entity.toDomain())).toList();
    }

    @Override
    public Peca salvar(Peca peca, List<FotoGravacao> fotos) {
        repository.saveAndFlush(PecaJpaEntity.fromDomain(peca));
        if (fotos != null) {
            var storage = storageProvider.getIfAvailable();
            var antigas = jdbc.query("SELECT storage_key FROM peca_fotos WHERE peca_slug = ? AND storage_key IS NOT NULL",
                    (rs, row) -> rs.getString(1), peca.slug());
            var manter = new HashSet<String>();
            var completas = new ArrayList<FotoPersistida>();
            for (var foto : fotos) {
                UUID id = idDaUrl(foto.foto().url());
                String chave = null;
                String formato = foto.formato();
                byte[] legado = null;
                if (foto.conteudo() != null) {
                    if (storage == null) {
                        throw new FotoStorageIndisponivelException();
                    }
                    chave = chaveStorage(peca.slug(), id, formato);
                    limpeza.limparSeRollback(chave);
                    storage.guardar(chave, formato, foto.conteudo());
                } else {
                    var atual = jdbc.query("SELECT formato, conteudo, storage_key FROM peca_fotos WHERE id = ?",
                            (rs, row) -> new FotoPersistida(foto, rs.getString("formato"), rs.getBytes("conteudo"),
                                    rs.getString("storage_key")), id).stream().findFirst()
                            .orElseThrow(() -> new IllegalStateException("Foto existente não encontrada."));
                    formato = atual.formato();
                    chave = atual.storageKey();
                    legado = atual.conteudo();
                    if (chave == null && storage != null && legado != null) {
                        chave = chaveStorage(peca.slug(), id, formato);
                        limpeza.limparSeRollback(chave);
                        storage.guardar(chave, formato, legado);
                        legado = null;
                    }
                }
                if (chave != null) manter.add(chave);
                completas.add(new FotoPersistida(foto, formato, legado, chave));
            }
            antigas.stream().filter(chave -> !manter.contains(chave)).forEach(limpeza::agendar);
            jdbc.update("DELETE FROM peca_fotos WHERE peca_slug = ?", peca.slug());
            for (var persistida : completas) {
                var foto = persistida.gravacao().foto();
                jdbc.update("INSERT INTO peca_fotos (id, peca_slug, foto_id, nome, url, ordem, formato, conteudo, storage_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        idDaUrl(foto.url()), peca.slug(), foto.id(), foto.nome(), foto.url(), foto.ordem(),
                        persistida.formato(), persistida.conteudo(), persistida.storageKey());
            }
        }
        return obter(peca.slug()).orElseThrow();
    }

    @Override
    public Optional<ArquivoFoto> arquivo(UUID id) {
        var arquivo = jdbc.query("SELECT formato, conteudo, storage_key FROM peca_fotos WHERE id = ?",
                (rs, row) -> new FotoArmazenada(rs.getString("formato"), rs.getBytes("conteudo"),
                        rs.getString("storage_key")), id).stream().findFirst();
        if (arquivo.isEmpty()) return Optional.empty();
        var persistido = arquivo.get();
        if (persistido.storageKey() != null) {
            var storage = storageProvider.getIfAvailable();
            if (storage == null) throw new FotoStorageIndisponivelException();
            return storage.obter(persistido.storageKey()).map(bytes -> new ArquivoFoto(persistido.formato(), bytes));
        }
        return persistido.conteudo() == null ? Optional.empty()
                : Optional.of(new ArquivoFoto(persistido.formato(), persistido.conteudo()));
    }

    private UUID idDaUrl(String url) {
        int inicio = PREFIXO_URL_FOTO.length();
        int ponto = url.lastIndexOf('.');
        return UUID.fromString(url.substring(inicio, ponto));
    }

    private String chaveStorage(String slug, UUID id, String formato) {
        String extensao = "jpeg".equals(formato) ? "jpg" : formato;
        return "catalog/" + slug + "/" + id + "." + extensao;
    }

    private record FotoPersistida(FotoGravacao gravacao, String formato, byte[] conteudo, String storageKey) {}

    private record FotoArmazenada(String formato, byte[] conteudo, String storageKey) {}

    private Peca comFotos(Peca peca) {
        var fotos = jdbc.query("SELECT foto_id, nome, url, ordem FROM peca_fotos WHERE peca_slug = ? ORDER BY ordem",
                (rs, row) -> new FotoPeca(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)),
                peca.slug());
        return new Peca(peca.slug(), peca.nome(), peca.artesao(), peca.territorio(), peca.tecnica(),
                peca.categoria(), peca.tipo(), peca.preco(), peca.desconto(), peca.disponibilidade(),
                peca.prazoProducaoDias(), peca.historia(), peca.imagem(), peca.avaliacao(),
                peca.totalAvaliacoes(), peca.situacao(), peca.inativadoEm(), peca.ordemCatalogo(),
                peca.proprietarioId(), peca.criadaEm(), fotos, peca.materiais(), peca.autentica());
    }

    private Sort ordenar(OrdenacaoCatalogo ordenacao) {
        return switch (ordenacao) {
            case PRECO_ASC -> Sort.by(Sort.Order.asc("precoComDesconto"), Sort.Order.asc("ordemCatalogo"));
            case PRECO_DESC -> Sort.by(Sort.Order.desc("precoComDesconto"), Sort.Order.asc("ordemCatalogo"));
            case AVALIACAO -> Sort.by(
                    Sort.Order.desc("avaliacao").nullsLast(),
                    Sort.Order.asc("ordemCatalogo"));
            case RECENTES -> Sort.by(Sort.Order.desc("ordemCatalogo"));
            case PADRAO -> Sort.by(Sort.Order.asc("ordemCatalogo"));
        };
    }
}
