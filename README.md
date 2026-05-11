# AIBridge

A Quarkus-based LLM Gateway that manages multiple LLM providers, standardizes I/O to the OpenAI Chat Completions format, and provides multi-tenant routing, rate pacing, failover, and a built-in admin UI.

## Prerequisites

| Tool        | Version   | Notes                                      |
|-------------|-----------|--------------------------------------------|
| Java        | 17+       | Amazon Corretto 17, Temurin, or Oracle JDK |
| Maven       | 3.9+      | Used for building the backend              |
| PostgreSQL  | 14+       | Stores LLM configs and providers           |
| Redis       | 7+        | Rate pacing, config cache, auth token cache|
| Node.js     | 22 LTS    | Only required if building the Angular UI   |
| Docker      | 24+       | Optional; used for `docker-compose`        |

## Project Structure

```
ai-bridge/
  pom.xml                          # Maven build (Quarkus 3.17, Java 17)
  docker-compose.yml               # PostgreSQL + Redis services
  Architecture.md                  # Full architecture & design document
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
          V1__init_schema.sql      # Flyway migration
    test/
      java/com/aibridge/           # Unit tests (JUnit 5 + Mockito)
```

## Quick Start

### Option 1: Docker for Dependencies + Local Backend (Recommended for IntelliJ)

**1. Start PostgreSQL and Redis:**

```bash
docker-compose up -d postgres redis
```

This starts:
- PostgreSQL on `localhost:5432` (database: `aibridge`, user/password: `postgres/postgres`)
- Redis on `localhost:6379`

**2. Configure `application.properties`:**

The default configuration in `src/main/resources/application.properties` is pre-configured for local development:

```properties
# Database
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/aibridge
quarkus.datasource.username=postgres
quarkus.datasource.password=postgres

# Redis
quarkus.redis.hosts=redis://localhost:6379

# Encryption key (CHANGE in production — must be exactly 32 characters)
aibridge.encryption.key=CHANGE_ME_IN_PRODUCTION_32CHARS!

# UI toggle
ui.required=true
```

**3. Build and run:**

```bash
# Backend only (fast iteration)
mvn quarkus:dev

# Or build the full JAR
mvn clean package -Dui.required=true
java -jar target/quarkus-app/quarkus-run.jar
```

**4. Access the service:**

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
docker-compose up -d postgres redis
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

### Option 4: Full Docker Run

Uncomment the `aibridge` service in `docker-compose.yml`, then:

```bash
mvn clean package -Dui.required=true
docker-compose up --build
```

## Configuration Reference

All configuration lives in `src/main/resources/application.properties`. Key properties:

### Database & Redis

| Property | Default | Description |
|----------|---------|-------------|
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://localhost:5432/aibridge` | PostgreSQL JDBC URL |
| `quarkus.datasource.username` | `postgres` | DB username |
| `quarkus.datasource.password` | `postgres` | DB password |
| `quarkus.redis.hosts` | `redis://localhost:6379` | Redis connection URL |

### Docker Profile Overrides

Properties prefixed with `%docker.` are active when `QUARKUS_PROFILE=docker`:

| Property | Value |
|----------|-------|
| `%docker.quarkus.datasource.jdbc.url` | `jdbc:postgresql://postgres:5432/aibridge` |
| `%docker.quarkus.redis.hosts` | `redis://redis:6379` |

### AIBridge Custom Config

| Property | Default | Description |
|----------|---------|-------------|
| `ui.required` | `true` | Build & serve Angular UI; set `false` for API-only mode |
| `aibridge.encryption.key` | *must set* | AES-256 encryption key (exactly 32 characters) |
| `aibridge.queue.timeout-ms` | `5000` | Max ms a request waits in pacing queue before failover |
| `aibridge.queue.poll-interval-ms` | `50` | Polling interval for pacing slot acquisition |
| `aibridge.queue.max-depth` | `1000` | Max queued requests per LLM config (prevents DoS) |
| `aibridge.load-test.max-parallel` | `500` | Max parallel requests for load testing |
| `aibridge.allowed-provider-hosts` | *(see file)* | Comma-separated allowlist for SSRF prevention |
| `aibridge.endpoint.allow-http` | `true` (dev) / `false` (prod) | Allow HTTP endpoints (should be false in prod) |

### CORS

| Property | Default |
|----------|---------|
| `quarkus.http.cors.origins` | `http://localhost:4200` |

In production, set this to your actual frontend origin(s).

## API Endpoints

### Chat Completions (Client-facing)

```
POST /v1/chat/completions
Headers:
  X-Feature: <feature-name>     (required)
  X-Tenant-ID: <tenant-id>     (optional; omit for global config)
  Content-Type: application/json
```

Request body follows the [OpenAI Chat Completions format](https://platform.openai.com/docs/api-reference/chat). The `model` field is resolved from the database — do not send it in the payload.

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

The packaged JAR is at `target/quarkus-app/quarkus-run.jar`.

### Running the Service

```bash
# 1. Start dependencies (PostgreSQL + Redis)
docker-compose up -d postgres redis

# 2a. Dev mode (hot reload, no JAR needed)
mvn quarkus:dev

# 2b. Or run the packaged JAR
java -jar target/quarkus-app/quarkus-run.jar
```

## Running Tests

```bash
# Unit tests only (no integration tests, no running DB required)
mvn clean test

# Generate JaCoCo coverage report (opens at target/site/jacoco/index.html)
mvn clean test jacoco:report
```

All tests use Mockito for mocking dependencies. No real database or Redis connection is needed.

## Building for Production

```bash
# Build backend + UI
mvn clean package -Dui.required=true

# Build backend only (API-only mode)
mvn clean package
```

The packaged JAR is at `target/quarkus-app/quarkus-run.jar`.

### Production Checklist

- Set `aibridge.encryption.key` to a strong 32-character secret (use env var or Vault)
- Set `%prod.aibridge.endpoint.allow-http=false` (already configured)
- Set `quarkus.http.cors.origins` to your frontend domain
- Use TLS for PostgreSQL and Redis connections
- Use a least-privilege DB user (Flyway migrations can use a separate privileged user)
- Review `aibridge.allowed-provider-hosts` for your provider allowlist

## OWASP Dependency Check

```bash
mvn verify -Psecurity-scan
```

Scans all transitive dependencies for known CVEs.

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
