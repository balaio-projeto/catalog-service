package br.com.balaio.catalog.peca.application;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TextoBusca {
    private static final int MAXIMO_PALAVRAS = 8;
    private static final Set<String> PALAVRAS_DE_LIGACAO = Set.of("a", "o", "as", "os", "de", "da", "do", "das",
            "dos", "e", "em", "na", "no", "nas", "nos", "um", "uma", "com", "para", "por");

    private TextoBusca() {
    }

    public static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public static List<TermoBusca> termos(String consulta, Collection<ArtesaoPublico> artesaos) {
        if (consulta == null || consulta.isBlank()) {
            return List.of();
        }
        var palavras = Arrays.stream(normalizar(consulta).trim().split("\\s+")).distinct().toList();
        var relevantes = palavras.stream().filter(palavra -> !PALAVRAS_DE_LIGACAO.contains(palavra)).toList();
        var nomesPorSlug = artesaos.stream()
                .filter(artesao -> artesao.slug() != null && !artesao.slug().isBlank())
                .collect(Collectors.toMap(ArtesaoPublico::slug, TextoBusca::identificacao, (a, b) -> a + " " + b));
        return (relevantes.isEmpty() ? palavras : relevantes).stream()
                .limit(MAXIMO_PALAVRAS)
                .map(palavra -> termo(palavra, nomesPorSlug))
                .toList();
    }

    static List<String> variantes(String palavra) {
        String singular = singular(palavra);
        return singular.equals(palavra) ? List.of(palavra) : List.of(palavra, singular);
    }

    static String singular(String palavra) {
        int tamanho = palavra.length();
        if (tamanho < 4 || !palavra.endsWith("s")) {
            return palavra;
        }
        if (tamanho >= 5 && (palavra.endsWith("oes") || palavra.endsWith("aes"))) {
            return palavra.substring(0, tamanho - 3) + "a";
        }
        if (tamanho >= 5 && Stream.of("ais", "eis", "ois", "res", "zes", "ses").anyMatch(palavra::endsWith)) {
            return palavra.substring(0, tamanho - 2);
        }
        if (palavra.endsWith("ns")) {
            return palavra.substring(0, tamanho - 2) + "m";
        }
        return palavra.substring(0, tamanho - 1);
    }

    private static TermoBusca termo(String palavra, Map<String, String> nomesPorSlug) {
        var variantes = variantes(palavra);
        var artesaos = nomesPorSlug.entrySet().stream()
                .filter(artesao -> variantes.stream().anyMatch(artesao.getValue()::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return new TermoBusca(variantes, artesaos);
    }

    private static String identificacao(ArtesaoPublico artesao) {
        return normalizar(Stream.of(artesao.nome(), artesao.atelie())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" ")));
    }
}
