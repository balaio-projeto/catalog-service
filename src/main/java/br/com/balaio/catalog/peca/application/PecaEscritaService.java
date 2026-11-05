package br.com.balaio.catalog.peca.application;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.catalog.peca.application.port.out.GerenciarPecasPort;
import br.com.balaio.catalog.peca.application.port.out.ResolverArtesaoPort;
import br.com.balaio.catalog.peca.application.port.in.CriarPecaUseCase;
import br.com.balaio.catalog.peca.application.port.in.AtualizarPecaUseCase;
import br.com.balaio.catalog.peca.domain.FotoPeca;
import br.com.balaio.catalog.peca.domain.Peca;
import br.com.balaio.catalog.peca.domain.ReferenciasCatalogo;

@Service
public class PecaEscritaService implements CriarPecaUseCase, AtualizarPecaUseCase {
    private static final Pattern DATA_URI = Pattern.compile("^data:image/(jpeg|png|webp);base64,([A-Za-z0-9+/]+={0,2})$");
    private static final Pattern URL_FOTO = Pattern.compile("^/api/v1/arquivos/pecas/[0-9a-f-]{36}\\.(jpg|png|webp)$");
    private final GerenciarPecasPort pecas;
    private final ResolverArtesaoPort artesaos;

    public PecaEscritaService(GerenciarPecasPort pecas, ResolverArtesaoPort artesaos) {
        this.pecas = pecas;
        this.artesaos = artesaos;
    }

    @Transactional
    @Override
    public Peca criar(DadosPeca dados, UUID contaId, String token) {
        var valido = validar(dados);
        String artesao = artesaos.resolver(contaId, token);
        pecas.bloquearCriacao();
        String base = slugificar(valido.nome());
        String slug = base;
        int sufixo = 2;
        while (pecas.existe(slug)) slug = base + "-" + sufixo++;
        var fotos = prepararFotos(valido.fotos(), List.of());
        var peca = montar(slug, artesao, contaId, valido, fotos, pecas.proximaOrdem(),
                OffsetDateTime.now(ZoneOffset.UTC), null, BigDecimal.ZERO, null, null);
        return pecas.salvar(peca, fotos);
    }

    @Transactional
    @Override
    public Peca atualizar(String slug, DadosPeca alteracoes, UUID contaId) {
        var atual = pecas.obter(slug).orElseThrow(() -> naoEncontrado());
        if (atual.proprietarioId() == null || !contaId.equals(atual.proprietarioId()))
            throw new ErroPecaException(403, "SEM_PERMISSAO", null, "Você não pode alterar esta peça.");
        if (alteracoes == null) alteracoes = new DadosPeca(null,null,null,null,null,null,null,null,null,null,null,null);
        if (alteracoes.inativadoEm() != null)
            throw ErroPecaException.invalido("inativadoEm", "Campo protegido.");
        var dados = validar(new DadosPeca(
                escolher(alteracoes.nome(), atual.nome()),
                escolher(alteracoes.tecnica(), atual.tecnica()),
                escolher(alteracoes.territorio(), atual.territorio()),
                escolher(alteracoes.categoria(), atual.categoria()),
                escolher(alteracoes.tipo(), atual.tipo()),
                escolher(alteracoes.historia(), atual.historia()),
                escolher(alteracoes.preco(), atual.preco()),
                escolher(alteracoes.disponibilidade(), atual.disponibilidade()),
                alteracoes.prazoProducaoDias() != null ? alteracoes.prazoProducaoDias()
                        : alteracoes.disponibilidade() != null && !alteracoes.disponibilidade().equals("encomenda")
                                ? null : atual.prazoProducaoDias() == null
                                        ? null : BigDecimal.valueOf(atual.prazoProducaoDias()),
                escolher(alteracoes.situacao(), atual.situacao()),
                alteracoes.fotos(), null));
        var fotos = dados.fotos() == null ? null : prepararFotos(dados.fotos(), atual.fotos());
        var peca = montar(slug, atual.artesao(), contaId, dados,
                fotos == null ? atual.fotos().stream().map(f -> new GerenciarPecasPort.FotoGravacao(f, null, null)).toList() : fotos,
                atual.ordemCatalogo(), atual.criadaEm(), atual.inativadoEm(), atual.desconto(),
                atual.avaliacao(), atual.totalAvaliacoes());
        return pecas.salvar(peca, fotos);
    }

    @Transactional
    @Override
    public Peca inativar(String slug, UUID contaId) {
        var atual = pecas.obter(slug).orElseThrow(this::naoEncontrado);
        if (atual.proprietarioId() == null || !contaId.equals(atual.proprietarioId()))
            throw new ErroPecaException(403, "SEM_PERMISSAO", null, "Você não pode alterar esta peça.");
        if (atual.inativadoEm() != null) return atual;
        var inativa = new Peca(atual.slug(), atual.nome(), atual.artesao(), atual.territorio(), atual.tecnica(),
                atual.categoria(), atual.tipo(), atual.preco(), atual.desconto(), atual.disponibilidade(),
                atual.prazoProducaoDias(), atual.historia(), atual.imagem(), atual.avaliacao(),
                atual.totalAvaliacoes(), atual.situacao(), OffsetDateTime.now(ZoneOffset.UTC),
                atual.ordemCatalogo(), atual.proprietarioId(), atual.criadaEm(), atual.fotos());
        return pecas.salvar(inativa, null);
    }

    private <T> T escolher(T recebido, T atual) { return recebido == null ? atual : recebido; }

    private Peca montar(String slug, String artesao, UUID dono, DadosPeca dados,
            List<GerenciarPecasPort.FotoGravacao> fotos, int ordem, OffsetDateTime criadaEm,
            OffsetDateTime inativadoEm, BigDecimal desconto, BigDecimal avaliacao, Integer totalAvaliacoes) {
        var metadados = fotos.stream().map(GerenciarPecasPort.FotoGravacao::foto).toList();
        String imagem = metadados.isEmpty() ? "/fotos/ImagemBase.webp" : metadados.getFirst().url();
        return new Peca(slug, dados.nome(), artesao, dados.territorio(), dados.tecnica(), dados.categoria(),
                dados.tipo(), dados.preco(), desconto, dados.disponibilidade(),
                dados.prazoProducaoDias() == null ? null : dados.prazoProducaoDias().intValueExact(),
                dados.historia(), imagem, avaliacao, totalAvaliacoes, dados.situacao(), inativadoEm, ordem,
                dono, criadaEm, metadados);
    }

    private DadosPeca validar(DadosPeca entrada) {
        if (entrada == null) throw ErroPecaException.invalido(null, "Dados da peça inválidos.");
        String nome = entrada.nome() == null ? "" : entrada.nome().strip();
        if (nome.isEmpty() || nome.length() > 120) throw ErroPecaException.invalido("nome", "Informe um nome de até 120 caracteres.");
        referencia(entrada.tecnica(), ReferenciasCatalogo.TECNICAS, "tecnica");
        referencia(entrada.territorio(), ReferenciasCatalogo.TERRITORIOS, "territorio");
        referencia(entrada.categoria(), ReferenciasCatalogo.CATEGORIAS, "categoria");
        referencia(entrada.tipo(), ReferenciasCatalogo.TIPOS, "tipo");
        var historia = entrada.historia() == null ? List.<String>of() : entrada.historia();
        if (historia.stream().anyMatch(t -> t == null) || String.join("\n", historia).length() > 4000)
            throw ErroPecaException.invalido("historia", "História da peça inválida.");
        String situacao = entrada.situacao() == null ? "publicada" : entrada.situacao();
        if (!Set.of("rascunho", "publicada").contains(situacao))
            throw ErroPecaException.invalido("situacao", "Situação da peça inválida.");
        BigDecimal preco = entrada.preco() == null ? BigDecimal.ZERO : entrada.preco();
        if (preco.compareTo(BigDecimal.ZERO) < 0 || preco.compareTo(new BigDecimal("1000000")) > 0
                || preco.stripTrailingZeros().scale() > 2
                || (situacao.equals("publicada") && preco.signum() == 0))
            throw ErroPecaException.invalido("preco", "Preço inválido para a situação da peça.");
        String disponibilidade = entrada.disponibilidade() == null ? "disponivel" : entrada.disponibilidade();
        if (!Set.of("disponivel", "unica", "encomenda").contains(disponibilidade))
            throw ErroPecaException.invalido("disponibilidade", "Disponibilidade inválida.");
        BigDecimal prazo = entrada.prazoProducaoDias();
        if ((prazo != null && (prazo.stripTrailingZeros().scale() > 0
                || prazo.compareTo(BigDecimal.ONE) < 0 || prazo.compareTo(new BigDecimal("120")) > 0))
                || (situacao.equals("publicada") && disponibilidade.equals("encomenda") && prazo == null))
            throw ErroPecaException.invalido("prazoProducaoDias", "Informe prazoProducaoDias entre 1 e 120 dias.");
        return new DadosPeca(nome, entrada.tecnica(), entrada.territorio(), entrada.categoria(), entrada.tipo(),
                historia, preco, disponibilidade, prazo, situacao, entrada.fotos(), null);
    }

    private void referencia(String valor, List<String> valores, String campo) {
        if (valor == null || !valores.contains(valor)) throw ErroPecaException.invalido(campo, "Escolha um valor válido para " + campo + ".");
    }

    private List<GerenciarPecasPort.FotoGravacao> prepararFotos(List<DadosPeca.FotoRecebida> recebidas,
            List<FotoPeca> anteriores) {
        if (recebidas == null) recebidas = List.of();
        if (recebidas.size() > 5) throw ErroPecaException.invalido("fotos", "Envie no máximo 5 fotos.");
        var ids = new HashSet<String>();
        var resultado = new ArrayList<GerenciarPecasPort.FotoGravacao>();
        int ordem = 0;
        for (var foto : recebidas) {
            if (foto == null || foto.id() == null || foto.id().isBlank() || foto.id().length() > 160
                    || !ids.add(foto.id()) || foto.url() == null)
                throw ErroPecaException.invalido("fotos", "Fotos inválidas.");
            String nome = foto.nome() == null ? "" : foto.nome().strip();
            if (nome.length() > 200) nome = nome.substring(0, 200);
            var matcher = DATA_URI.matcher(foto.url());
            String url;
            String formato = null;
            byte[] bytes = null;
            if (matcher.matches()) {
                formato = matcher.group(1);
                try { bytes = Base64.getDecoder().decode(matcher.group(2)); }
                catch (IllegalArgumentException exception) { throw ErroPecaException.invalido("fotos", "Foto em base64 inválida."); }
                if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024 || !assinaturaValida(formato, bytes))
                    throw ErroPecaException.invalido("fotos", "Cada foto deve ser JPG, PNG ou WebP de até 5 MB.");
                String extensao = formato.equals("jpeg") ? "jpg" : formato;
                url = "/api/v1/arquivos/pecas/" + UUID.randomUUID() + "." + extensao;
            } else {
                var anterior = anteriores.stream().filter(a -> a.id().equals(foto.id()) && a.url().equals(foto.url())).findFirst();
                if (anterior.isEmpty() || !URL_FOTO.matcher(foto.url()).matches())
                    throw ErroPecaException.invalido("fotos", "Foto existente inválida.");
                url = foto.url();
            }
            resultado.add(new GerenciarPecasPort.FotoGravacao(new FotoPeca(foto.id(), nome, url, ordem++), formato, bytes));
        }
        return resultado;
    }

    private boolean assinaturaValida(String formato, byte[] bytes) {
        if (formato.equals("jpeg")) return bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        if (formato.equals("png")) return bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
        return bytes.length > 12 && bytes[0] == 82 && bytes[1] == 73 && bytes[2] == 70 && bytes[3] == 70
                && bytes[8] == 87 && bytes[9] == 69 && bytes[10] == 66 && bytes[11] == 80;
    }

    private String slugificar(String nome) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        base = base.length() > 60 ? base.substring(0, 60).replaceAll("-$", "") : base;
        return base.isEmpty() ? "peca" : base;
    }

    private ErroPecaException naoEncontrado() {
        return new ErroPecaException(404, "RECURSO_NAO_ENCONTRADO", null, "Peça não encontrada.");
    }
}
