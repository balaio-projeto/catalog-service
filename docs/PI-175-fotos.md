# PI-175 — fotos reais de peças

## Contrato

O cadastro (`POST /api/v1/pecas`) e a edição (`PATCH /api/v1/pecas/{slug}`)
recebem `fotos: [{id, nome, url}]`. Uma foto nova usa data URI em `url`;
uma foto mantida usa o mesmo `id` e a URL devolvida anteriormente pela API.
A ordem da lista define `ordem` e a primeira foto define `imagem`.
Omitir `fotos` no PATCH preserva a galeria; enviar `[]` remove a galeria.

São aceitas até cinco fotos JPEG, PNG ou WebP de até 5 MiB por arquivo.
Quantidade, tamanho, tipo e assinatura são validados antes de gravar arquivos.
O JWT identifica o artesão e a posse é validada pelos dados do catálogo.
Outro proprietário recebe 404, sem revelar a peça privada.

## Persistência e leitura

O bucket privado `balaio-media` guarda os arquivos sob
`catalog/{slug}/{uuid}.{extensao}`. A identidade `balaio-catalog` possui somente
Read/Write nesse prefixo; Write inclui exclusão no SeaweedFS.
Credenciais administrativas não são entregues ao catálogo nem ao navegador.

PostgreSQL guarda metadados, associação e `storage_key`. A migration V10 não
altera migrations anteriores e mantém leitura de binários legados; quando uma
foto legada é reenviada na galeria existente, o arquivo migra para S3 e o binário
é removido da linha. No ambiente local verificado não havia binários legados.

`GET /api/v1/arquivos/pecas/{uuid}.{extensao}` devolve o binário pelo catálogo,
gateway e proxy do frontend, sem expor endereço Docker ou credenciais.
A migration V11 cria uma fila persistente de limpeza. Objetos antigos são
agendados na transação da edição; uploads novos têm limpeza registrada para
rollback. Um worker tenta excluir até dez objetos por ciclo, verifica que não
há referências ativas e mantém itens com falha para nova tentativa. A exclusão
não bloqueia a resposta HTTP e a fila sobrevive ao reinício do catálogo.
As referências da galeria são atualizadas na transação do catálogo.

Storage indisponível devolve HTTP 503, com `dados: null` e
`erro.codigo: STORAGE_INDISPONIVEL`. Chamadas S3 têm limite total de oito
segundos por operação e quatro segundos por tentativa, sem expor detalhes do SDK.
Arquivo ausente continua retornando 404.

O cliente usa path style e envio com tamanho conhecido, desabilita
`Expect: 100-continue` tanto no transporte quanto no módulo S3 e usa checksums
adicionais somente quando exigidos pela operação. Esses parâmetros evitam
negociações/trailers extras no storage compatível S3; veja a
[documentação de checksums do SDK](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/s3-checksums.html).
Os ensaios comparam os bytes enviados e lidos. Após restart, aguardar os
healthchecks antes de repetir os comandos de gravação.

## Execução local

Dentro de `platform`, em PowerShell 7.2 ou superior:

```powershell
./storage/scripts/prepare-local-storage.ps1
docker compose -f docker-compose.yml -f docker-compose.storage.yml --profile extended --profile storage up -d --build --wait
```

`scripts/start-local.ps1` faz os dois passos sozinho. O preparador gera/preserva
o par `CATALOG_S3_ACCESS_KEY_ID`/`CATALOG_S3_SECRET_ACCESS_KEY` no `.env`
ignorado pelo Git, independente dos pares do administrador e do Profile, e o
`docker-compose.storage.yml` entrega esse par apenas ao `seaweedfs` e ao
`catalog-service`, com `CATALOG_S3_ENABLED=true`. Certificados devem ser
preparados pela conta Windows que usa o Docker. Configuração parcial ou
reutilizada de credenciais é recusada; não há rotação automática de segredos.

Configurações: `CATALOG_S3_ENABLED`, `CATALOG_S3_ENDPOINT`, `CATALOG_S3_REGION`,
`CATALOG_S3_BUCKET`, `CATALOG_S3_ACCESS_KEY_ID`, `CATALOG_S3_SECRET_ACCESS_KEY`.
O endpoint Docker é `http://seaweedfs:8333`, região `us-east-1`.
O volume `seaweedfs-data` preserva os objetos; não apagar volumes para atualizar.

## Validação reproduzível

```powershell
./storage/scripts/test-catalog-storage.ps1
```

`test-catalog-storage.ps1` (no `platform`) valida a identidade `balaio-catalog`
na rede do Compose: leitura, escrita e exclusão só em `catalog/*`, recusa nos
demais prefixos, ACL, listagem, criação de bucket e acesso anônimo, além de
credenciais fora dos logs. O roteiro HTTP abaixo foi executado com `curl` contra o
gateway da pilha local.

O roteiro cria dois artesãos e uma peça com identificador exclusivo, valida
uploads, conteúdo dos binários, ordem, capa, posse, substituição, exclusão no S3,
leitura após restart e recuperação após indisponibilidade controlada.
A limpeza remove os objetos pela API e apaga somente os registros temporários
identificados pelo UUID do ensaio; não usa contas reais.
`-MaxSizeFixture` aceita um PNG de exatamente 5 MiB para testar cinco arquivos
no limite e a recusa de arquivo com um byte a mais.

Em 22/11/2026, foram atualizados os containers de catálogo, perfil, gateway e
frontend, preservando os bancos com backups anteriores à atualização em
`platform/backups/`. Como as chaves JWT antigas já não existiam no host ou nos
containers, o perfil gerou novo par persistente em volumes. Sessões antigas
exigem novo login. Não foram alterados os fontes do perfil, gateway ou frontend.

Os ensaios HTTP usam a mesma URL e proxy consumidos pelas telas. Verificação
visual no navegador não foi concluída por indisponibilidade da ferramenta de
navegação; esses ensaios não substituem o aceite visual da tela.

Resultado final em 22/11/2026: suíte com 46 testes aprovados, incluindo
PostgreSQL. O roteiro integrado pela porta 4321 passou com cinco PNGs de
5.242.880 bytes, recusa com um byte excedente, checagens de posse, capa, ordem,
HTML do detalhe/edição, indisponibilidade, retomada após restart e exclusão
física dos objetos. Os sete containers da pilha estavam healthy; Flyway na V11;
fila de limpeza, binários legados e registros temporários dos ensaios zerados.
Nenhuma das credenciais S3 apareceu nos logs examinados do catálogo/storage.
