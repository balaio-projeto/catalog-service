package br.com.balaio.catalog.peca.application;

import java.math.BigDecimal;
import java.util.List;

public record FiltroCatalogo(
        String q,
        String tecnica,
        String territorio,
        String categoria,
        String tipo,
        String disponibilidade,
        Boolean desconto,
        BigDecimal precoMin,
        BigDecimal precoMax,
        List<TermoBusca> termos) {

    public FiltroCatalogo {
        termos = termos == null ? null : List.copyOf(termos);
    }

    public FiltroCatalogo(String q, String tecnica, String territorio, String categoria, String tipo,
            String disponibilidade, Boolean desconto, BigDecimal precoMin, BigDecimal precoMax) {
        this(q, tecnica, territorio, categoria, tipo, disponibilidade, desconto, precoMin, precoMax, null);
    }

    public FiltroCatalogo(String q, String tecnica, String territorio, String categoria, String tipo,
            String disponibilidade, Boolean desconto) {
        this(q, tecnica, territorio, categoria, tipo, disponibilidade, desconto, null, null);
    }

    public FiltroCatalogo comTermos(List<TermoBusca> novosTermos) {
        return new FiltroCatalogo(q, tecnica, territorio, categoria, tipo, disponibilidade, desconto, precoMin,
                precoMax, novosTermos);
    }

    public List<TermoBusca> termosDaBusca() {
        return termos != null ? termos : TextoBusca.termos(q, List.of());
    }
}
