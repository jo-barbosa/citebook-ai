# citebook-ai

Ask questions about a course's PDFs and get answers that cite their source (file and page).

citebook-ai is a retrieval-augmented generation (RAG) service. You upload a course's PDFs, it splits them into chunks, embeds them and stores them in pgvector. A question is answered only from the most relevant excerpts, and every citation in the answer is checked against the excerpts that were actually retrieved. The same engine is exposed through a REST API (for an Angular UI) and an MCP server (for external assistants such as Claude Desktop).

> **Status:** work in progress. Document ingestion (upload, chunking, embedding, storage), course listing, retrieval and the retrieval evaluation work today. Question answering with citations, the MCP tools and the Angular UI are next.

## Features

| Area | Status |
| --- | --- |
| Upload a PDF to a course (idempotent, replaces changed files) | Done |
| List courses with their document counts | Done |
| Page-aware chunking with overlap | Done |
| Embeddings stored in pgvector, filtered by course | Done |
| Errors as RFC 9457 Problem Details, OpenAPI / Swagger UI | Done |
| Answers with checked citations (file + page) | Planned |
| Read-only MCP tools | Planned |
| Retrieval evaluation (hit rate@k, MRR) as a test | Done |
| Angular UI | Planned |

## Design choices

**Hand-written RAG core.** Spring AI supplies the building blocks (`VectorStore`, `DocumentReader`, `EmbeddingModel`, `ChatModel`, `@Tool`). Chunking, prompt construction, citation checking and evaluation are written in this project's own code, not delegated to Spring AI advisors, so each step can be read and explained without the framework.

**Chunking** (`PageChunker`). Each page is chunked on its own, so every chunk maps to exactly one page and a citation can point to it. Chunks are up to 800 characters (chosen with the retrieval evaluation) with about 15% overlap. A cut prefers a paragraph break, then a sentence end, and only then a hard cut, and it never falls in the first half of a chunk. Pages with almost no text are skipped.

**Idempotent ingestion.** A document is identified by course and file name and fingerprinted with SHA-256. Uploading the same file again changes nothing (`200`). Uploading a changed file with the same name replaces the old chunks and metadata in a single transaction (`201`).

**Course-filtered vector search.** All courses share one table and one HNSW index. With a filter, pgvector first reads the nearest chunks from the index (about 40 by default) and only then drops those of other courses, so a small course could get no results although it has relevant material. A Flyway migration and the connection pool turn on iterative index scans (`hnsw.iterative_scan = strict_order`), which keep reading the index until `k` chunks of the course are found (up to a scan limit). The same migration adds a GIN index on the metadata for the course and document filters.

**Prompt-injection hygiene** (`PromptBuilder`). Retrieved excerpts go into the prompt inside `<excerpt>` tags with their number, file and page, and the system prompt states that excerpts are data, not instructions. All text that is not ours (chunk text, file names, the question) is escaped as XML text, so a PDF or a question containing `</excerpt>` cannot close its tag or forge an excerpt with a fake source. This protects the prompt's structure; an instruction written as plain text still reaches the model, which is why citations are also checked against the excerpts actually sent (planned).

**Hexagonal architecture.** The domain (`course`, `document`, `chunk`, `citation`) is plain Java with no framework types. The REST controllers and MCP tools are two inbound adapters calling the same use cases; pgvector, the PDF reader and the LLM are outbound adapters behind ports.

```
io.github.jo_barbosa.citebook
├── domain/            model and RAG logic, plain Java
├── application/
│   ├── ports/in/      use cases, commands, views
│   ├── ports/out/     ports for storage, PDF reading, LLM
│   └── services/      use case implementations
└── infrastructure/
    ├── adapters/in/   web (REST) and mcp
    ├── adapters/out/  pgvector, persistence, pdf
    └── config/
```

## Tech stack

- Java 25, Spring Boot 4.1, Spring AI 2.0, Maven
- PostgreSQL 16 + pgvector (HNSW index, cosine distance, iterative index scans), schema managed by Flyway
- Embeddings: Ollama with `bge-m3` (1024 dimensions), running locally
- Chat model: OpenAI-compatible API through OpenRouter
- springdoc-openapi, Actuator + Prometheus metrics
- Tests: JUnit 5, Testcontainers; CI on GitHub Actions

## Getting started

### Prerequisites

- Java 25
- Docker (Docker Desktop with WSL2 integration works)
- An [OpenRouter](https://openrouter.ai) API key

### Configure

Copy the example file and set your key:

```bash
cd backend
cp .env.example .env   # .env is git-ignored
```

API keys are read from the environment only and never committed.

### Run

```bash
cd backend
./mvnw spring-boot:run
```

Spring Boot's Docker Compose support starts Postgres + pgvector and Ollama from `backend/compose.yaml` and connects to them automatically. Flyway creates the schema on startup.

The first time, pull the embedding model into the Ollama container while the app is running:

```bash
docker compose exec ollama ollama pull bge-m3
```

Run with the `dev` profile (`-Dspring-boot.run.profiles=dev`) to log prompts and completions. It is off by default because prompts contain course excerpts.

### Use

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Upload a PDF to a course (the course is created on first upload; codes are 1 to 30 lowercase letters, digits or hyphens):

```bash
curl -F file=@notes.pdf http://localhost:8080/api/courses/my-course/documents
```

```json
{ "documentId": "…", "pages": 12, "chunks": 31, "status": "INDEXED" }
```

| Status | Meaning |
| --- | --- |
| `201` | Indexed (`INDEXED`) or replaced (`REPLACED`) |
| `200` | Same file already indexed (`ALREADY_INDEXED`) |
| `400` | Invalid course code or missing file |
| `413` | File larger than 50 MB |
| `422` | Not a PDF, or a PDF without extractable text |
| `503` | Embedding service unavailable |

List the courses and how many documents each has:

```bash
curl http://localhost:8080/api/courses
```

```json
[{ "code": "my-course", "documents": 1 }]
```

## Tests

```bash
cd backend
./mvnw verify
```

Docker must be running: integration tests start a real Postgres + pgvector through Testcontainers. Tests use a fake embedding model, so no Ollama or API key is needed. The same command runs in CI on every push that touches `backend/`.

### Retrieval evaluation

```bash
cd backend
./mvnw verify -Dgroups=eval -DexcludedGroups=
```

Ingests the evaluation corpus with the real `bge-m3` embedding model (Ollama in a Testcontainers container), runs the 25 questions and 5 off-topic questions through the search use case and reports hit rate@1/3/5/8, MRR and the similarity scores in `backend/target/eval-report.md`. The test fails if hit rate@5 or MRR drops below a floor, or if an off-topic question gets an excerpt. No LLM and no API key are involved. The first run downloads `bge-m3` (about 1.2 GB) and saves a local Docker image, `citebook-ollama-bge-m3`, so later runs skip the download. The default build excludes this test. In GitHub Actions it runs in its own workflow, `eval.yml`: on demand and on pushes to `main` that change chunking, search or the evaluation, with the report in the job summary.

Chunk size comparison (2026-11-09; `bge-m3`, overlap 15%, no score threshold, 25 questions):

| Chunk size | Chunks | hit rate@1 | hit rate@3 | hit rate@5 | hit rate@8 | MRR (depth 8) |
| --- | --- | --- | --- | --- | --- | --- |
| 800 | 847 | 0.52 | 0.76 | 0.88 | 0.96 | 0.660 |
| 1600 | 453 | 0.36 | 0.88 | 0.88 | 1.00 | 0.613 |
| 3200 | 322 | 0.44 | 0.84 | 0.92 | 0.96 | 0.638 |

Chosen: chunk size 800, k 5, score threshold 0.53. k 5 was preferred to 8: one more question (0.96 vs 0.88) is not worth 60% more prompt. The threshold sits between the scores of right excerpts and of 5 off-topic questions the book does not answer; with it, 5 of the 5 get no excerpt, so the answering step can reply "no relevant material" without calling the LLM.

Measured at the chosen configuration (2026-11-09): hit rate@1 0.52, hit rate@3 0.76, hit rate@5 0.88, hit rate@8 0.96, MRR 0.660 (over search depth 8); 5 of 5 off-topic questions get no excerpt; lowest score of a relevant excerpt 0.552.

### Live OpenRouter check

```bash
cd backend
./mvnw verify -Dgroups=live -DexcludedGroups=
```

Sends one question to OpenRouter with the configured base URL and model, to confirm the key, the endpoint and the model work together. It needs `OPENROUTER_API_KEY` (environment or `backend/.env`) and is skipped without it; Docker must be running, as for the other tests that start the application. The default build excludes it. Chat calls are retried once (2 attempts) with a 30 s timeout per attempt; rate limits, provider errors and timeouts return `503`.

## Data

Course PDFs are copyrighted and stay local, outside the repository. Tests, evaluation and demos use the author's own notes or public documents.

The retrieval evaluation uses *Open Data Structures (in Java)* by Pat Morin ([opendatastructures.org](https://opendatastructures.org), CC BY 2.5 CA), with 25 questions whose answers are on known pages, plus 5 off-topic questions. Corpus, questions and attribution: [`backend/src/test/resources/eval/`](backend/src/test/resources/eval/).

## License

[MIT](LICENSE)
