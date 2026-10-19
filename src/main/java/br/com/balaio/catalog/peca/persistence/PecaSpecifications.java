package br.com.balaio.catalog.peca.persistence;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import br.com.balaio.catalog.peca.application.FiltroCatalogo;

final class PecaSpecifications {
    private PecaSpecifications() {
    }

    static Specification<PecaJpaEntity> catalogo(FiltroCatalogo filtro) {
        Specification<PecaJpaEntity> spec = (root, query, criteria) -> criteria.and(
                criteria.equal(root.get("situacao"), "publicada"),
                criteria.isNull(root.get("inativadoEm")));

        if (filtro.q() != null && !filtro.q().isBlank()) {
            String termo = normalizarTexto(filtro.q().trim())
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            spec = spec.and((root, query, criteria) -> criteria.like(
                    criteria.lower(root.get("textoBusca")), "%" + termo + "%", '\\'));
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

        return spec;
    }

    private static Specification<PecaJpaEntity> igualSeInformado(
            Specification<PecaJpaEntity> atual, String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return atual;
        }
        return atual.and((root, query, criteria) -> criteria.equal(root.get(campo), valor));
    }

    private static String normalizarTexto(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
