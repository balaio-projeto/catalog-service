# Catalog Service

Serviço dono do catálogo de peças artesanais do Balaio. O gateway será a entrada HTTP do frontend; este serviço aplicará as regras do catálogo e persistirá os dados das peças.

## Estado atual

Há somente o esqueleto Spring Boot e dependências iniciais. Não existem controllers, entidades, migrações de negócio ou rotas de catálogo implementadas. As rotas abaixo são planejadas a partir da [documentação da API do frontend](https://github.com/lucaschavessf2/Balaio/blob/main/Frontend/docs/api.md) e da Fake API.

## Responsabilidades e limites

- Manter peças, dados de catálogo, disponibilidade e publicação.
- Oferecer busca, filtros, ordenação, paginação, detalhe e peças relacionadas.
- Fornecer referências usadas pelos filtros.
- Permitir ao artesão gerenciar as próprias peças, após definida a integração de identidade.
- Referenciar o perfil do artesão por ID/slug. Contas e perfis pertencem ao `profile-service`.
- Não incluir venda de matéria-prima nesta entrega.
- Não ler ou gravar diretamente no banco de outro microsserviço.

Perguntas sobre peças pertencem ao `community-service`; pedidos pertencem ao `commerce-service`.

## API planejada

Prefixo `/api/v1`. Nenhuma destas rotas está implementada ainda.

| Método e rota | Uso |
| --- | --- |
| `GET /pecas` | Busca com `q`, `tecnica`, `territorio`, `categoria`, `tipo`, `disponibilidade`, `desconto`, `ordenar`, `pagina`, `tamanho` |
| `GET /pecas/{slug}` | Detalhe público da peça |
| `GET /pecas/{slug}/relacionadas` | Peças relacionadas |
| `GET /pecas/{slug}/historico` | Dados preservados para pedidos antigos |
| `GET /artesaos/{slug}/pecas` | Peças de um ateliê |
| `GET /referencias` | Técnicas, territórios, categorias e tipos |
| `POST /pecas` | Cadastrar peça do artesão autenticado |
| `PATCH /pecas/{slug}` | Editar, publicar, voltar a rascunho ou inativar |
| `DELETE /pecas/{slug}` | Remover peça do próprio artesão |

A Fake API usa `recentes`, `preco-asc`, `preco-desc` e `avaliacao` como valores de ordenação. Respostas devem preservar o contrato do frontend: `{ "dados": ..., "erro": null }`, com `paginacao` nas listagens.

## Stack e desenvolvimento

- Java 25, Maven Wrapper, Spring Boot 4.1.1.
- Spring MVC, Spring Data JPA, Bean Validation, PostgreSQL, Flyway e Actuator.
- Requer JDK 25 e PostgreSQL. Use o repositório `platform` para infraestrutura local quando disponível; cada microsserviço mantém seus próprios dados.
- Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` com os valores locais do serviço. Não versione credenciais.
- Crie migrações em `src/main/resources/db/migration`, por exemplo `V1__create_pecas.sql`.
- O `application.yaml` atual define somente o nome do serviço; conexão e migrações de negócio ainda precisam ser implementadas.

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd package
```

Linux/macOS: use os mesmos comandos com `./mvnw`.

Com Actuator, a verificação de saúde padrão fica em `/actuator/health`. O teste atual só verifica o contexto; acrescente testes de contrato, regras e persistência ao implementar endpoints.

## OpenAPI / Swagger UI

Ainda não está no `pom.xml`. A decisão anterior não previa Swagger; a recomendação agora é adicionar OpenAPI e Swagger UI quando os primeiros controllers forem criados. Para Spring Boot 4, use springdoc 3.x; a versão estável consultada é 3.1.1: `org.springdoc:springdoc-openapi-starter-webmvc-ui`. URLs esperadas após configurar: `/swagger-ui/index.html` e `/v3/api-docs`.

## Referências

- [Contrato da API do frontend](https://github.com/lucaschavessf2/Balaio/blob/main/Frontend/docs/api.md)
- [Fake API](https://github.com/lucaschavessf2/Balaio/tree/main/Frontend/fake-api)
- [springdoc-openapi](https://springdoc.org/)
