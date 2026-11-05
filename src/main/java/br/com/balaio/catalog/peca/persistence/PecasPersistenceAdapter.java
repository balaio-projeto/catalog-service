package br.com.balaio.catalog.peca.persistence;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.balaio.catalog.peca.application.FiltroCatalogo;
import br.com.balaio.catalog.peca.application.OrdenacaoCatalogo;
import br.com.balaio.catalog.peca.application.port.out.ConsultarPecasPort;
import br.com.balaio.catalog.peca.application.port.out.GerenciarPecasPort;
import br.com.balaio.catalog.peca.domain.Peca;
import br.com.balaio.catalog.peca.domain.FotoPeca;

@Repository
public class PecasPersistenceAdapter implements ConsultarPecasPort, GerenciarPecasPort {
    private final SpringDataPecaRepository repository;
    private final JdbcTemplate jdbc;

    public PecasPersistenceAdapter(SpringDataPecaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
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
            var completas = new ArrayList<FotoGravacao>();
            for (var foto : fotos) {
                if (foto.conteudo() != null) {
                    completas.add(foto);
                } else {
                    var id = UUID.fromString(foto.foto().url().substring("/api/v1/arquivos/pecas/".length(),
                            foto.foto().url().lastIndexOf('.')));
                    var atual = arquivo(id).orElseThrow();
                    completas.add(new FotoGravacao(foto.foto(), atual.formato(), atual.conteudo()));
                }
            }
            jdbc.update("DELETE FROM peca_fotos WHERE peca_slug = ?", peca.slug());
            for (var foto : completas) {
                var id = UUID.fromString(foto.foto().url().substring("/api/v1/arquivos/pecas/".length(),
                        foto.foto().url().lastIndexOf('.')));
                jdbc.update("INSERT INTO peca_fotos (id, peca_slug, foto_id, nome, url, ordem, formato, conteudo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        id, peca.slug(), foto.foto().id(), foto.foto().nome(), foto.foto().url(),
                        foto.foto().ordem(), foto.formato(), foto.conteudo());
            }
        }
        return obter(peca.slug()).orElseThrow();
    }

    @Override
    public Optional<ArquivoFoto> arquivo(UUID id) {
        return jdbc.query("SELECT formato, conteudo FROM peca_fotos WHERE id = ?",
                (rs, row) -> new ArquivoFoto(rs.getString(1), rs.getBytes(2)), id).stream().findFirst();
    }

    private Peca comFotos(Peca peca) {
        var fotos = jdbc.query("SELECT foto_id, nome, url, ordem FROM peca_fotos WHERE peca_slug = ? ORDER BY ordem",
                (rs, row) -> new FotoPeca(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)),
                peca.slug());
        return new Peca(peca.slug(), peca.nome(), peca.artesao(), peca.territorio(), peca.tecnica(),
                peca.categoria(), peca.tipo(), peca.preco(), peca.desconto(), peca.disponibilidade(),
                peca.prazoProducaoDias(), peca.historia(), peca.imagem(), peca.avaliacao(),
                peca.totalAvaliacoes(), peca.situacao(), peca.inativadoEm(), peca.ordemCatalogo(),
                peca.proprietarioId(), peca.criadaEm(), fotos);
    }

    private Sort ordenar(OrdenacaoCatalogo ordenacao) {
        return switch (ordenacao) {
            case PRECO_ASC -> Sort.by(Sort.Order.asc("precoComDesconto"));
            case PRECO_DESC -> Sort.by(Sort.Order.desc("precoComDesconto"));
            case AVALIACAO -> Sort.by(
                    Sort.Order.desc("avaliacao").nullsLast(),
                    Sort.Order.asc("ordemCatalogo"));
            case RECENTES -> Sort.by(Sort.Order.desc("ordemCatalogo"));
            case PADRAO -> Sort.by(Sort.Order.asc("ordemCatalogo"));
        };
    }
}
