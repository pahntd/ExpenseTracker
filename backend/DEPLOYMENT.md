# Backend Deployment Plan

Status: **Phase 3 (preparation) done — nothing is deployed or provisioned yet.**
Decision date: 2026-09-27, revised the same day to a Free-tier verification environment.
Provider facts were checked against the provider documentation on that date (links at the end); anything marked *verify in Phase 4* can only be confirmed on a real deployment.

```text
SELECTED HOSTING PROVIDER: Render
ENVIRONMENT (initial):     TEMPORARY VERIFICATION ENVIRONMENT — NOT PERMANENT PRODUCTION
  Web:      Render Free Web Service (Docker, Singapore region)
  Database: Render Free PostgreSQL (PostgreSQL 18, Singapore region, internal URL)
```

> ⚠️ **Verification-only.** This environment exists to prove the deployment and the Android ↔ backend integration end to end.
> The Free PostgreSQL database **expires 30 days after creation, has no backups, and must never be treated as the permanent production database.**
> Do not store data in it that anyone needs to keep. A permanent production environment is a separate, later decision (§9).

---

## 1. What the backend requires (verified in this repository)

| Requirement | Evidence |
|---|---|
| Container runtime, Java 21 | `backend/Dockerfile`: `eclipse-temurin:21-jdk` build stage → `eclipse-temurin:21-jre` runtime |
| Listens on `0.0.0.0:$PORT`, default `8080` | `config/ServerConfig.kt`, `main.kt`; image `EXPOSE 8080` |
| Plain HTTP inside the container | No TLS in the app; the platform must terminate HTTPS |
| PostgreSQL via JDBC | `database/DatabaseSettings.kt` (`org.postgresql.Driver`, JDBC URL) |
| `APP_ENV=production` ⇒ `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` mandatory | `DatabaseSettings.fromEnvironment`; image sets `ENV APP_ENV=production` |
| `JWT_SECRET` mandatory | `auth/JwtConfig.kt` (startup fails without it) |
| UTC | `main.kt` `TimeZone.setDefault(UTC)`, image `TZ=UTC` + `-Duser.timezone=UTC` |
| **Flyway runs on every startup, before anything else** | `Application.kt` → `FlywayConfig.migrate()` |
| ⇒ the DB must be reachable **before** the backend starts; there is no retry | startup throws, the platform restarts the container |
| ⇒ the DB user needs DDL rights (CREATE TABLE/INDEX in `public`) | `V1__initial_schema.sql` |
| `GET /health` = liveness only (200 JSON, no DB check) | `route/Routing.kt` |
| No outbound internet needed at runtime (DB only) | no HTTP clients in the backend |
| Stateless app, no local disk writes, logs to stdout | `logback.xml` (console appender) — compatible with Render Free's ephemeral filesystem |

These behaviours are intentionally **not** changed for deployment.

---

## 2. Hosting options evaluated

| | **Render** (selected) | **Fly.io** | **Railway** |
|---|---|---|---|
| Deployment model | Web service built from a Dockerfile in the Git repo (or a prebuilt image) | Machines from a Docker image via `flyctl` + `fly.toml` | Service from a Dockerfile/repo |
| Container / Java 21 | Yes (BuildKit build of our Dockerfile) | Yes | Yes |
| Port | App binds `$PORT` (Render default 10000; can be set, e.g. 8080) on `0.0.0.0` | `internal_port` in `fly.toml` | `$PORT` |
| HTTPS | Automatic, free managed TLS on `*.onrender.com`; HTTP → HTTPS redirect | Automatic on `*.fly.dev` | Automatic on `*.up.railway.app` |
| Secrets | Dashboard env vars, env groups, secret files | `fly secrets` | Service variables |
| Logs | Dashboard/CLI log stream | `fly logs` | Dashboard |
| Health checks | HTTP path, 2xx/3xx healthy, 5 s timeout; unhealthy 15 s → stop routing, 60 s → restart; failing deploys cancelled after 15 min | `[checks]` in `fly.toml` | Healthcheck path (deploy gating) |
| Postgres | Render Postgres (managed), incl. a **Free** instance; private internal URL | Managed Postgres, paid only | Postgres template container on a volume |
| **$0 option usable for verification** | **Yes**: Free web service + Free Postgres | No (trial only) | Limited free plan (0.5 GB RAM); DB is a self-run container |

### Why Render for the verification environment

1. **Complete $0 setup with a managed database**: Free web service + Free Render Postgres in the **same region**, connected over Render's private network (Free web services *can send* private-network traffic to data stores in the same region).
2. **Directly compatible with what Phase 2 built**: builds `backend/Dockerfile` unchanged; the app already reads `PORT` and binds `0.0.0.0`.
3. **Automatic HTTPS** on `*.onrender.com` — enough for real Android testing without a domain.
4. **Singapore region** — closest Render region to the developer/users (UTC+7).
5. Simple workflow: connect GitHub repo, set root directory, env vars, health path. No Kubernetes, no CLI required.
6. The same service can later be moved to paid instance types without changing the backend or its environment contract (§9).

---

## 3. Render Free limitations that matter to this project

Source: Render Free-tier and compute-plan docs (2026-09-27).

### Free Web Service

| Limitation | Impact on Expense Tracker |
|---|---|
| **Spins down after 15 minutes without inbound traffic; the next request triggers a cold start of about one minute** | The first Android request after idle time (login, splash refresh, sync) waits ~1 min **plus JVM + Flyway startup**. Android's OkHttp timeouts are 15 s, so that first request is expected to **fail with a timeout** and be retried later (sync treats network errors as retryable). This is a known, accepted limitation of the verification environment, not a bug. |
| **512 MB RAM, 0.1 CPU** | JVM startup (and Flyway) is slow on 0.1 CPU; memory headroom is small. Watch startup time and memory in Phase 4; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` is available if heap is too small. |
| 750 free instance hours per workspace per calendar month | Enough for one always-idle-sleeping service; service is suspended if exhausted. |
| Ephemeral filesystem, no persistent disk, no shell/SSH, single instance | No impact: the backend is stateless and logs to stdout; single instance also means only one process runs Flyway. |
| Can't receive private network traffic | No impact: only Android (public HTTPS) calls the backend. |
| Render may restart Free instances at any time | Brief unavailability; clients retry. |

### Free PostgreSQL

| Limitation | Impact |
|---|---|
| **Expires 30 days after creation** (then a 14-day grace period to upgrade to a paid plan before deletion) | The verification database has a **limited lifetime**. All data in it (test accounts, transactions) will be lost unless it is upgraded. Plan Phase 4–7 verification within this window. |
| **No backups of any kind** | Data loss is unrecoverable. |
| **1 GB fixed storage**, 256 MB RAM, 0.1 CPU, max 100 connections | Plenty for verification data. |
| Only one Free Postgres database per workspace | Recreating it means deleting the old one. |

**Consequence:** this database is **not** the permanent production database. It may hold test data only. Real user data waits for the permanent production decision (§9).

---

## 4. Verification database: Render Free PostgreSQL

| Item | Plan |
|---|---|
| Provider / plan | Render Postgres, **Free** instance — temporary |
| PostgreSQL version | **18** (matches local dev `postgres:18`; Flyway 13.5 + driver 42.7.8 verified against 18.6 locally) |
| Region | **Singapore** — must equal the web service region for the internal URL |
| Database name | `expense_tracker` (set at creation; cannot be changed later) |
| User | `expense_tracker` (set at creation; cannot be changed later). Render generates the password |
| Password | Render-generated → `<PRODUCTION_DB_PASSWORD>`; distinct from the dev password by construction |
| Connection model | **Internal URL** (private network, same region) from the web service |
| Public endpoint | Exists by default (TLS-only, rejects `sslmode=disable`). **Plan: remove all IP allowlist entries → external access disabled.** Temporarily allowlist a single admin IP only if needed |
| SSL | Enforced on external connections. Internal connections are on the private network; TLS optional there (self-signed, `verify-ca`/`verify-full` unsupported) |
| Backups | **None** on Free |
| Lifetime | **30 days** from creation (+14-day grace to upgrade) |
| Migration permissions | The Render-created user owns the database; V1 needs only CREATE TABLE/INDEX in `public` and no extensions — *verify in Phase 4 via Flyway log* |

### Connection string conversion (required)

Render shows URLs as `postgresql://USER:PASSWORD@HOST:5432/DATABASE`. The backend needs **JDBC** plus separate credentials:

```text
DATABASE_URL=jdbc:postgresql://<RENDER_DB_INTERNAL_HOST>:5432/expense_tracker
DATABASE_USER=expense_tracker
DATABASE_PASSWORD=<PRODUCTION_DB_PASSWORD>
```

`<RENDER_DB_INTERNAL_HOST>` is the host part of Render's **Internal Database URL** (e.g. `dpg-…-a`). Never paste the `postgresql://` URL into `DATABASE_URL` — the PostgreSQL JDBC driver rejects it.

### Migration readiness

- The database starts **empty**: Flyway creates `flyway_schema_history` and applies `V1__initial_schema.sql`. This exact path was tested in Phase 2 against a fresh PostgreSQL 18.6 container (all 5 tables created, V1 success).
- V1 uses only standard DDL (no extensions, no superuser features).
- Migration is **not** run in Phase 3; it runs on the first backend start in Phase 4.
- **V1 immutability:** once any database that must be preserved (the permanent production database) has applied `V1__initial_schema.sql`, it is immutable; every schema change must be a new `V2__…`, `V3__…`. Treat V1 as frozen from the first deployment onwards, even though the Free verification database itself is disposable.
- Single instance (Free) ⇒ only one process migrates at a time.

---

## 5. Environment contract (unchanged)

Template: [`backend/.env.example`](.env.example). Real values live **only** in the Render service's Environment settings. The contract is identical for the verification environment and a later permanent production environment.

| Variable | Value source | Secret |
|---|---|---|
| `APP_ENV` | `production` | no |
| `DATABASE_URL` | `jdbc:postgresql://<RENDER_DB_INTERNAL_HOST>:5432/expense_tracker` | no (no credentials inside) |
| `DATABASE_USER` | Render DB user | low |
| `DATABASE_PASSWORD` | `<PRODUCTION_DB_PASSWORD>` (Render-generated) | **yes** |
| `JWT_SECRET` | `<PRODUCTION_JWT_SECRET>` (generated locally, see §6) | **yes** |
| `PORT` | `8080` | no |
| `REDIS_URL` | Render Key Value **internal** URL, `redis://<RENDER_KV_INTERNAL_HOST>:6379` (see §5a) | low (internal-only host) |
| `CLIENT_IP_HEADER` | `CF-Connecting-IP` (see §5a) | no |
| `JAVA_TOOL_OPTIONS` (optional) | `-XX:MaxRAMPercentage=75` | no |

Rules:

- `DATABASE_URL` must be a **JDBC** PostgreSQL URL.
- `PORT` stays `8080` (Render routes to the `PORT` value; matches `EXPOSE 8080`).
- `JWT_SECRET` is production-only; never reuse the local compose value (`local-dev-only-…`).
- `APP_ENV=production` is correct for the verification environment too: it enables the fail-fast configuration checks.
- Render passes service env vars to Docker builds as build args. Our Dockerfile declares **no `ARG`s**, so no secret can be baked into the image. Keep it that way.

### 5a. Redis / IP rate limiting (added 2026-10-02)

`POST /login` and `POST /register` allow 5 requests/60 s per client IP, `POST /auth/refresh` 10/60 s, each in its own bucket (`rl:<login|register|refresh>:ip:<ip>`, fixed window, atomic Lua `INCR` + `PEXPIRE`). Over the limit: `429` + `Retry-After: <s>` + `{"error": "...", "code": "RATE_LIMITED", "retryAfterSeconds": <s>}`.

- `POST /login` additionally has a per-account limit (added 2026-10-02, Phase 2): 5 attempts/60 s per normalized email (`trim` + lowercase), key `rl:login:account:<email>`, checked after the IP limit and before the password check. Invalid credentials keep their existing `400` (identical for unknown email and wrong password, both counted); the attempt after 5 failures gets the same `429` contract with the account bucket's remaining TTL. A successful login deletes the counter. Uses the same Redis connection and `REDIS_URL` — no new service or variable.
- Counters live only in Redis, so every backend instance shares them. There is no in-memory fallback.
- `REDIS_URL` is mandatory with `APP_ENV=production`; startup also fails if Redis does not answer PING (same policy as the database).
- If Redis fails at runtime, the three endpoints answer `503` (fail closed) and the error is logged; other endpoints are unaffected. Lettuce reconnects automatically.
- Render Key Value: create it in the **same region** as the web service (Singapore) and use the **internal** URL. Keep external access disabled — the backend does not need it.
- Client IP: requests reach the container from a Render-internal 10.x proxy, so the socket address is the same for everyone. `CLIENT_IP_HEADER=CF-Connecting-IP` names the header Render's Cloudflare edge overwrites with the real client IP. `X-Forwarded-For` is deliberately not used: Render appends to it, so its first entry is client-controlled. Without `CLIENT_IP_HEADER` (or if the header is missing on a request) the limiter falls back to the socket address — stricter, never looser. *Verify after deploy* (§8): the startup log line `client IP from header CF-Connecting-IP`, and no `Header CF-Connecting-IP is missing` warnings for normal traffic.

---

## 6. Secrets handling (unchanged strategy)

- `<PRODUCTION_JWT_SECRET>`: 512-bit random value, base64url, generated locally on 2026-09-27 into
  `%USERPROFILE%\.expense-tracker-secrets\production.env` (outside the repository, ACL restricted to the local user).
  Paste it into Render's Environment settings in Phase 4, then delete the local file (or move it into a password manager).
  To regenerate, use a cryptographic generator only, e.g. `openssl rand -base64 64`, or in PowerShell
  `$b = New-Object byte[] 64; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`.
- `<PRODUCTION_DB_PASSWORD>`: generated by Render when the database is created; copy it from the database's Connect panel straight into the web service's `DATABASE_PASSWORD`.
- Never commit, never put in `docker-compose.yml`, never paste into chat/issues/logs.
- Dev and production credentials are distinct: dev = `docker-compose.yml` placeholders; Render = Render-generated DB password + separately generated JWT secret.

---

## 7. Render service settings for Phase 4

| Setting | Value |
|---|---|
| Service type | Web Service, runtime **Docker**, from the GitHub repo `pahntd/ExpenseTracker`, branch `main` |
| Root Directory | `backend` (build context = `backend/`, matches the Dockerfile's `COPY` paths; auto-deploys only on backend changes) |
| Dockerfile Path | `./Dockerfile` (relative to the root directory) |
| Docker Command | empty (use the image `ENTRYPOINT`) |
| Region | **Singapore** (same as the database) |
| Instance type | **Free** (512 MB, 0.1 CPU, sleeps after 15 min idle) |
| Instances | 1 |
| Health Check Path | `/health` (expects 200) — *verify in Phase 4 that the setting is available on the Free instance type* |
| Environment | variables from §5 |
| Auto-deploy | optional; manual deploy is fine for the first release |

### Public URL / HTTPS

- Default hostname: `https://<service-name>.onrender.com` (HTTPS automatic, HTTP redirected).
- Expected API base URL for Android verification (Phase 6): `https://<service-name>.onrender.com/` (trailing slash required by Retrofit).
- A custom domain is **not** needed.

### Health check

- Path `GET /health`, expected **200** `{"status":"ok"}`.
- Liveness only: it does not prove DB connectivity. DB connectivity is proven by a successful startup (Flyway + `SELECT 1` run before the server accepts traffic) and by the Phase 5 API checks.
- On Free, a sleeping service answers the first request only after the cold start; Render's health checks are not a keep-alive.

---

## 8. Verified vs. still to verify

**Verified from provider documentation (2026-09-27):** Dockerfile builds and custom Dockerfile path; `PORT` env var (default 10000, overridable) and `0.0.0.0` binding requirement; automatic managed TLS with HTTPS termination at Render's load balancer; env vars, env groups, secret files; env vars exposed to Docker builds as build args; health check semantics; regions (Oregon, Ohio, Virginia, Frankfurt, Singapore); Render Postgres versions 13–18, internal URL same-region only, external TLS enforcement, IP allowlist / external access disable; Free web service: 512 MB / 0.1 CPU, 15-min spin-down, ~1-min cold start, 750 h/month, ephemeral filesystem, can send private-network traffic to same-region data stores; Free Postgres: 256 MB / 0.1 CPU, 1 GB storage, 30-day expiry + 14-day grace, no backups, one per workspace.

**To verify during Phase 4:**

- Health check path configurable on the Free instance type.
- Render routes to container port 8080 with `PORT=8080`.
- Docker build (including the Gradle download inside the build) succeeds on Render's builders.
- JVM startup time and memory on 512 MB / 0.1 CPU; set `JAVA_TOOL_OPTIONS` if needed.
- Flyway connects over the internal URL and applies V1 with the Render DB user's permissions.
- Actual cold-start duration after spin-down (JVM + Flyway on 0.1 CPU).
- Whether `?sslmode=require` on the internal JDBC URL works (optional hardening).
- Log output visibility, restart-on-crash behaviour, graceful shutdown on redeploy.

---

## 9. Later: permanent production (not decided, not part of this plan)

The verification environment must be replaced before real users store data. That future decision needs at least: an always-on web instance (no spin-down), a database with **backups** and **no expiry**, and a plan for the V1-immutability rule above. Render paid instance types are one option; it is **not** decided or assumed here. The environment contract (§5), Dockerfile and secret strategy stay the same.

---

## 10. Phase 4 prerequisites checklist

- [ ] Render account + workspace, GitHub repo access granted (no billing needed for Free resources).
- [ ] Phase 3 repo changes committed and pushed to `main` (Render builds from GitHub).
- [ ] Render **Free** Postgres created: PostgreSQL **18**, region **Singapore**, DB `expense_tracker`, user `expense_tracker`. **Note the creation date — it expires 30 days later.**
- [ ] Database external access disabled (IP allowlist cleared).
- [ ] Internal hostname noted; `DATABASE_URL` built in JDBC form.
- [ ] `<PRODUCTION_JWT_SECRET>` available from the local secrets file.
- [ ] **Free** web service configured per §7 (Docker, root `backend`, Singapore, `/health`) with all §5 variables.
- [ ] Plan to watch the first deploy's logs for: `Starting in PRODUCTION mode`, Flyway `Successfully applied 1 migration … v1`, `Responding at http://0.0.0.0:8080`.
- [ ] Everyone involved knows: verification-only, test data only, database lifetime 30 days, first request after idle may time out.
- [ ] Android stays on the dev URL until Phase 6.

---

## Sources

- Render web services: https://render.com/docs/web-services
- Render Docker: https://render.com/docs/docker
- Render health checks: https://render.com/docs/health-checks
- Render environment variables & secrets: https://render.com/docs/configure-environment-variables
- Render regions: https://render.com/docs/regions
- Render Free tier: https://render.com/docs/free
- Render compute plans: https://render.com/docs/compute-plans
- Render Postgres create/connect: https://render.com/docs/postgresql-creating-connecting
- Fly.io pricing: https://docs.fly.io/about/pricing/
- Fly.io Managed Postgres: https://docs.fly.io/mpg/
- Railway plans: https://docs.railway.com/reference/pricing/plans
