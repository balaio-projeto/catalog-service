package br.com.balaio.catalog.peca.domain;

import java.util.List;

/** Valores públicos aceitos na escrita de peças, alinhados à Fake API. */
public final class ReferenciasCatalogo {
    public static final List<String> TECNICAS = List.of("Cerâmica & Barro", "Bordado & Renda (Renascença)",
            "Escultura em Madeira", "Couro Autoral", "Xilogravura");
    public static final List<String> TERRITORIOS = List.of("Mata Norte", "Cariri", "Pajeú", "Petrolina",
            "Vale do Ipojuca", "Caruaru", "Alto do Moura, Caruaru");
    public static final List<String> CATEGORIAS = List.of("Decoração & Arte", "Casa & Mesa", "Vestuário & Acessórios");
    public static final List<String> TIPOS = List.of("Jarras & Vasos", "Esculturas & Bonecos", "Mesa & Cozinha",
            "Toalhas & Rendas", "Couro: Bolsas, Cintos & Gibões", "Gravuras & Cordéis");
    public static final List<String> ETIQUETAS = List.of("ceramica", "renascenca", "xilogravura",
            "couro", "madeira", "passoapasso");

    private ReferenciasCatalogo() {
    }
}
