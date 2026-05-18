# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build the entire project
./mvnw clean compile

# Run tests (tests are centralized in ai-start module)
./mvnw test

# Run a single test class
./mvnw test -pl ai-start -Dtest=ClassName

# Run with a specific profile
./mvnw spring-boot:run -pl ai-start -Dspring-boot.run.profiles=dev

# Package the application
./mvnw clean package -DskipTests
```

## Project Architecture

This is an **AI-powered tour guide** application (Spring Boot 4.0.4, Java 21, Maven multi-module).

### Module Dependency Graph (top-down)

```
ai-start ──→ ai-framework ──→ ai-common
    │              │
    ├── biz-user ──┤
    ├── biz-admin ─┤
    ├── biz-attractions ─┤
    └── biz-ai ────┘
                       │
                  ai-api (shared DTOs + internal service interfaces)
```

- **ai-common** — Shared utilities: `JwtUtil`, `AliyunNlsTokenManager`, `Result<T>` (unified response wrapper), enums, Redis/MQ constants, `UserContext`/`AdminContext` (ThreadLocal).
- **ai-framework** — Infrastructure: MyBatis-Plus config (pagination, optimistic locking), JWT interceptors (`UserJwtInterceptor`, `AdminJwtInterceptor`), global exception handler (`@RestControllerAdvice`), RabbitMQ message converter, WebMVC/Swagger config.
- **ai-api** — Lightweight module containing only DTOs and internal service interfaces (e.g., `UserInternalService`, `AdminAttractionsInternalService`). This is the **dependency inversion layer** — biz modules implement interfaces declared here so they can consume each other's services without circular dependencies.
- **ai-biz** — Parent POM for four business submodules:
  - `biz-user` — User login/register/profile, tour history tracking.
  - `biz-admin` — Admin auth, analytics dashboard (emotion/focus stats, FAQ hot charts, chat trends).
  - `biz-attractions` — Attractions CRUD, file upload (Aliyun OSS via `spring-file-storage`), document/FAQ management.
  - `biz-ai` — Core AI: WebSocket chat handler, RAG vector search/storage, experience analysis, MCP client for Amap integration. Contains multiple `ChatClient` beans (VL model, DeepSeek, ETL qwen3.6-flash, Expert qwen3.6-plus).
- **ai-start** — Entry point (`@SpringBootApplication`), `application.yml` (context-path `/ai-project`, port 8080), and `application-{dev,prod}.yml` profiles.

### Layered Pattern Within Each Biz Module

```
controller → service (interface) → service/impl → mapper (MyBatis-Plus)
                ↕
           converter (MapStruct: Entity ↔ DTO ↔ VO)
```

- Services extend `ServiceImpl<Mapper, Entity>` from MyBatis-Plus, inheriting CRUD methods.
- Controllers return `Result<T>` (code: 1=success, 400=client error, 500=server error).
- `@Valid` on DTOs triggers validation; `MethodArgumentNotValidException` is caught by the global handler.

### Authentication Flow

1. Login/register endpoints (`/v1/users/login`, `/v1/admins/login`, etc.) are **excluded** from interceptors.
2. All other paths under `/v1/users/**` and `/v1/admins/**` go through JWT interceptors.
3. Token is stored in Redis with key `user:login_token:{uuid}` or `admin:login_token:{uuid}`, with 7-day expiry.
4. On each request, the interceptor parses the Bearer token, checks Redis TTL, and sets the user/admin ID into `UserContext`/`AdminContext` (ThreadLocal, cleared on `afterCompletion`).

### RAG / Document Pipeline

```
Upload file → Aliyun OSS → RabbitMQ RESULT_QUEUE
  → MarkdownDocumentReader → TokenTextSplitter (800-char chunks, table protection)
  → Redis VectorStore (prefix: "attraction:", index: attraction_index)
  → MySQL metadata record
```

- `VectorSearchService` handles similarity search at inference time.
- Hot FAQ: missed user questions accumulate in Redis Sets; periodic tasks (`FaqEvolutionTask`) process them into standard Q&A.

### WebSocket AI Chat

- `AiChatHandler` (extends `AbstractWebSocketHandler`) manages persistent connections.
- Supports text messages, image frames (Base64), and real-time audio via Alibaba NLS speech recognition.
- Two ChatClient paths: `vlGuideChatClient` (VL model with image) and `dsGuideChatClient` (DeepSeek text-only).
- Dynamic prompt assembled from: user profile (Redis), hot FAQ match (Redis/MySQL), or RAG document context.
- Conversation memory: JDBC-persisted `MessageWindowChatMemory` (10 messages), conversation ID = `attractionId:userId`.

### Scheduled Tasks

- `@EnableScheduling` is on the main application class. Key tasks:
  - `ExperienceAnalysisTask` — periodic user sentiment/emotion analysis.
  - `FaqEvolutionTask` — evolves hot questions into standard FAQ entries.
  - `FlushFaqStatsTask` — flushes FAQ statistics to MySQL.

## API Documentation

Swagger UI available at `/ai-project/swagger-ui/index.html` when running. Annotated with `@Tag` and `@Operation` from SpringDoc 3.0.2.

## External Dependencies

- **Alibaba DashScope** — LLM API (OpenAI-compatible endpoint at `dashscope.aliyuncs.com/compatible-mode`)
- **Alibaba NLS** — Real-time speech recognition/transcription
- **Alibaba OSS** — File/object storage (bucket: guying60, endpoint: oss-cn-guangzhou)
- **MySQL** — Primary database (`ai_guide`)
- **Redis** — Caching, chat memory, vector store, hot FAQ tracking
- **RabbitMQ** — Async messaging for document vectorization, FAQ saving, tour history
- **Amap MCP** — Maps/POI integration via Spring AI MCP client

## Security Note

`application-dev.yml` and `application-prod.yml` contain hardcoded credentials (Alibaba Cloud access keys, API keys, database/Redis/RabbitMQ passwords). Never commit these files to a public repository. Consider moving secrets to environment variables or an external vault.
