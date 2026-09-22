# Mecanismos de paginação — catálogo de filmes

Três estratégias de paginação implementadas sobre **o mesmo catálogo de 200 mil filmes**, para comparar não só como se escreve cada uma, mas onde cada uma quebra.

## Status

✅ Implementado, testado e medido.

## Stack

- Java 21 + Spring Boot 3.5
- PostgreSQL 16 + Spring Data JPA / Hibernate 6 + Flyway
- Gradle (Kotlin DSL) + wrapper `gradlew` (toolchain Java 21 resolvida automaticamente)
- Lombok nas entidades JPA; DTOs como records
- Testcontainers + JUnit 5 + AssertJ

## As três estratégias

| Endpoint | Estratégia | SQL de paginação | Custo por página |
|----------|-----------|------------------|------------------|
| `GET /movies/offset` | `Page` — offset + total | `LIMIT n OFFSET d` **+** `COUNT(*)` | cresce com a profundidade |
| `GET /movies/slice` | `Slice` — offset sem total | `LIMIT n+1 OFFSET d` | cresce com a profundidade |
| `GET /movies/keyset` | cursor | `WHERE (release_date, id) < (?, ?) LIMIT n` | constante |

Todas aceitam os mesmos filtros (`title`, `genre`, `yearFrom`, `yearTo`, `minRating`), e um teste de integração garante que as três devolvem **exatamente os mesmos itens na mesma ordem** — sem isso, comparar performance entre elas não significaria nada.

## Os números

Medidos nesta máquina, sobre 200 mil linhas, páginas de 20 itens (`GET /movies/benchmark?size=20`):

| Página | Linhas puladas | Query offset | `COUNT(*)` | Total offset | Query keyset | |
|-------:|---------------:|-------------:|-----------:|-------------:|-------------:|---|
| 0 | 0 | 1,25 ms | 14,52 ms | **15,77 ms** | **0,94 ms** | keyset 16,7x |
| 10 | 200 | 0,96 ms | 14,47 ms | **15,43 ms** | **1,06 ms** | keyset 14,5x |
| 100 | 2.000 | 2,53 ms | 14,52 ms | **17,04 ms** | **0,98 ms** | keyset 17,4x |
| 1.000 | 20.000 | 8,06 ms | 14,66 ms | **22,72 ms** | **0,91 ms** | keyset 24,9x |
| 5.000 | 100.000 | 35,95 ms | 14,91 ms | **50,86 ms** | **0,63 ms** | keyset 80,2x |
| 9.000 | 180.000 | 63,13 ms | 14,29 ms | **77,43 ms** | **0,89 ms** | keyset 86,9x |

Três leituras desse quadro:

1. **A query de offset cresce linearmente com a profundidade** — 1,25 ms na primeira página, 63 ms na página 9.000. O banco precisa localizar e descartar 180 mil linhas antes de devolver 20. O trabalho jogado no lixo *é* o custo.
2. **O keyset é plano** — entre 0,63 ms e 1,06 ms, sem relação com a profundidade. A comparação de tupla permite ao Postgres entrar direto no índice na posição do cursor e ler 20 linhas.
3. **O `COUNT(*)` é o custo escondido do `Page`** — 14 ms constantes, **independentes da página**. Nas primeiras páginas ele é o item mais caro da requisição: na página 10, o `COUNT` responde por 94% do tempo. Se a tela não mostra "de 200.000 resultados", esse tempo é desperdício puro — e é exatamente por isso que `Slice` existe.

### Metodologia

Honestidade sobre a medição importa mais que o número:

- As duas estratégias executam **a mesma query**, mudando só a cláusula de paginação. Comparar duas implementações diferentes mediria as implementações, não as estratégias.
- Cada medição faz um *warm-up* não cronometrado e depois 5 execuções cronometradas, reportando a **mediana** (não a média — um GC ou checkpoint em background distorceria a média para um lugar onde a requisição típica não está).
- O cursor usado pela query de keyset é resolvido **antes** e **não** entra no tempo dela, porque um cliente real já o tem da página anterior.
- Todas as execuções acontecem com cache de buffers quente, o que **favorece o offset**. Com cache frio a diferença é maior.

## Quando usar cada uma

| | Offset (`Page`) | Slice | Keyset |
|---|---|---|---|
| Pular para a página 57 | ✅ | ✅ | ❌ **impossível** |
| Mostrar "página 3 de 1.200" | ✅ | ❌ | ❌ |
| Custo constante em qualquer profundidade | ❌ | ❌ | ✅ |
| Estável sob escrita concorrente | ❌ | ❌ | ✅ |
| Ordenação por coluna arbitrária | ✅ | ✅ | ⚠️ exige índice e cursor por chave |
| Scroll infinito / feed | ⚠️ | ✅ | ✅ |
| API pública de alto volume | ❌ | ⚠️ | ✅ |

O keyset **não é melhor em tudo**: ele não permite acesso aleatório a páginas e obriga a ordenação a acompanhar um índice. Quando a interface é uma tabela com numeração de páginas e o volume é pequeno, offset é a escolha certa — mais simples e suficiente.

## A instabilidade do offset não é teórica

Dois testes de integração rodam o **mesmo cenário** — ler uma página, alguém inserir uma linha acima do limite da página, ler a página seguinte:

- `offsetShouldRepeatAnItemWhenARowIsInsertedAboveThePageBoundary` — o offset **repete** um item que o cliente já viu. A inserção empurrou todas as linhas uma posição para baixo, então `OFFSET 3` agora aponta para outro lugar. Nada está corrompido: a janela simplesmente passou a significar outra coisa.
- `keysetShouldNotRepeatAnItemWhenARowIsInsertedAboveThePageBoundary` — o keyset não repete nada. O cursor está ancorado em um *valor*, não em uma posição.

O primeiro teste é escrito para **falhar se o offset parar de errar**, porque todo o argumento em favor do keyset depende desse defeito ser real.

> Inserção causa repetição; remoção causa **omissão** — um item nunca é mostrado. A omissão é o caso mais grave, porque é silenciosa: ninguém percebe o registro que nunca apareceu.

## O cursor

Opaco para o cliente, Base64 de `releaseDate|id`:

```
MjAyNC0xMC0wM3wxNTk5OTk  →  2024-10-03|159999
```

Duas decisões deliberadas:

- **Chave composta.** O catálogo tem ~10 filmes por data de lançamento. Ordenar por uma coluna não única não define uma ordem total, e as linhas que compartilham a data do limite seriam puladas ou repetidas na virada de página. O par `(release_date, id)` é único, o que torna a ordem total e o limite da página exato.
- **Opacidade.** Base64 não esconde nada — qualquer um decodifica. É um recado de contrato: não construa cursores à mão nem dependa da chave de ordenação interna. Isso deixa a chave livre para mudar depois sem quebrar clientes.

## Armadilhas cobertas de propósito

Cada uma destas está implementada, comentada no código e coberta por teste:

**1. `COUNT(*)` mais caro que a busca** — 14 ms constantes contra 1 ms da query. Daí existir `Slice`.

**2. `Page` serializado direto na resposta** — `PageImpl` tem formato JSON que é detalhe de implementação e mudou entre versões (o Spring Boot 3.3+ chegou a emitir warning sobre depender dele). Expor `Page` transforma uma classe interna em contrato público. Aqui há envelopes próprios: `PageResponse`, `SliceResponse`, `CursorPageResponse`.

**3. Ordenação sem desempate** — `SortFields` sempre acrescenta o `id` como tie-breaker. Sem ele, linhas com o mesmo valor de ordenação voltam na ordem que o banco quiser, que muda entre execuções: o mesmo registro aparece em duas páginas e outro não aparece em nenhuma.

**4. Campo de ordenação vindo do cliente** — uma propriedade inexistente estoura `PropertyReferenceException` (um 500 causado por input do cliente) e uma propriedade real permite sondar o modelo interno. `SortFields` usa whitelist; campo inválido responde **400**, não 500.

**5. N+1 ao paginar** — `movies` tem `genre` LAZY de propósito: `EAGER` esconderia o problema no mapeamento em vez de resolvê-lo na query. `NPlusOneIntegrationTest` **conta os statements preparados** e exige exatamente 2 na paginação por offset (janela + `COUNT`), 1 no slice e 1 no keyset. Sem o entity graph seriam 52.

**6. `JOIN FETCH` de coleção com paginação** — a mais cara e a menos conhecida. `GET /movies/collection-fetch-trap?size=3` responde:

```json
{
  "requestedSize": 3,
  "returnedSize": 3,
  "entitiesLoadedByHibernate": 8000
}
```

Pediu 3 filmes, o Hibernate materializou **8.000 entidades**. Como a query faz fetch de uma coleção, aplicar `LIMIT` no SQL cortaria um filme no meio das suas reviews e devolveria um objeto incompleto — então o Hibernate **remove o `LIMIT` do SQL**, carrega todas as linhas e pagina em memória, registrando `HHH90003004` no log. A query *parece* paginada e não está. A saída: paginar os ids primeiro e buscar a coleção só para aquela página (duas queries), ou carregar a coleção com `@BatchSize`/subselect.

**7. Índice B-tree não serve para `LIKE '%termo%'`** — sem prefixo conhecido não há como descer na árvore. A migration `V3` cria um índice **GIN de trigramas** (`pg_trgm`), que é o que torna essa busca indexável.

**8. `ORDER BY` do keyset tem que casar com o índice** — `idx_movies_keyset` é `(release_date DESC, id DESC)`, exatamente a ordenação da query. Se divergir, o Postgres não percorre o índice e a vantagem do keyset desaparece.

**9. Seed antes dos índices** — a `V2` insere as 200 mil linhas e só a `V3` cria os índices: construir o índice uma vez sobre a tabela pronta é bem mais rápido que mantê-lo a cada insert. A `V3` termina com `ANALYZE`, sem o qual o planner pode ignorar os índices recém-criados e o benchmark mediria a coisa errada.

**10. `open-in-view` desligado** — com ele ligado o contexto de persistência fica aberto durante a renderização e o carregamento lazy passa despercebido. Desligado, o problema falha alto.

## Como rodar

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

A primeira subida roda as migrations e semeia 200 mil filmes (alguns segundos). A API responde em `http://localhost:8080`.

## Como rodar os testes

```bash
./gradlew test
```

28 testes: 12 unitários (codec de cursor, whitelist de ordenação) e 16 de integração contra um PostgreSQL real via Testcontainers.

### Nota sobre Testcontainers e Docker Engine recente

Se os testes de integração falharem com `client version 1.32 is too old. Minimum supported API version is 1.40`, a causa é o `docker-java` embutido no Testcontainers negociar a API 1.32, abaixo do mínimo aceito pelo Docker Engine 29+. Correção global, de uma linha:

```bash
echo 'api.version=1.44' > ~/.docker-java.properties
```

### Nota sobre o container nos testes

`IntegrationTestBase` usa o padrão **singleton container** — o container é iniciado num bloco `static` e nunca é entregue à extensão `@Testcontainers` do JUnit. Motivo: aquela extensão amarra o ciclo de vida do container à **classe de teste**, parando-o ao fim da classe e subindo um novo, em outra porta, para a classe seguinte. O Spring, por sua vez, cacheia o contexto entre classes — então a partir da segunda classe o pool de conexões aponta para o container que acabou de ser destruído e tudo falha com *connection refused*. Iniciar uma vez por JVM alinha os dois ciclos de vida. O Ryuk continua removendo o container quando a JVM encerra.

## Endpoints

| Método | Rota | Descrição |
|--------|------|-----------|
| GET | `/movies/offset` | Paginação por offset com total (`page`, `size`, `sort`) |
| GET | `/movies/slice` | Offset sem `COUNT` (`page`, `size`, `sort`) |
| GET | `/movies/keyset` | Paginação por cursor (`cursor`, `size`) |
| GET | `/movies/benchmark` | Compara offset e keyset em profundidades crescentes (`size`, `pages`) |
| GET | `/movies/collection-fetch-trap` | Demonstra a paginação em memória com `JOIN FETCH` de coleção |

Filtros aceitos nos três primeiros: `title`, `genre`, `yearFrom`, `yearTo`, `minRating`.
Campos de ordenação aceitos: `id`, `title`, `releaseDate`, `rating` (formato `campo,asc|desc`).

## Exemplo de uso

```bash
# Primeira página por offset
curl -s "localhost:8080/movies/offset?size=3"

# Mesma página sem pagar o COUNT
curl -s "localhost:8080/movies/slice?size=3"

# Primeira página por cursor
curl -s "localhost:8080/movies/keyset?size=3"

# Próxima página: use o nextCursor devolvido acima
curl -s "localhost:8080/movies/keyset?size=3&cursor=MjAyNC0xMC0wM3wxNTk5OTk"

# Filtros combinados
curl -s "localhost:8080/movies/offset?size=5&genre=Horror&minRating=9.5&yearFrom=2000&yearTo=2005"

# O benchmark
curl -s "localhost:8080/movies/benchmark?size=20"

# A armadilha do JOIN FETCH de coleção
curl -s "localhost:8080/movies/collection-fetch-trap?size=3"

# Erros esperados (400, não 500)
curl -s "localhost:8080/movies/offset?sort=passwordHash,desc"
curl -s "localhost:8080/movies/keyset?cursor=not-a-valid-cursor"
curl -s "localhost:8080/movies/offset?size=5000"
```
