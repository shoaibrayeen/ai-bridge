# AIBridge

A Quarkus-based LLM Gateway that manages multiple LLM providers, standardizes I/O to the OpenAI Chat Completions format, and provides multi-tenant routing, rate pacing, failover, and a built-in admin UI.

## Prerequisites

| Tool        | Version    | Notes                                                    |
|-------------|------------|----------------------------------------------------------|
| Java        | 17 or 21   | Amazon Corretto, Temurin, or Oracle JDK. **See note below**|
| Maven       | 3.9+       | Used for building the backend                            |
| PostgreSQL  | 14+        | Stores LLM configs and providers                         |
| Redis       | 7+         | Only needed when `CACHE_MODE=redis`                      |
| Node.js     | 22 LTS     | Only required if building the Angular UI                 |
| Docker      | 24+        | Optional; used for `docker compose` and the `Dockerfile` |

> **JDK version matters.** Quarkus 3.17.5 ships a Byte Buddy that supports up to
> Java 23. Building on a newer JDK (e.g. Corretto 25) fails during Quarkus
> augmentation with `Java 25 (69) is not supported by the current version of Byte
> Buddy`. Point `JAVA_HOME` at a JDK 17 or 21 install before building:
>
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
> ```

## Project Structure

```
ai-bridge/
  pom.xml                          # Maven build (Quarkus 3.17, Java 17)
  Dockerfile                       # Multi-stage image (Angular UI + Quarkus fast-jar)
  .dockerignore                    # Keeps build output and secrets out of the image
  docker-compose.yml               # PostgreSQL + Redis (+ optional app via the `app` profile)
  .github/workflows/ci.yml         # Tests + frontend build + image build on every push/PR
  .env.example                     # Template for local/container configuration
  .env                             # Your local copy (git-ignored, never commit)
  Architecture.md                  # Full architecture & design document (source of truth)
  architecture.html                # Rendered architecture reference with flow diagrams
  frontend/                        # Angular 21 SPA (admin + playground)
    package.json
    angular.json
    src/app/
  src/
    main/
      java/com/aibridge/           # Quarkus backend source
        adapter/                   # LLM provider adapters (OpenAI, Claude, Bedrock, WatsonX, Cerebras)
        config/                    # App configuration classes
        dto/                       # Request/Response DTOs (OpenAI format, admin, load-test)
        exception/                 # Custom exceptions + GlobalExceptionMapper
        filter/                    # Security headers, SSRF validator, correlation IDs
        model/                     # JPA entities (LlmConfig, LlmProvider, Feature)
        repository/                # Panache repositories
        resource/                  # JAX-RS REST endpoints
        service/                   # Business logic (pacing, encryption, validation, etc.)
      resources/
        application.properties     # Multi-profile config (dev / docker / prod)
        db/migration/
          V1__init_schema.sql      # Flyway: base schema
          V2__gateway_model_name.sql  # Flyway: gateway-unique model names
    test/
      java/com/aibridge/           # Unit tests (JUnit 5 + Mockito)
```

## Setup

**1. Create your environment file:**

```bash
cp .env.example .env
```

`.env` is git-ignored; `.env.example` is the committed template. Edit `.env` and
set at minimum:

| Variable | Why |
|----------|-----|
| `AIBRIDGE_ENCRYPTION_KEY` | AES-256 key for provider credentials — **exactly 32 characters** |
| `AIBRIDGE_AUTH_API_KEY` | The key clients exchange for a bearer token at `POST /api/auth/token` |

Generate a key with:

```bash
openssl rand -base64 32 | cut -c1-32
```

**2. Point `JAVA_HOME` at a JDK 17 or 21** (see the note under Prerequisites).

The `.env` file is read in two places:

- **Quarkus** loads a `.env` from the working directory as a config source
  (ordinal 295), so it overrides `application.properties` for `mvn quarkus:dev`
  and for `java -jar target/quarkus-app/quarkus-run.jar`.
- **Docker Compose** loads it via `env_file` for the `aibridge` service and for
  the Postgres credentials.

A property maps to an environment variable by uppercasing it and replacing every
non-alphanumeric character with `_` — `quarkus.datasource.jdbc.url` becomes
`QUARKUS_DATASOURCE_JDBC_URL`, `aibridge.encryption.key` becomes
`AIBRIDGE_ENCRYPTION_KEY`.

## Quick Start

### Option 1: Docker for Dependencies + Local Backend (Recommended for IntelliJ)

**1. Start PostgreSQL and Redis:**

```bash
docker compose up -d postgres redis
```

This starts:
- PostgreSQL on `localhost:5432` (database: `aibridge`, user/password: `postgres/postgres`)
- Redis on `localhost:6379`

Naming the two services keeps the `aibridge` container out of it, so you can run
the backend from IntelliJ or `mvn quarkus:dev` against them. See Option 4 to run
the app in Docker too.

**2. Build and run:**

```bash
# Backend only (fast iteration)
mvn quarkus:dev

# Or build the full JAR
mvn clean package -Dui.required=true
java -jar target/quarkus-app/quarkus-run.jar
```

**3. Access the service:**

| URL                          | Description                  |
|------------------------------|------------------------------|
| `http://localhost:8080/`     | Playground (Angular UI)      |
| `http://localhost:8080/admin`| Admin Dashboard              |
| `http://localhost:8080/admin/health` | Health Dashboard    |
| `http://localhost:8080/admin/load-test` | Load Testing    |
| `http://localhost:8080/q/health` | SmallRye health probes   |

### Option 2: Angular Dev Server (Frontend Hot Reload)

For frontend development with hot reload:

```bash
# Terminal 1: Start backend
docker compose up -d postgres redis
mvn quarkus:dev

# Terminal 2: Start Angular dev server
cd frontend
npm install
npm start
```

The Angular dev server runs at `http://localhost:4200` and proxies API calls to `http://localhost:8080` via `proxy.conf.json`.

### Option 3: Without Docker (Manual PostgreSQL + Redis)

If you run PostgreSQL and Redis natively (e.g., via Homebrew):

1. Create the database:
   ```sql
   CREATE DATABASE aibridge;
   ```
2. Verify that PostgreSQL is on `localhost:5432` and Redis on `localhost:6379`.
3. Run: `mvn quarkus:dev`

Flyway automatically creates all tables on first startup.

### Option 4: Full Docker Run (app + dependencies)

The `Dockerfile` is a three-stage build that needs nothing installed locally
except Docker — no JDK, no Maven, no Node:

1. `node:22-alpine` runs `npm ci && npm run build` for the Angular bundle
2. `maven:3.9-eclipse-temurin-17` builds the Quarkus fast-jar with the bundle in place
3. `eclipse-temurin:17-jre-jammy` runs it as the non-root `aibridge` user

```bash
cp .env.example .env          # if you haven't already
docker compose up -d --build
```

Compose starts Postgres and Redis first, waits for their healthchecks, then
starts the app on `http://localhost:8080` with `QUARKUS_PROFILE=docker` and the
in-network hostnames (`postgres`, `redis`) — these override whatever the `.env`
says, so the same `.env` works for local and containerised runs.

Useful commands:

```bash
docker compose logs -f aibridge   # tail app logs
docker compose ps                 # service status + health
docker compose down               # stop everything
docker compose down -v            # …and drop the data volumes
```

To build the image on its own:

```bash
docker build -t aibridge:local .

# API-only image (skips bundling the Angular UI)
docker build --build-arg BUILD_UI=false -t aibridge:api-only .
```

The image exposes `8080` and has a `HEALTHCHECK` on `/q/health/ready`. Tune the
heap with `JAVA_OPTS` (defaults to `-XX:MaxRAMPercentage=75.0`).

## Configuration Reference

Defaults live in `src/main/resources/application.properties`; override any of
them from `.env` or the real environment. Precedence, highest first:

1. System properties (`-D...`)
2. Environment variables
3. `.env` in the working directory
4. `application.properties` (including `%docker.` / `%prod.` profile entries)

### Database & Redis

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `quarkus.datasource.jdbc.url` | `QUARKUS_DATASOURCE_JDBC_URL` | `jdbc:postgresql://localhost:5432/aibridge` | PostgreSQL JDBC URL |
| `quarkus.datasource.username` | `QUARKUS_DATASOURCE_USERNAME` | `postgres` | DB username |
| `quarkus.datasource.password` | `QUARKUS_DATASOURCE_PASSWORD` | `postgres` | DB password |
| `quarkus.redis.hosts` | `QUARKUS_REDIS_HOSTS` | `redis://localhost:6379` | Redis URL; only used when `cache.mode=redis` |

### Docker Profile Overrides

Properties prefixed with `%docker.` are active when `QUARKUS_PROFILE=docker`:

| Property | Value |
|----------|-------|
| `%docker.quarkus.datasource.jdbc.url` | `jdbc:postgresql://postgres:5432/aibridge` |
| `%docker.quarkus.redis.hosts` | `redis://redis:6379` |

### AIBridge Custom Config

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `ui.required` | `UI_REQUIRED` | `true` | Serve the Angular UI; set `false` for API-only mode |
| `cache.mode` | `CACHE_MODE` | `in-memory` | `in-memory` or `redis`. Use `redis` when running more than one instance so pacing and auth tokens are shared |
| `aibridge.encryption.key` | `AIBRIDGE_ENCRYPTION_KEY` | *must set* | AES-256 encryption key (exactly 32 characters) |
| `aibridge.auth.api-key` | `AIBRIDGE_AUTH_API_KEY` | *must set* | Static key exchanged for a bearer token at `POST /api/auth/token` |
| `aibridge.auth.token-validity-minutes` | `AIBRIDGE_AUTH_TOKEN_VALIDITY_MINUTES` | `60` | Bearer token TTL |
| `aibridge.queue.timeout-ms` | `AIBRIDGE_QUEUE_TIMEOUT_MS` | `5000` | Max ms a request waits in pacing queue before failover |
| `aibridge.queue.poll-interval-ms` | `AIBRIDGE_QUEUE_POLL_INTERVAL_MS` | `50` | Polling interval for pacing slot acquisition |
| `aibridge.queue.max-depth` | `AIBRIDGE_QUEUE_MAX_DEPTH` | `1000` | Max queued requests per LLM config (prevents DoS) |
| `aibridge.load-test.max-parallel` | `AIBRIDGE_LOAD_TEST_MAX_PARALLEL` | `500` | Max parallel requests for load testing |
| `aibridge.allowed-provider-hosts` | `AIBRIDGE_ALLOWED_PROVIDER_HOSTS` | *(see file)* | Comma-separated allowlist for SSRF prevention |
| `aibridge.endpoint.allow-http` | `AIBRIDGE_ENDPOINT_ALLOW_HTTP` | `true` (dev) / `false` (prod) | Allow HTTP endpoints (should be false in prod) |
| `aibridge.log.level` | `AIBRIDGE_LOG_LEVEL` | `INFO` | Root log level |
| `aibridge.log.app-level` | `AIBRIDGE_LOG_APP_LEVEL` | `DEBUG` | Log level for `com.aibridge` |

### CORS

| Property | Env var | Default |
|----------|---------|---------|
| `quarkus.http.cors.origins` | `QUARKUS_HTTP_CORS_ORIGINS` | `http://localhost:4200` |

In production, set this to your actual frontend origin(s).

## API Endpoints

### Auth

```
POST /api/auth/token
Content-Type: application/json

{ "api_key": "<AIBRIDGE_AUTH_API_KEY>" }
```

Returns an opaque bearer token valid for `AIBRIDGE_AUTH_TOKEN_VALIDITY_MINUTES`
(default 60). The admin UI login screen calls this endpoint. Send the token as
`Authorization: Bearer <token>` on subsequent admin calls.

### Chat Completions (Client-facing)

```
POST /v1/chat/completions
Headers:
  X-Feature: <feature-name>       (required, unless model names a gateway config)
  X-Tenant-ID: <tenant-id>        (optional; omit for global config)
  X-Include-Lineage: true         (optional; returns the routing trail)
  Content-Type: application/json
```

Request body follows the [OpenAI Chat Completions format](https://platform.openai.com/docs/api-reference/chat).

There are two ways to choose which LLM answers:

| `model` in the payload | Routing |
|------------------------|---------|
| Absent, or a provider name like `gpt-4o` | Ignored. Routed by `X-Tenant-ID` × `X-Feature` through the full failover chain. |
| A gateway model name like `ai-bridge-2-claude-sonnet-6` | Pinned to that one config. `X-Feature` becomes optional, and **no failover** happens — you asked for a specific config. |

The response always echoes the gateway model name in `model`, so the value round-trips.

### Gateway Model Names

Every LLM config gets a service-wide unique name when it is created:

```
ai-bridge-<sequence>-<slugified model name>
```

The sequence counts up per slug, which is what lets the same underlying model be registered more than once — two Claude Sonnet 6 configs with different API keys, endpoints or rate limits become:

```
ai-bridge-1-claude-sonnet-6
ai-bridge-2-claude-sonnet-6
```

The name appears in the admin list and form, in `GET /admin/api/llm-configs` as `gateway_model_name`, and in `GET /v1/models`. Editing a config's model name assigns a new gateway name; editing anything else leaves it alone, so clients pinning a name are not broken by an unrelated edit.

### Models (OpenAI-compatible discovery)

```
GET /v1/models
GET /v1/models/{gateway-model-name}
Headers:
  X-Tenant-ID: <tenant-id>     (optional; scopes to that tenant plus global configs)
```

Returns the standard OpenAI envelope, so unmodified OpenAI SDKs and tools can enumerate what this gateway offers:

```json
{
  "object": "list",
  "data": [
    {
      "id": "ai-bridge-2-claude-sonnet-6",
      "object": "model",
      "created": 1767225600,
      "owned_by": "claude",
      "root": "claude-sonnet-6",
      "features": ["chat"]
    }
  ]
}
```

A config belonging to another tenant is reported as 404, identically to one that does not exist.

### Request Lineage

Every completion builds a trail of which configs were attempted, how each ended, and the tokens each consumed. It is **always logged** with the request id, and returned in the response body only when the request carries `X-Include-Lineage: true`:

```json
"x_aibridge_lineage": {
  "request_id": "8f3c…",
  "tenant_id": "acme",
  "routing": "feature",
  "chain_length": 2,
  "attempts": [
    { "sequence": 1, "gateway_model_name": "ai-bridge-1-gpt-4o", "provider": "OPENAI",
      "outcome": "QUEUE_TIMEOUT", "duration_ms": 5001 },
    { "sequence": 2, "gateway_model_name": "ai-bridge-1-claude-sonnet-6", "provider": "CLAUDE",
      "outcome": "SUCCESS", "duration_ms": 812,
      "prompt_tokens": 41, "completion_tokens": 96, "total_tokens": 137 }
  ],
  "total_duration_ms": 5813,
  "billed_total_tokens": 137
}
```

`billed_total_tokens` sums every attempt, not just the successful one — a failover that burned tokens upstream is still visible.

| Outcome | Advances the chain? |
|---------|---------------------|
| `SUCCESS` | No — terminal |
| `EMPTY_RESPONSE` | No — returned to the caller as 200 |
| `QUEUE_TIMEOUT` | Yes |
| `RATE_LIMITED` | Yes |
| `UNAVAILABLE` | Yes |
| `NO_ADAPTER` | Yes |

### Admin API

| Method | Path | Description |
|--------|------|-------------|
| `GET`    | `/admin/api/llm-providers` | List all providers |
| `POST`   | `/admin/api/llm-providers` | Create a provider |
| `PUT`    | `/admin/api/llm-providers/{id}` | Update a provider |
| `DELETE`  | `/admin/api/llm-providers/{id}` | Delete a provider |
| `GET`    | `/admin/api/llm-configs` | List configs (filterable: `?tenant_id=&feature=&provider_id=`) |
| `GET`    | `/admin/api/llm-configs/{id}` | Get single config (credentials masked) |
| `POST`   | `/admin/api/llm-configs` | Create config (validates LLM first) |
| `PUT`    | `/admin/api/llm-configs/{id}` | Update config (re-validates) |
| `DELETE`  | `/admin/api/llm-configs/{id}` | Soft-delete (deactivate) |
| `POST`   | `/admin/api/llm-configs/test` | Dry-run validation without saving |
| `GET`    | `/admin/api/features` | List distinct feature names |
| `POST`   | `/admin/api/load-test` | Start a load test (returns 202 + runId) |
| `GET`    | `/admin/api/load-test/{runId}` | Poll load test results |
| `GET`    | `/admin/api/load-test` | List recent test runs |
| `GET`    | `/admin/api/health` | DB + Redis health with latency |

### Auth-protected paths

`ApiAuthFilter` requires a bearer token on everything under `/v1/*` and `/admin/api/*`. Only `POST /api/auth/token` is open.

## Building

### Backend Only (No UI)

```bash
mvn clean package -DskipTests
```

### Frontend Only

```bash
cd frontend
npm install
npm run build      # outputs to src/main/resources/META-INF/resources/
```

### Backend + Frontend (Bundled)

```bash
mvn clean package -DskipTests -Dui.required=true
```

`-Dui.required=true` activates the `build-frontend` Maven profile, which
downloads Node 22 into `target/frontend-node`, runs `npm install`, and runs
`npm run build`. Without it, Maven packages whatever is already in
`src/main/resources/META-INF/resources/` — and that directory is git-ignored, so
a **fresh clone has no UI bundle**. Build the frontend at least once (either via
`-Dui.required=true`, via `cd frontend && npm run build`, or via the Dockerfile)
before running with `ui.required=true`.

The packaged JAR is at `target/quarkus-app/quarkus-run.jar`.

### Docker Image

```bash
docker build -t aibridge:local .
```

Builds the UI and the backend inside the image — no local JDK, Maven, or Node
needed.

### Running the Service

```bash
# 1. Start dependencies (PostgreSQL + Redis)
docker compose up -d postgres redis

# 2a. Dev mode (hot reload, no JAR needed)
mvn quarkus:dev

# 2b. Or run the packaged JAR
java -jar target/quarkus-app/quarkus-run.jar
```

## Running Tests

```bash
# Unit tests only (no integration tests, no running DB required)
mvn clean test

# One class
mvn test -Dtest=GatewayModelNameServiceTest

# Generate JaCoCo coverage report (opens at target/site/jacoco/index.html)
mvn clean test jacoco:report
```

Every dependency is mocked with Mockito — repository/DAO calls, the cache, and the LLM provider
adapters — so the suite needs no database, no Redis, and makes no network calls. It runs in a few
seconds.

What the suite covers:

| Area | Class | Covers |
|------|-------|--------|
| Gateway model names | `GatewayModelNameServiceTest` | Slugification of every provider name shape, sequence allocation, two configs of one model, re-assignment rules |
| Routing | `ChatCompletionServiceTest` | Model-pinned vs feature routing, the full failover chain, empty responses, chain exhaustion |
| Lineage | `ChatCompletionServiceTest`, `CompletionLineageTest` | One record per attempt, outcome per failure mode, token totals across attempts, log formatting, JSON shape |
| Model discovery | `ModelsResourceTest` | OpenAI envelope, tenant scoping, cross-tenant isolation, malformed headers |
| Config resolution | `ConfigResolverServiceTest` | Precedence chain, cache hit/miss, gateway-name lookup, tenant isolation |
| Admin API | `AdminLlmConfigResourceTest` | Create/update/delete, live validation before persist, credential re-encryption, gateway-name assignment |
| Adapters | `*AdapterTest` | Request/response translation and auth per provider, with the HTTP call mocked |

### CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to `main`, every pull
request, and on demand. Three jobs: backend unit tests on JDK 17 (with the JaCoCo report uploaded
as an artifact, and surefire reports uploaded on failure), the Angular build on Node 22, and a
Docker image build gated on both.

## Building for Production

```bash
# Build backend + UI
mvn clean package -Dui.required=true

# Build backend only (API-only mode)
mvn clean package
```

The packaged JAR is at `target/quarkus-app/quarkus-run.jar`.

### Production Checklist

- Set `AIBRIDGE_ENCRYPTION_KEY` to a strong 32-character secret (env var or Vault — never in `application.properties`)
- Set `AIBRIDGE_AUTH_API_KEY` to a strong secret; the default `CHANGE_ME_IN_PRODUCTION` grants anyone a token
- Run with `QUARKUS_PROFILE=prod`
- Set `CACHE_MODE=redis` when running more than one instance (in-memory pacing and tokens are per-process)
- Set `%prod.aibridge.endpoint.allow-http=false` (already configured)
- Set `QUARKUS_HTTP_CORS_ORIGINS` to your frontend domain
- Keep `.env` out of version control and out of images (`.gitignore` and `.dockerignore` already exclude it)
- Use TLS for PostgreSQL and Redis connections
- Use a least-privilege DB user (Flyway migrations can use a separate privileged user)
- Review `aibridge.allowed-provider-hosts` for your provider allowlist

## OWASP Dependency Check

```bash
mvn verify -Psecurity-scan
```

Scans all transitive dependencies for known CVEs.

## Troubleshooting

| Symptom | Cause / Fix |
|---------|-------------|
| `java.lang.IllegalArgumentException: Java 25 (69) is not supported by the current version of Byte Buddy` during `mvn package` | Your JDK is too new for Quarkus 3.17.5. Build with JDK 17 or 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` |
| `http://localhost:8080/` returns 404 or a blank page | The Angular bundle was never built. Run `mvn clean package -Dui.required=true`, or `cd frontend && npm install && npm run build`, or set `UI_REQUIRED=false` for API-only mode |
| App fails to start with a Flyway/connection error | PostgreSQL isn't up, or the JDBC URL is wrong. `docker compose up -d`, then check `QUARKUS_DATASOURCE_JDBC_URL` |
| Redis connection errors on startup | `CACHE_MODE=redis` without a reachable Redis. Either start Redis or set `CACHE_MODE=in-memory` |
| `Invalid API key` from `POST /api/auth/token` | `AIBRIDGE_AUTH_API_KEY` in `.env` doesn't match the key you're sending |
| Encryption failures on config save | `AIBRIDGE_ENCRYPTION_KEY` is not exactly 32 characters |
| App container can't reach `postgres`/`redis` | Start it through Compose (`docker compose up -d`) so it joins the same network |

## Technology Stack

- **Quarkus 3.17** — RESTEasy Reactive, Hibernate ORM with Panache, Flyway, Redis, SmallRye Health
- **Java 17** — Language and runtime
- **PostgreSQL 16** — Persistent storage
- **Redis 7** — Distributed rate pacing, caching, auth token storage
- **Angular 21** — Admin UI and playground (conditionally built)
- **Node.js 22 LTS** — Angular build toolchain

## Supported LLM Providers

| Provider | Auth Type | Adapter |
|----------|-----------|---------|
| OpenAI | API Key | `OpenAiAdapter` — near pass-through |
| Cerebras | API Key | `CerebrasAdapter` — OpenAI-compatible |
| Claude (Anthropic) | API Key | `ClaudeAdapter` — maps messages format |
| WatsonX (IBM) | IAM Token Exchange | `WatsonXAdapter` — IAM auth + project_id |
| AWS Bedrock | AWS SigV4 | `BedrockAdapter` — Converse API + SigV4 signing |
