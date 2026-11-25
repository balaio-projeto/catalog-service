package br.com.balaio.catalog.peca.application;

import java.util.List;
import java.util.Set;

public record TermoBusca(List<String> variantes, Set<String> artesaos) {
    public TermoBusca {
        variantes = List.copyOf(variantes);
        artesaos = Set.copyOf(artesaos);
    }
}
