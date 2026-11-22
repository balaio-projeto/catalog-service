# catalog-service

## Reserva interna de peça única (PI-190 / PI-243)

O catálogo é a autoridade da disponibilidade. `POST /internal/v1/reservas` recebe `{pedidoId,slug,chave}` e devolve `{dados:{id,pedidoId,slug,estado,expiraEm,preco,artesao,proprietarioId},erro:null}`. Preço e proprietário são registrados na reserva e permanecem estáveis nos replays. A chave de criação é estável por pedido e peça. A mesma chamada devolve a reserva ativa existente; pedido ou chave diferente recebe conflito. A reserva dura 15 minutos e retira a peça única da listagem. Peças de encomenda não usam esta operação.

Após confirmação confiável do pagamento pelo commerce, `POST /internal/v1/reservas/{id}/confirmar` recebe `{pedidoId,chave:"confirmar:<id>"}` e muda a reserva para `CONSUMIDA`. Uma reserva consumida não expira e mantém a peça indisponível; a repetição devolve o resultado persistido. Uma reserva já vencida não pode ser confirmada. `POST /internal/v1/reservas/{id}/liberar` recebe `{pedidoId,chave:"liberar:<id>"}` e libera tanto uma reserva aguardando pagamento quanto uma consumida após cancelamento/reembolso. A repetição devolve o resultado persistido. Uma liberação tardia de reserva expirada ou já liberada não toca numa reserva posterior da mesma peça. `GET /internal/v1/reservas/{id}` exige o cabeçalho `X-Balaio-Pedido-Id` e permite reconciliação pelo commerce. O processo de expiração libera reservas não consumidas vencidas em intervalos de um minuto; a criação também trata uma reserva vencida antes de tentar reservar novamente.

Todas as rotas internas exigem `X-Balaio-Service-Token` com o valor de `BALAIO_INTERNAL_TOKEN`, configurado fora do Git com pelo menos 32 caracteres. Sem esse segredo configurado, as rotas internas recusam chamadas. Elas não são encaminhadas pelo Gateway. O banco do commerce nunca deve atualizar a tabela `pecas` diretamente.

Enquanto uma peça está inativada por reserva, o PATCH do artesão retorna 409; ele usa o mesmo bloqueio de linha que as operações de reserva e liberação. Assim, uma edição concorrente não altera preço, situação ou disponibilidade de uma peça reservada.

### Garantias da reserva atômica (PI-243)

- A criação bloqueia a chave idempotente por advisory lock transacional e a peça por `SELECT ... FOR UPDATE`. O índice único parcial permite somente uma reserva RESERVADA/CONSUMIDA por peça. A reserva e a retirada da vitrine são gravadas na mesma transação do catálogo.
- Pedidos concorrentes para a mesma peça produzem um 201 e um 409. Requisições simultâneas com o mesmo pedido, slug e chave retornam 201 com o mesmo identificador, preço e vencimento, sem duplicar a reserva ou renovar seu prazo.
- A chave é vinculada ao pedido e ao slug: reutilizá-la para outra peça ou pedido retorna 409 `RESERVA_CONFLITANTE`, inclusive sob concorrência. O commerce usa `reservar:<pedidoId>:<slug>` e deve preservá-la ao repetir uma chamada após timeout.
- Uma reserva LIBERADA/EXPIRADA não é recriada pelo replay do pedido/chave antigos (409). Um novo pedido pode reservar após liberação ou expiração. Uma criação válida de outro pedido expira a reserva vencida da peça na própria transação, sem aguardar o agendador.
- `encomenda` e `disponivel` retornam 409 `RESERVA_NAO_APLICAVEL`, sem criar reserva ou inativar a peça. O commerce segue o fluxo de encomenda sem invocar a reserva exclusiva; consultar cotação não reserva a peça.
- Slug inexistente retorna 404; peça única não pública/inativa retorna 409; comando sem pedido, slug ou chave válidos retorna 400. Os erros preservam `{dados:null,erro:{codigo,mensagem}}`.

Os cenários de concorrência, idempotência, expiração, liberação e compatibilidade com o painel são exercitados por `ReservasPostgresTest` com PostgreSQL real em Testcontainers. Habilite `CATALOG_POSTGRES_TEST=true` antes de executar `mvn test`; sem a variável, essa classe é ignorada. A validação completa de pagamento/frete pertence à PI-190 e não é substituída pelos testes da reserva.

Serviço responsável pelos dados e pela persistência do catálogo de peças. O frontend chama a API pelo api-gateway.

## Recursos

- GET /api/v1/pecas: consulta pública de peças publicadas e ativas.
- Busca textual sem diferença entre maiúsculas e minúsculas ou acentos.
- Filtros por técnica, território, categoria, tipo, disponibilidade e desconto.
- Ordenação por preço final com desconto, avaliação ou ordem de publicação.
- Paginação no envelope compatível com a Fake API: dados, erro e paginacao.
- Flyway cria a tabela e carrega as 24 peças do seed atual do frontend.
- OpenAPI e Swagger UI.
- POST /api/v1/pecas e PATCH /api/v1/pecas/{slug}: criação e edição pelo artesão autenticado.
- Fotos ordenadas em `peca_fotos` no PostgreSQL, servidas em GET /api/v1/arquivos/pecas/{nome}.
- GET /api/v1/referencias: listas públicas de técnicas, territórios, categorias, tipos e etiquetas aceitas pelo formulário.
- GET /api/v1/pecas/{slug}: detalhe público de peça publicada e ativa; o dono autenticado também pode ler seu rascunho.
- GET /api/v1/artesaos/{slug}/pecas: listagem pública de peças publicadas e ativas, inclusive quando consultada pelo proprietário autenticado.
- GET /api/v1/artesaos/{slug}/pecas/painel: listagem privada das peças do artesão responsável, incluindo rascunhos, inativadas, reservadas e vendidas.

## Contrato

Parâmetros opcionais: q, tecnica, territorio, categoria, tipo, disponibilidade, desconto, ordenar, pagina e tamanho.

Valores de ordenar: preco-asc, preco-desc, avaliacao e recentes. O filtro desconto=true mostra só peças com desconto. Sem tamanho, a API devolve todos os resultados até 100; valores maiores também são limitados a 100. A página pedida é ajustada para o intervalo existente.

Exemplo de chamada:

~~~http
GET /api/v1/pecas?q=leao&ordenar=preco-asc
~~~

A resposta contém dados como lista de peças, erro como null e paginacao com pagina, tamanho, total e totalPaginas. Os campos de cada peça seguem o tipo Peca usado pelo frontend, inclusive historia como lista de textos.

`q` pesquisa por correspondência parcial, sem diferenciar maiúsculas/minúsculas nem acentos. Os campos pesquisados são nome, história, categoria, técnica, tipo e território. O texto indexável é normalizado e persistido na coluna `texto_busca`; o nome do artesão não é pesquisado, pois essa coluna contém atualmente apenas o slug/identificador do ateliê e não há projeção local do nome do perfil. Busca vazia ou em branco equivale à listagem sem termo. Caracteres `%`, `_` e `\` são tratados literalmente. A visibilidade (publicada e sem `inativado_em`) é a mesma das demais listagens públicas.

Uma futura busca por nome de artesão exige uma projeção local sincronizada por API ou eventos, com política de atualização e consistência definida; o catálogo não consulta diretamente o banco do profile-service.

### Busca textual (PI-179)

Criação e edição recompõem `texto_busca` com nome, slug do artesão, território, técnica, categoria, tipo e todos os parágrafos da história, na ordem persistida. A busca continua parcial, sem diferença de caixa ou acentos; não é pesquisa por palavras independentes nem por relevância. Materiais não fazem parte dos campos pesquisáveis deste contrato.

A migration Java `V9__reindexar_texto_busca_pecas` reconstrói o texto das peças existentes usando normalização Unicode NFD e `Locale.ROOT`, como a aplicação, sem exigir extensão PostgreSQL. Ela altera somente `texto_busca`: preserva conteúdo, autoria, preço e visibilidade. Valores de nomes de perfil embutidos no seed antigo deixam de compor a busca; permanece o slug persistido no catálogo. A V2 aplicada não foi editada. A V9 roda transacionalmente e faz atualizações em lotes de 200; em uma base maior, planeje a aplicação considerando o volume de escrita e bloqueio dos registros.

A consulta pública continua excluindo rascunhos e peças inativas/reservadas/vendidas. Termo vazio ou em branco mantém a listagem; ausência de resultados retorna 200 com `dados: []` e metadados de paginação. `%`, `_` e `\` são literais, não curingas. A aplicação não chama o profile-service para buscar peças.

Os testes verificam campos, acentos, caixa, termos parciais, caracteres literais, ausência de resultado e atualização/remoção da história. Com `CATALOG_POSTGRES_TEST=true`, também verificam o fluxo HTTP em PostgreSQL, visibilidade após edição/inativação e atualização de uma base V8 para V9. A normalização versionada da migration é independente das entidades atuais para preservar sua execução futura.

O contrato segue `GET /api/v1/pecas?q=` e compõe os filtros/paginação existentes. Nenhuma alteração no frontend é necessária para o parâmetro `q`; a UI ainda não deve prometer busca por nome de artesão. Não foi adicionado índice B-tree em `texto_busca`, pois ele não resolve o padrão `%termo%`; índices especializados exigem medição e decisão própria, sem introduzir Redis nesta entrega.

### Filtros, ordenação e paginação (PI-180)

Todos os filtros se combinam por interseção no PostgreSQL, com as mesmas condições na contagem e na consulta paginada. `tecnica`, `territorio`, `categoria`, `tipo` e `disponibilidade` comparam o valor exato persistido; valores omitidos ou em branco não restringem a consulta. Valor desconhecido retorna lista vazia. Somente `desconto=true` exige desconto positivo; `false` não significa "sem desconto", apenas não aplica esse filtro. Rascunhos e peças inativadas ficam fora de todos os resultados públicos.

| `ordenar` | Critério | Desempate |
| --- | --- | --- |
| Omitido ou desconhecido | Ordem do catálogo crescente | A ordem é única. |
| `recentes` | Ordem do catálogo decrescente | A ordem é única; não se baseia no momento da última edição. |
| `preco-asc` / `preco-desc` | Preço final após desconto, crescente/decrescente | Ordem do catálogo crescente. |
| `avaliacao` | Avaliação decrescente, valores ausentes no final | Ordem do catálogo crescente. |

O desempate mantém peças com preços iguais em ordem determinística entre páginas, enquanto os dados não mudarem. A paginação não mantém um snapshot entre requisições distintas: publicações/edições podem mudar o conjunto retornado.

O envelope contém `pagina`, `tamanho`, `total` e `totalPaginas`. `pagina` omitida, zero ou negativa resulta em 1; acima da última página é ajustada para a última. `tamanho` omitido ou zero usa o total filtrado entre 1 e 100; negativo é ajustado para 1 e acima de 100 para 100. Sem resultados, `pagina=1`, `total=0`, `totalPaginas=1`, preservando o tamanho solicitado após seus limites. Sem tamanho informado, a lista vazia usa `tamanho=1`. Esses ajustes preservam o comportamento existente; não são erros de validação. Números ou booleanos malformados retornam 400 `REQUISICAO_INVALIDA` no envelope padrão.

`FiltrosPaginacaoPostgresTest`, habilitado por `CATALOG_POSTGRES_TEST=true`, verifica interseção de todos os filtros, visibilidade, ordenações e desempates, avaliação ausente, limites de página, teto de 100 e parâmetros inválidos. Não há consulta a outro serviço nem necessidade de Redis. `precoMin` e `precoMax` continuam fora do contrato, conforme o card; não foram introduzidos nesta entrega.

### Escrita

`POST /api/v1/pecas` recebe `nome`, `tecnica`, `territorio`, `categoria`, `tipo`, `historia` (lista de textos), `preco`, `disponibilidade`, `prazoProducaoDias`, `situacao` e `fotos` (lista ordenada de `{id,nome,url}`). `situacao` aceita `rascunho` e `publicada`. O preço vai de zero a R$ 1.000.000,00; publicação exige preço maior que zero. `encomenda` publicada exige prazo inteiro de 1 a 120 dias; rascunho segue a regra da Fake API e pode ficar sem prazo. Fotos novas devem ser data URI JPG/PNG/WebP com até 5 MB por arquivo; até cinco fotos são aceitas. O serviço grava os bytes em tabela própria e retorna URLs estáveis. A primeira foto vira `imagem`. A resposta é `201 {"dados":{...},"erro":null}`. Slugs homônimos recebem sufixos `-2`, `-3` etc.

`PATCH /api/v1/pecas/{slug}` atualiza apenas peças ativas do proprietário; `inativadoEm` não pode ser alterado pelo corpo. `DELETE /api/v1/pecas/{slug}` faz inativação lógica e é idempotente para a peça já excluída. A exclusão de peça com reserva vigente ou consumida retorna 409; liberação ou expiração da reserva não reativa uma peça excluída pelo proprietário.

A migration V4 cria uma linha de controle bloqueada durante cada criação. A escolha do slug e de `ordem_catalogo` ocorre sob esse bloqueio transacional, inclusive entre instâncias do serviço, para que criações concorrentes com nomes iguais ou diferentes recebam identificadores distintos. `GET /referencias` e as regras de validação compartilham a mesma lista de valores.

`PATCH /api/v1/pecas/{slug}` recebe apenas campos alterados, aplica as mesmas regras e retorna `200`. Fotos existentes podem ser reenviadas com o mesmo `id` e `url` retornados antes. Campos de identidade, `papel`, `artesao`, `artesaoId`, `slug` e `id` no JSON são ignorados. A titularidade vem do `sub` do JWT, persistido em `proprietario_id`. Peças antigas do seed não têm titularidade e não podem ser editadas por essa rota.

As rotas de escrita exigem Bearer JWT RS256, `sub` UUID, `papel=artesao`, `iss`, `aud`, `iat`, `exp` e `jti`. O catálogo confere assinatura, emissor, audiência e validade com a chave pública. Na criação ele consulta `GET /api/v1/artesao/me` do profile-service usando o mesmo Bearer para obter `artesao-<UUID>` e confirmar o vínculo. O perfil atual ainda não contém cadastro de ateliê; esse slug é estável e provisório, e páginas públicas de ateliê precisarão integrar o perfil real. A listagem pública mantém apenas peças publicadas e ativas, sem condicionar publicação à curadoria de selo.

Erros seguem `{ "dados": null, "erro": { "codigo": "...", "mensagem": "...", "campo": "preco" } }`; `campo` só aparece quando há um campo específico. JWT ausente/inválido retorna 401; papel incorreto, 403; peça inexistente ou de outro dono, 404; validação, 400. O formulário atual exibe a mensagem no aviso geral e ainda não usa `erro.campo` para destacar o input.

## Organização do código

O catálogo usa uma arquitetura hexagonal leve para manter o contrato HTTP, as regras da aplicação e o PostgreSQL em fronteiras claras:

- `peca/api`: adaptador HTTP; recebe os parâmetros e transforma o resultado no envelope da Fake API.
- `peca/application/port/in`: caso de uso exposto ao controller.
- `peca/application/port/out`: contrato de consulta usado pela aplicação para buscar peças.
- `peca/application`: filtros, ordenação, paginação e implementação do caso de uso.
- `peca/domain`: modelo `Peca`, sem anotações de Spring ou JPA.
- `peca/persistence`: adaptador PostgreSQL, especificações de consulta, repositório Spring Data e entidade JPA.

O controller não conhece o repositório. A aplicação depende da porta `ConsultarPecasPort`, e o adaptador de persistência traduz essa consulta para Spring Data/JPA. Não há uma interface por classe: as portas representam os limites entre HTTP, aplicação e banco.

## Executar localmente

Requer JDK 25, PostgreSQL e o banco balaio_catalog criado antes da primeira inicialização.

| Variável | Padrão |
| --- | --- |
| CATALOG_SERVICE_PORT | 8081 |
| CATALOG_DB_URL | jdbc:postgresql://localhost:5432/balaio_catalog |
| CATALOG_DB_USERNAME | balaio_catalog_app |
| CATALOG_DB_PASSWORD | Obrigatória; forneça por variável de ambiente ou pelo Compose (`DB_PASSWORD`/`CATALOG_DB_PASSWORD` em `platform/.env`). Não há senha padrão no serviço. |
| JWT_PUBLIC_KEY_LOCATION | caminho `file:` para chave pública RSA PEM correspondente à chave privada do profile-service (obrigatório) |
| JWT_ISSUER / JWT_AUDIENCE | mesmos valores configurados no profile-service (obrigatórios) |
| PROFILE_SERVICE_URL | http://localhost:8082 |

No Windows, use .\mvnw.cmd spring-boot:run e .\mvnw.cmd test. Em Linux/macOS, use ./mvnw spring-boot:run e ./mvnw test.

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI JSON: http://localhost:8081/v3/api-docs
- Health: http://localhost:8081/actuator/health

V2__seed_pecas_from_fake_api.sql é um snapshot de Frontend/fake-api/seed.json. A Fake API orienta o contrato e os dados demonstrativos; migrações já aplicadas não devem ser editadas. Outros serviços acessam o catálogo pela API, nunca pelo banco deste serviço.

## Peças relacionadas (PI-184)

`GET /api/v1/pecas/{slug}/relacionadas?limite=3` retorna `{ "dados": [...], "erro": null }`, sem paginação.
Consulta somente peças publicadas e ativas da mesma técnica, exclui o slug atual e ordena por `ordem_catalogo` crescente, preservando a ordem da Fake API. O limite padrão é 3 (inclusive quando enviado 0); valores negativos são limitados a 1 e o máximo é 100. Valor não inteiro retorna 400.

Com poucas similares, retorna somente as disponíveis, inclusive lista vazia com HTTP 200. A Fake API e a página de detalhe atual não preenchem essa lista com outra técnica. Slug inexistente, rascunho ou peça inativada retorna 404 no envelope de erro do catálogo. A consulta usa exclusivamente o banco do catalog-service.

A busca aplica o limite no banco sem contagem de paginação.

## Disponibilidade e painel de peças (PI-196)

A mudança de condição usa o PATCH existente, identificado pelo proprietário persistido e pelo JWT, sem chamar o profile-service. Publicação `encomenda` exige prazo inteiro de 1 a 120 dias; a exceção de rascunho sem prazo é preservada. O mesmo bloqueio de linha serializa edição, reserva e liberação. Edição é recusada enquanto houver inativação ou reserva RESERVADA/CONSUMIDA, inclusive alteração de preço, disponibilidade ou situação.

Na rota privada `GET /api/v1/artesaos/{slug}/pecas/painel`, apenas o artesão proprietário com slug `artesao-<sub>` recebe as peças do painel com `estadoVenda`: disponivel, rascunho, inativada, reservada ou vendida. `inativadoEm` é retornado no objeto da peça; `vendidaEmPedido` aparece apenas na lista do dono quando a reserva está CONSUMIDA. A condição `disponibilidade` continua sendo disponivel/encomenda/unica e não é sobrescrita para representar venda. Liberação/expiração usam o estado persistido pelo fluxo de reservas existente; não há reativação antecipada pela leitura.

O painel consulta as reservas do proprietário em lote e lê peças e reservas em transação REPEATABLE_READ, evitando estados de instantes distintos. A rota pública permanece sem os dados privados da reserva. O gateway encaminha a rota privada e exige papel artesao, tanto em GET quanto em HEAD; o catálogo também exige JWT válido e verifica a posse. Sem autenticação a resposta é 401; papel incorreto ou slug de outro artesão retorna 403. Não há consulta ao profile-service nesse fluxo.

### Contrato para a próxima integração do frontend

- Consultar `/api/v1/artesaos/artesao-<sub>/pecas/painel` pelo gateway, com sessão ou Bearer, usando o envelope `{dados: [...], erro: null, paginacao: null}`. A lista privada não é paginada.
- Usar `estadoVenda` para os estados do painel e `vendidaEmPedido` somente como referência da venda; não inferir reserva/venda a partir da lista de pedidos. Peças reservadas e vendidas recusam edição com 409.
- Alterar condição por `PATCH /api/v1/pecas/{slug}`, por exemplo `{"disponibilidade":"encomenda","prazoProducaoDias":10}`. Encomenda publicada sem prazo retorna 400 com campo `prazoProducaoDias`; rascunho pode omiti-lo conforme o contrato de escrita existente. Ao sair de encomenda, o prazo é removido.
- Inativar por DELETE; `inativadoEm` é protegido no PATCH. Não há comando de reativação neste contrato.
- Manter `/api/v1/artesaos/{slug}/pecas` nas telas públicas; autenticar nessa rota não a transforma em painel.

O frontend ainda precisa migrar para a rota privada. Nenhum resumo de métricas ou integração da PI-247 foi adicionado. Não é necessária migration de produção: os dados já existem em pecas e reservas_peca.

## Ainda pendente em outras histórias

Cadastro completo de ateliê e sincronização dos dados de perfil.

## Referências e histórico (PI-215)

`GET /api/v1/referencias` retorna `{ dados: { tecnicas, territorios, categorias, tipos, etiquetas }, erro: null }`.
Os valores curados vêm da tabela `referencias_catalogo`, ordenados pelo campo `ordem` de cada grupo; grupos vazios retornam listas vazias. A migration V8 preserva os valores e a ordem anteriormente usados pela API. Novas opções são cadastradas por migrations de curadoria no catálogo, e passam a ser consultadas e aceitas na escrita sem alteração de listas Java ou cache. Cadastrar uma peça não cria uma opção arbitrária nem remove referências sem peças associadas.

`GET /api/v1/pecas/{slug}/historico` retorna o mesmo objeto e envelope do detalhe, preservando a ordem de `historia`, inclusive para peças inativadas. Conforme decisão de contrato PI-215, mantém o comportamento consumido pelas telas de pedidos e pela Fake API; retorna 404 para slug inexistente ou rascunho. Este endpoint consulta os dados atuais persistidos e não um log de versões. A decisão está registrada em `Frontend/docs/api.md`.

Os adaptadores existentes do frontend já consomem esses caminhos e formatos e o gateway já os encaminha ao catálogo. A validação ponta a ponta das telas com o ambiente real depende de execução integrada; os testes deste serviço validam os contratos HTTP e a persistência localmente.
