# catalog-service

## Reserva interna de peça única (PI-190)

O catálogo é a autoridade da disponibilidade. `POST /internal/v1/reservas` recebe `{pedidoId,slug,chave}` e devolve `{dados:{id,pedidoId,slug,estado,expiraEm,preco,artesao,proprietarioId},erro:null}`. Preço e proprietário são registrados na reserva e permanecem estáveis nos replays. A chave de criação é estável por pedido e peça. A mesma chamada devolve a reserva ativa existente; pedido ou chave diferente recebe conflito. A reserva dura 15 minutos e retira a peça única da listagem. Peças de encomenda não usam esta operação.

Após confirmação confiável do pagamento pelo commerce, `POST /internal/v1/reservas/{id}/confirmar` recebe `{pedidoId,chave:"confirmar:<id>"}` e muda a reserva para `CONSUMIDA`. Uma reserva consumida não expira e mantém a peça indisponível; a repetição devolve o resultado persistido. Uma reserva já vencida não pode ser confirmada. `POST /internal/v1/reservas/{id}/liberar` recebe `{pedidoId,chave:"liberar:<id>"}` e libera tanto uma reserva aguardando pagamento quanto uma consumida após cancelamento/reembolso. A repetição devolve o resultado persistido. Uma liberação tardia de reserva expirada ou já liberada não toca numa reserva posterior da mesma peça. `GET /internal/v1/reservas/{id}` exige o cabeçalho `X-Balaio-Pedido-Id` e permite reconciliação pelo commerce. O processo de expiração libera reservas não consumidas vencidas em intervalos de um minuto; a criação também trata uma reserva vencida antes de tentar reservar novamente.

As três rotas exigem `X-Balaio-Service-Token` com o valor de `BALAIO_INTERNAL_TOKEN`, configurado fora do Git com pelo menos 32 caracteres. Sem esse segredo configurado, as rotas internas recusam chamadas. Elas não são encaminhadas pelo Gateway. O banco do commerce nunca deve atualizar a tabela `pecas` diretamente.

Enquanto uma peça está inativada por reserva, o PATCH do artesão retorna 409; ele usa o mesmo bloqueio de linha que as operações de reserva e liberação. Assim, uma edição concorrente não altera preço, situação ou disponibilidade de uma peça reservada.

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
- GET /api/v1/artesaos/{slug}/pecas: listagem pública de peças publicadas e ativas; o dono autenticado com slug `artesao-<UUID>` também vê seus rascunhos.

## Contrato

Parâmetros opcionais: q, tecnica, territorio, categoria, tipo, disponibilidade, desconto, ordenar, pagina e tamanho.

Valores de ordenar: preco-asc, preco-desc, avaliacao e recentes. O filtro desconto=true mostra só peças com desconto. Sem tamanho, a API devolve todos os resultados até 100; valores maiores também são limitados a 100. A página pedida é ajustada para o intervalo existente.

Exemplo de chamada:

~~~http
GET /api/v1/pecas?q=leao&ordenar=preco-asc
~~~

A resposta contém dados como lista de peças, erro como null e paginacao com pagina, tamanho, total e totalPaginas. Os campos de cada peça seguem o tipo Peca usado pelo frontend, inclusive historia como lista de textos.

### Escrita

`POST /api/v1/pecas` recebe `nome`, `tecnica`, `territorio`, `categoria`, `tipo`, `historia` (lista de textos), `preco`, `disponibilidade`, `prazoProducaoDias`, `situacao` e `fotos` (lista ordenada de `{id,nome,url}`). `situacao` aceita `rascunho` e `publicada`. O preço vai de zero a R$ 1.000.000,00; publicação exige preço maior que zero. `encomenda` publicada exige prazo inteiro de 1 a 120 dias; rascunho segue a regra da Fake API e pode ficar sem prazo. Fotos novas devem ser data URI JPG/PNG/WebP com até 5 MB por arquivo; até cinco fotos são aceitas. O serviço grava os bytes em tabela própria e retorna URLs estáveis. A primeira foto vira `imagem`. A resposta é `201 {"dados":{...},"erro":null}`. Slugs homônimos recebem sufixos `-2`, `-3` etc.

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

## Ainda pendente em outras histórias

Cadastro completo de ateliê e sincronização dos dados de perfil. As referências atuais são um conjunto estático versionado conforme a Fake API, não um cadastro editável.
