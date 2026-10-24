package br.com.balaio.catalog.peca;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.balaio.catalog.peca.domain.Peca;
import br.com.balaio.catalog.peca.persistence.PecaJpaEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class PecasControllerIntegrationTest {
    @Autowired
    private TestRestTemplate http;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void limparBanco() {
        transacao().executeWithoutResult(status ->
                entityManager.createQuery("delete from PecaJpaEntity").executeUpdate());
    }

    @Test
    void buscaIgnoraAcentosFiltraDescontoELimitaPagina() {
        persistir(peca("leao-imperial", "Leão Imperial", "1250", "20", 1, "publicada"));
        persistir(peca("leao-rascunho", "Leão rascunho", "500", "20", 2, "rascunho"));

        ResponseEntity<Map> resposta = http.getForEntity(
                "/api/v1/pecas?q=LEAO&desconto=true&tamanho=1&pagina=99", Map.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);

        Map corpo = resposta.getBody();

        assertThat(corpo)
                .containsKeys("dados", "erro", "paginacao");

        assertThat(corpo.get("dados")).isNotNull();
        assertThat(corpo.get("erro")).isNull();
        assertThat(corpo.get("paginacao")).isNotNull();
        List<Map> dados = (List<Map>) corpo.get("dados");
        assertThat(dados).hasSize(1);
        assertThat(dados.getFirst()).containsEntry("slug", "leao-imperial");
        Map paginacao = (Map) corpo.get("paginacao");
        assertThat(paginacao).containsEntry("pagina", 1).containsEntry("total", 1);
    }

    @Test
    void ordenaPeloPrecoComDesconto() {
        persistir(peca("preco-cheio", "Preço cheio", "80", "0", 1, "publicada"));
        persistir(peca("promocional", "Preço promocional", "100", "50", 2, "publicada"));

        ResponseEntity<Map> resposta = http.getForEntity("/api/v1/pecas?ordenar=preco-asc", Map.class);

        List<Map> dados = (List<Map>) resposta.getBody().get("dados");
        assertThat(dados.getFirst()).containsEntry("slug", "promocional");
    }

    @Test
    void parametroPaginaInvalidoRetornaErroPadronizado() {
        ResponseEntity<Map> resposta = http.getForEntity(
                "/api/v1/pecas?pagina=abc",
                Map.class
        );

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);

        Map corpo = resposta.getBody();
        assertThat(corpo).containsKeys("dados", "erro");
        assertThat(corpo).containsEntry("dados", null);
        assertThat(corpo).doesNotContainKey("paginacao");

        Map erro = (Map) corpo.get("erro");
        assertThat(erro)
                .containsEntry("codigo", "REQUISICAO_INVALIDA")
                .containsEntry("mensagem", "Dados inválidos.");
    }

    @Test
    void rotaInexistenteRetornaErroPadronizado() {
        ResponseEntity<Map> resposta = http.getForEntity(
                "/api/v1/rota-inexistente",
                Map.class
        );

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);

        Map corpo = resposta.getBody();
        assertThat(corpo).containsKeys("dados", "erro");
        assertThat(corpo).containsEntry("dados", null);
        assertThat(corpo).doesNotContainKey("paginacao");

        Map erro = (Map) corpo.get("erro");
        assertThat(erro)
                .containsEntry("codigo", "RECURSO_NAO_ENCONTRADO")
                .containsEntry("mensagem", "Recurso não encontrado.");
    }

    private void persistir(Peca peca) {
        transacao().executeWithoutResult(status -> {
            entityManager.persist(PecaJpaEntity.fromDomain(peca));
            entityManager.flush();
            entityManager.clear();
        });
    }

    private TransactionTemplate transacao() {
        return new TransactionTemplate(transactionManager);
    }

    private Peca peca(String slug, String nome, String preco, String desconto, int ordem, String situacao) {
        return new Peca(slug, nome, "artesao", "Agreste", "Cerâmica & Barro", "Decoração & Arte",
                "Esculturas & Bonecos", new BigDecimal(preco), new BigDecimal(desconto), "disponivel", null,
                List.of("História."), "/fotos/" + slug + ".svg", new BigDecimal("4.8"), 10, situacao, null, ordem);
    }
}
