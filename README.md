# catalog-service

Serviço responsável pelos dados e pela persistência do catálogo de peças. O frontend chama a API pelo api-gateway.

## Implementado nesta primeira fatia

- GET /api/v1/pecas: consulta pública de peças publicadas e ativas.
- Busca textual sem diferença entre maiúsculas e minúsculas ou acentos.
- Filtros por técnica, território, categoria, tipo, disponibilidade e desconto.
- Ordenação por preço final com desconto, avaliação ou ordem de publicação.
- Paginação no envelope compatível com a Fake API: dados, erro e paginacao.
- Flyway cria a tabela e carrega as 24 peças do seed atual do frontend.
- OpenAPI e Swagger UI.

## Contrato

Parâmetros opcionais: q, tecnica, territorio, categoria, tipo, disponibilidade, desconto, ordenar, pagina e tamanho.

Valores de ordenar: preco-asc, preco-desc, avaliacao e recentes. O filtro desconto=true mostra só peças com desconto. Sem tamanho, a API devolve todos os resultados até 100; valores maiores também são limitados a 100. A página pedida é ajustada para o intervalo existente.

Exemplo de chamada:

~~~http
GET /api/v1/pecas?q=leao&ordenar=preco-asc
~~~

A resposta contém dados como lista de peças, erro como null e paginacao com pagina, tamanho, total e totalPaginas. Os campos de cada peça seguem o tipo Peca usado pelo frontend, inclusive historia como lista de textos.

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
| CATALOG_DB_PASSWORD | balaio (padrão local do application.yaml; prefira configurar uma senha própria) |

No Windows, use .\mvnw.cmd spring-boot:run e .\mvnw.cmd test. Em Linux/macOS, use ./mvnw spring-boot:run e ./mvnw test.

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI JSON: http://localhost:8081/v3/api-docs
- Health: http://localhost:8081/actuator/health

V2__seed_pecas_from_fake_api.sql é um snapshot de Frontend/fake-api/seed.json. A Fake API orienta o contrato e os dados demonstrativos; migrações já aplicadas não devem ser editadas. Outros serviços acessam o catálogo pela API, nunca pelo banco deste serviço.

## Fora deste escopo

Detalhe de peça, referências para filtros, cadastro/edição, autenticação e sincronização contínua com profile-service serão implementados em etapas seguintes.
