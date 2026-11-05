package br.com.balaio.catalog.peca.application;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DadosPeca(String nome, String tecnica, String territorio, String categoria, String tipo,
        List<String> historia, BigDecimal preco, String disponibilidade, BigDecimal prazoProducaoDias,
        String situacao, List<FotoRecebida> fotos, String inativadoEm) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FotoRecebida(String id, String nome, String url) {
    }
}
