package br.com.balaio.catalog.peca.persistence;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import br.com.balaio.catalog.peca.application.FiltroCatalogo;
import br.com.balaio.catalog.peca.application.OrdenacaoCatalogo;
import br.com.balaio.catalog.peca.application.port.out.ConsultarPecasPort;
import br.com.balaio.catalog.peca.domain.Peca;

@Repository
public class PecasPersistenceAdapter implements ConsultarPecasPort {
    private final SpringDataPecaRepository repository;

    public PecasPersistenceAdapter(SpringDataPecaRepository repository) {
        this.repository = repository;
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
        return pagina.getContent().stream().map(PecaJpaEntity::toDomain).toList();
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
