# Paginação de Catálogo

Catálogo de filmes com 200 mil registros que expõe a mesma listagem por três estratégias de paginação diferentes, lado a lado, para comparar o comportamento de cada uma com volume real de dados.

| Estratégia | Rota | Como funciona |
|---|---|---|
| Offset | `/movies/offset` | `LIMIT`/`OFFSET` com `COUNT` total — permite "página 500 de 10.000" |
| Slice | `/movies/slice` | O mesmo, sem o `COUNT` — só informa se existe próxima página |
| Keyset | `/movies/keyset` | Avança por cursor opaco a partir do último registro lido |

Há ainda um endpoint de benchmark que mede as três em profundidades crescentes, para a diferença aparecer em números em vez de em teoria.

## Tecnologias e bibliotecas

| | |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5 |
| Persistência | Spring Data JPA, PostgreSQL 16 |
| Migrations | Flyway |
| Validação | Bean Validation |
| Build | Gradle Kotlin DSL (wrapper `gradlew`) |
| Testes | JUnit 5, Testcontainers |
| Apoio | Lombok |

## Pré-requisitos

- JDK 21 ou superior
- Docker

## Como rodar

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

A primeira subida roda as migrations e semeia 200 mil filmes — leva alguns segundos. A API fica em `http://localhost:8080`.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/movies/offset` | Paginação por offset, com total (`page`, `size`, `sort`) |
| `GET` | `/movies/slice` | Paginação por offset sem `COUNT` (`page`, `size`, `sort`) |
| `GET` | `/movies/keyset` | Paginação por cursor (`cursor`, `size`) |
| `GET` | `/movies/benchmark` | Compara offset e keyset em profundidades crescentes (`size`, `pages`) |
| `GET` | `/movies/collection-fetch-trap` | Mostra o efeito de paginar com `JOIN FETCH` de coleção |

Filtros aceitos nas três primeiras rotas: `title`, `genre`, `yearFrom`, `yearTo`, `minRating`.
Ordenação: `id`, `title`, `releaseDate` ou `rating`, no formato `campo,asc|desc`.

## Exemplos de uso

```bash
curl -s "localhost:8080/movies/offset?size=3"
```

```bash
curl -s "localhost:8080/movies/slice?size=3"
```

```bash
curl -s "localhost:8080/movies/keyset?size=3"
```

O cursor da próxima página vem no campo `nextCursor` da resposta anterior:

```bash
curl -s "localhost:8080/movies/keyset?size=3&cursor=MjAyNC0xMC0wM3wxNTk5OTk"
```

```bash
curl -s "localhost:8080/movies/offset?size=5&genre=Horror&minRating=9.5&yearFrom=2000&yearTo=2005"
```

```bash
curl -s "localhost:8080/movies/benchmark?size=20"
```

## Testes

```bash
./gradlew test
```

28 testes: 12 unitários, sobre o codec de cursor e a validação dos campos de ordenação, e 16 de integração contra um PostgreSQL em container.
