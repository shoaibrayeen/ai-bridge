# Project Memory

Working memory for AIBridge. Short-term is the current state of play and gets pruned; long-term is
what stays true across sessions. Keep entries short and dated — if something here contradicts the
code, the code wins and the entry should be fixed or deleted.

---

## Long-term

Durable decisions, constraints and conventions. Change these only when the decision itself changes.

### Build & toolchain

- **Build with JDK 17 or 21 — never newer.** Quarkus 3.17.5 bundles a Byte Buddy that supports
  class files up to Java 23. On a newer JDK the Maven compile step *succeeds* and then Quarkus
  augmentation fails with `Java 25 (69) is not supported by the current version of Byte Buddy`,
  which reads like a code bug but is purely a toolchain mismatch.
  `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`
- **A fresh clone has no UI bundle.** `src/main/resources/META-INF/resources/` is git-ignored
  because it is the Angular build output. Build the frontend once (`mvn package -Dui.required=true`,
  `cd frontend && npm run build`, or the Dockerfile) before running with `ui.required=true`.
- **The Dockerfile needs nothing installed locally.** Three stages: Node builds the Angular bundle
  into the Maven resource tree, Maven builds the fast-jar around it, a JRE base runs it as non-root.

### Conventions

- **Never commit without review.** Finish the work, run the tests, leave everything in the working
  tree, and hand over the file list plus the test result. The user reviews and commits.
- **Tests mock everything.** No test touches a database, Redis, or the network — repositories, the
  cache and the provider adapters are all mocked. The suite runs in seconds and CI runs it on every
  push and pull request.
- **Secrets never enter git.** `.gitignore` covers `.env`, `.env.*`, `*.env` and key material;
  `.env.example` is the only committed exception and holds placeholders. `.dockerignore` keeps the
  same files out of image layers.

### Design decisions worth not re-litigating

- **Model identity is the gateway model name**, `ai-bridge-<sequence>-<slug>`, unique service-wide.
  The sequence counts up per *slug*, not per raw model name, so two names that normalise the same
  cannot collide. This is what allows the same model to be registered many times with different
  credentials or rate limits. `V2__gateway_model_name.sql` dropped the old
  `UNIQUE(tenant_id, model_name, is_fallback)` index that made it impossible.
- **A `model` starting with `ai-bridge-` pins the request to one config** and disables failover —
  you asked for a specific config. Anything else in `model` is ignored and routing falls back to
  `X-Tenant-ID` × `X-Feature`. The prefix is the discriminator so OpenAI SDKs, which must send some
  model, keep working unchanged.
- **Failover covers establishing a stream, not the middle of one.** Once the first chunk reaches
  the client there is no honest way to switch providers.
- **An unexpected exception is never failed over.** A bad encryption key or malformed credentials
  JSON fails identically on every config in the chain, so trying the next one only buries the
  cause. It is recorded in the lineage, logged, and rethrown.
- **Allowlist wildcards:** a leading `*.` matches the domain and any subdomain; a `*` standing as a
  whole label matches exactly one label and never crosses a dot. A bare `*` is deliberately inert —
  an entry allowing every host is far more likely a mistake than an intent.
- **`cache.mode` is the one knob that decides horizontal scaling.** `in-memory` keeps rate limits
  and admin sessions per JVM, so two replicas allow double the traffic and a restart logs everyone
  out. Use `redis` for anything with more than one instance.

### Known limitations, accepted for now

- **DNS rebinding.** The private-address check runs when a config is saved, not on every call. The
  allowlist is the primary control.
- **One shared consumer API key.** Revoking one consumer means rotating it for all of them.
- **watsonx and Bedrock stream by buffering.** They produce a valid `text/event-stream`, but the
  whole answer arrives as one chunk.

---

## Short-term

Current state of the work. Prune anything that has landed and stopped being interesting.

### As of 2026-09-12

**Shipped and verified against the running Docker stack:**

- Gateway-unique model names + `V2` migration. Verified live: two `claude-sonnet-6` configs coexist
  as `ai-bridge-1-…` and `ai-bridge-2-…`.
- `GET /v1/models` and `/v1/models/{id}` — OpenAI envelope, tenant-scoped, cross-tenant reads 404.
- Streaming (`stream: true`) — native SSE for OpenAI/Cerebras/Claude, buffered for watsonx/Bedrock.
- Per-attempt lineage with token detail; always logged, returned on `X-Include-Lineage: true`.
- Swagger UI at `/q/swagger-ui`, OpenAPI at `/q/openapi`, static reference at `/api-docs`.
- 702 tests, all green.

**Bugs found and fixed this round** (each had a test written first):

1. `bedrock-runtime.*.amazonaws.com` in the shipped allowlist matched **nothing** — the matcher
   only understood a leading `*.`, so no AWS Bedrock endpoint could ever be saved.
2. `SpaRoutingFilter` enumerated SPA routes in a regex, so refreshing on `/admin/llm-configs`,
   `/admin/llm-configs/new` or `/admin/llm-providers` failed. Now an exclusion rule that cannot rot
   when a route is added.
3. `GlobalExceptionMapper` caught `Throwable` and turned every JAX-RS 404 into a 500 logged at
   SEVERE — wrong status for callers, and log noise from any bot probing unknown URLs.
4. Timing-unsafe API key comparison; no brute-force limit on `POST /api/auth/token`.
5. Prod started happily with the repo's placeholder secrets. `ProductionSecretsCheck` now refuses.
6. An unexpected exception escaped without the lineage ever being logged.

**Not done / next:**

- No real LLM call has been exercised end to end — that needs real provider credentials. The seed
  rows in the local database hold fake ciphertext, so a completion against them fails at decryption.
- Per-consumer API keys, if one shared key stops being acceptable.
