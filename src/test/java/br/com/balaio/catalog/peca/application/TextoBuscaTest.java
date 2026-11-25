package br.com.balaio.catalog.peca.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TextoBuscaTest {
    private static final List<ArtesaoPublico> ARTESAOS = List.of(
            new ArtesaoPublico("artesao-1", "Severina Ramos", "Ateliê Barro Vivo"),
            new ArtesaoPublico("artesao-2", "João do Pífano", "Oficina Sertão"),
            new ArtesaoPublico("artesao-3", null, null));

    @Test
    void separaPalavrasSemAcentoSemMaiusculasEDescartaPalavrasDeLigacao() {
        var termos = TextoBusca.termos("  Jarra de CERÂMICA  ", List.of());

        assertThat(termos).extracting(TermoBusca::variantes)
                .containsExactly(List.of("jarra"), List.of("ceramica"));
    }

    @Test
    void mantemPalavrasDeLigacaoQuandoSaoTudoQueFoiDigitado() {
        assertThat(TextoBusca.termos("de", List.of())).extracting(TermoBusca::variantes)
                .containsExactly(List.of("de"));
    }

    @Test
    void consultaVaziaNaoGeraTermos() {
        assertThat(TextoBusca.termos(null, ARTESAOS)).isEmpty();
        assertThat(TextoBusca.termos("   ", ARTESAOS)).isEmpty();
    }

    @Test
    void associaCadaPalavraAosArtesaosCujoNomeOuAtelieContemAPalavra() {
        var termos = TextoBusca.termos("Ateliê barro vivo severina joao", ARTESAOS);

        assertThat(termos).extracting(TermoBusca::artesaos).containsExactly(
                Set.of("artesao-1"), Set.of("artesao-1"), Set.of("artesao-1"), Set.of("artesao-1"),
                Set.of("artesao-2"));
    }

    @Test
    void reduzPluraisComunsParaTambemEncontrarOSingular() {
        assertThat(TextoBusca.singular("jarras")).isEqualTo("jarra");
        assertThat(TextoBusca.singular("flores")).isEqualTo("flor");
        assertThat(TextoBusca.singular("artesanais")).isEqualTo("artesana");
        assertThat(TextoBusca.singular("papeis")).isEqualTo("pape");
        assertThat(TextoBusca.singular("leoes")).isEqualTo("lea");
        assertThat(TextoBusca.singular("jardins")).isEqualTo("jardim");
        assertThat(TextoBusca.singular("mas")).isEqualTo("mas");
        assertThat(TextoBusca.singular("barro")).isEqualTo("barro");
        assertThat(TextoBusca.variantes("giboes")).containsExactly("giboes", "giba");
        assertThat(TextoBusca.variantes("vaso")).containsExactly("vaso");
    }

    @Test
    void limitaQuantidadeDePalavras() {
        assertThat(TextoBusca.termos("a1 a2 a3 a4 a5 a6 a7 a8 a9 a10", List.of())).hasSize(8);
    }
}
