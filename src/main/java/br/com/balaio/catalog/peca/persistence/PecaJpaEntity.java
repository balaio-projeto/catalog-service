package br.com.balaio.catalog.peca.persistence;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.balaio.catalog.peca.domain.Peca;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pecas")
public class PecaJpaEntity {
    @Id
    @Column(name = "slug", length = 160)
    private String slug;

    @Column(nullable = false, length = 240)
    private String nome;

    @Column(nullable = false, length = 160)
    private String artesao;

    @Column(nullable = false, length = 160)
    private String territorio;

    @Column(nullable = false, length = 160)
    private String tecnica;

    @Column(nullable = false, length = 160)
    private String categoria;

    @Column(nullable = false, length = 160)
    private String tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal desconto;

    @Column(nullable = false, length = 32)
    private String disponibilidade;

    @Column(name = "prazo_producao_dias")
    private Integer prazoProducaoDias;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] historia;

    @Column(nullable = false, length = 500)
    private String imagem;

    @Column(precision = 3, scale = 2)
    private BigDecimal avaliacao;

    @Column(name = "total_avaliacoes")
    private Integer totalAvaliacoes;

    @Column(nullable = false, length = 32)
    private String situacao;

    @Column(name = "inativado_em")
    private OffsetDateTime inativadoEm;

    @Column(name = "ordem_catalogo", nullable = false)
    private Integer ordemCatalogo;

    @Column(name = "texto_busca", nullable = false, columnDefinition = "text")
    private String textoBusca;

    @Column(name = "proprietario_id")
    private UUID proprietarioId;

    @Column(name = "criada_em")
    private OffsetDateTime criadaEm;

    @Formula("preco * (1 - desconto / 100.0)")
    private BigDecimal precoComDesconto;

    protected PecaJpaEntity() {
    }

    public static PecaJpaEntity fromDomain(Peca peca) {
        var entity = new PecaJpaEntity();
        entity.slug = peca.slug();
        entity.nome = peca.nome();
        entity.artesao = peca.artesao();
        entity.territorio = peca.territorio();
        entity.tecnica = peca.tecnica();
        entity.categoria = peca.categoria();
        entity.tipo = peca.tipo();
        entity.preco = peca.preco();
        entity.desconto = peca.desconto() == null ? BigDecimal.ZERO : peca.desconto();
        entity.disponibilidade = peca.disponibilidade();
        entity.prazoProducaoDias = peca.prazoProducaoDias();
        entity.historia = peca.historia().toArray(String[]::new);
        entity.imagem = peca.imagem();
        entity.avaliacao = peca.avaliacao();
        entity.totalAvaliacoes = peca.totalAvaliacoes();
        entity.situacao = peca.situacao();
        entity.inativadoEm = peca.inativadoEm();
        entity.ordemCatalogo = peca.ordemCatalogo();
        entity.proprietarioId = peca.proprietarioId();
        entity.criadaEm = peca.criadaEm();
        entity.textoBusca = normalizarTexto(String.join(" ", peca.nome(), peca.artesao(), peca.territorio(),
                peca.tecnica(), peca.categoria(), peca.tipo()));
        return entity;
    }

    public Peca toDomain() {
        return new Peca(slug, nome, artesao, territorio, tecnica, categoria, tipo, preco, desconto,
                disponibilidade, prazoProducaoDias, historia == null ? List.of() : Arrays.asList(historia.clone()),
                imagem, avaliacao, totalAvaliacoes, situacao, inativadoEm, ordemCatalogo,
                proprietarioId, criadaEm, List.of());
    }

    private static String normalizarTexto(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
