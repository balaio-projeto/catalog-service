package br.com.balaio.catalog.peca.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.springframework.data.jpa.domain.Specification;

import br.com.balaio.catalog.peca.application.FiltroCatalogo;
import br.com.balaio.catalog.peca.application.TermoBusca;
import jakarta.persistence.criteria.Predicate;

final class PecaSpecifications {
    private PecaSpecifications() {
    }

    static Specification<PecaJpaEntity> catalogo(FiltroCatalogo filtro) {
        Specification<PecaJpaEntity> spec = (root, query, criteria) -> criteria.and(
                criteria.equal(root.get("situacao"), "publicada"),
                criteria.isNull(root.get("inativadoEm")));

        for (TermoBusca termo : filtro.termosDaBusca()) {
            spec = spec.and(casaComTermo(termo));
        }

        spec = igualSeInformado(spec, "tecnica", filtro.tecnica());
        spec = igualSeInformado(spec, "territorio", filtro.territorio());
        spec = igualSeInformado(spec, "categoria", filtro.categoria());
        spec = igualSeInformado(spec, "tipo", filtro.tipo());
        spec = igualSeInformado(spec, "disponibilidade", filtro.disponibilidade());

        if (Boolean.TRUE.equals(filtro.desconto())) {
            spec = spec.and((root, query, criteria) -> criteria.greaterThan(
                    root.<BigDecimal>get("desconto"), BigDecimal.ZERO));
        }

        if (filtro.precoMin() != null) {
            spec = spec.and((root, query, criteria) -> criteria.greaterThanOrEqualTo(
                    root.<BigDecimal>get("precoComDesconto"), filtro.precoMin()));
        }
        if (filtro.precoMax() != null) {
            spec = spec.and((root, query, criteria) -> criteria.lessThanOrEqualTo(
                    root.<BigDecimal>get("precoComDesconto"), filtro.precoMax()));
        }

        return spec;
    }

    private static Specification<PecaJpaEntity> igualSeInformado(
            Specification<PecaJpaEntity> atual, String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return atual;
        }
        return atual.and((root, query, criteria) -> criteria.equal(root.get(campo), valor));
    }

    private static Specification<PecaJpaEntity> casaComTermo(TermoBusca termo) {
        return (root, query, criteria) -> {
            var textoBusca = criteria.lower(root.get("textoBusca"));
            var alternativas = new ArrayList<Predicate>();
            for (String variante : termo.variantes()) {
                alternativas.add(criteria.like(textoBusca, "%" + escaparCuringas(variante) + "%", '\\'));
            }
            if (!termo.artesaos().isEmpty()) {
                alternativas.add(root.get("artesao").in(termo.artesaos()));
            }
            return criteria.or(alternativas.toArray(Predicate[]::new));
        };
    }

    private static String escaparCuringas(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
