package br.com.balaio.catalog.peca.domain;

import java.util.List;

public record ReferenciasCatalogo(List<String> tecnicas, List<String> territorios,
        List<String> categorias, List<String> tipos, List<String> etiquetas) {
    public ReferenciasCatalogo {
        tecnicas = List.copyOf(tecnicas);
        territorios = List.copyOf(territorios);
        categorias = List.copyOf(categorias);
        tipos = List.copyOf(tipos);
        etiquetas = List.copyOf(etiquetas);
    }
}
