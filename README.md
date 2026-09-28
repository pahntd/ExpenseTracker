# Expense Tracker

A personal income and expense tracker. It has a native Android app (Kotlin, XML Views) and a Kotlin/Ktor backend backed by PostgreSQL.

The app works **offline-first**. Every read and write goes to a local Room database, and a background sync engine built on WorkManager keeps Room in step with the user's account on the server.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Features](#2-features)
3. [Tech Stack](#3-tech-stack)
4. [Architecture](#4-architecture)
5. [App Flows](#5-app-flows)
6. [Android ↔ Backend](#6-android--backend)
7. [Database](#7-database)
8. [Sync Strategy](#8-sync-strategy)
9. [Screenshots](#9-screenshots)
10. [How to Run](#10-how-to-run)
11. [Project Structure](#11-project-structure)
12. [Known Limitations, Bugs and Future Work](#12-known-limitations-bugs-and-future-work)
13. [Design Decisions and Trade-offs](#13-design-decisions-and-trade-offs)

---

## 1. Overview

**What it is.** Users record income and expense transactions, group them into categories, and see totals, a balance and charts. Each user has an account, and the data belongs to that account instead of to one device.

**V1 → V2.** V1 was a local-only Android app: Room plus MVVM, no login, no server, with default categories seeded on the device. V2 keeps the same MVVM + Room core and adds:

| Problem in V1 | What V2 does |
|---|---|
| Data lived on one device only; reinstalling lost it | Email/password accounts. Data is stored per user in PostgreSQL through a Ktor REST API |
| No authentication | JWT access token (15 min) plus refresh token (30 days), automatic refresh on `401`, and a session encrypted at rest |
| The app would need the network to be useful once a backend existed | Offline-first: the UI reads only from Room. Changes are tracked per row (`syncStatus`) and pushed and pulled in the background |
| Default categories hard-coded on the client | The server creates the default categories when a user registers. The client never seeds them |
| Basic statistics | Charts (bar, pie, line), a Day/Week/Month/All time filter, and a 12-month trend |
| No monetization | AdMob banner ad and rewarded ads that unlock advanced statistics for a limited time |

**How the parts relate.** The Android app is the only client. It talks to the backend over HTTPS/JSON (Retrofit). The backend is stateless: it authenticates each request with a JWT, scopes every query to the `userId` in that token, and stores data in PostgreSQL. Flyway manages the schema.

---

## 2. Features

Only features that exist in the current code are listed.

### Authentication
- **Register** with email and password. The client validates the input (`AuthValidator`) and the server validates it again (email format, password of at least 6 characters, unique email). When registration succeeds, the app returns to Login; it does not log the user in automatically.
- **Login** returns an access token and a refresh token.
- **Persistent session.** The tokens and user id are stored in a Proto DataStore that Tink AEAD encrypts. The Tink key is protected by the Android Keystore.
- **Session restore on startup (Splash):**
  - With no session, the app opens Login.
  - Offline, it trusts the stored session and opens Home.
  - Online, it refreshes the access token. If the server rejects the refresh token, the app goes to Login. If the network fails, it keeps the session and opens Home.
- **Automatic token refresh.** An OkHttp `Authenticator` refreshes the access token on a `401` and retries the request once.
- **Logout.** If local changes have not been synced yet, the app shows a warning with a **Sync now** option. Logout revokes the refresh token on the server on a best-effort basis, then wipes local data, the session and account-scoped preferences.
- **Account switching.** If a *different* account logs in on the same device, the app wipes the local database first. If the *same* user logs back in after a forced logout, their unsynced changes are kept.

### Transactions
- Add, edit and delete transactions. Each has an amount, a type (**Income** or **Expense**), a category, a date and an optional note/title.
- A detail screen shows the transaction, with Edit and Delete actions.
- **Search** matches the note/title or the category name (SQL `LIKE`).
- Amounts are entered as whole numbers and shown with `vi-VN` digit grouping.

### Categories
- Create, rename (including the icon) and delete custom categories. Names are normalized: trimmed, repeated spaces collapsed, first letter capitalized.
- Each category shows how many transactions use it.
- **Default categories** (Food, Transport, Shopping, Salary, Entertainment, Education, Health, Other) are created on the server at registration and reach the device through sync. They cannot be edited or deleted; the buttons are disabled in the UI and the server rejects changes with `409`.
- **A category that is in use cannot be deleted.** The UI blocks it, the server returns `409`, and the sync order respects the foreign key.

### Home / Dashboard
- A greeting with a username derived from the part of the login email before `@`.
- All-time **total income**, **total expense** and **balance**.
- The transaction list with search.
- A **sync status label** (Syncing… / Synced / Offline / Sync failed) and a **manual sync button**.

### Statistics
- A time filter: **Day / Week / Month / All** (calendar-based, stored in SharedPreferences, Month by default).
- Income, expense and balance for the selected period.
- A bar chart comparing income and expense.
- A pie chart of **expenses by category**.
- A pie chart of **income by category**, locked behind a rewarded ad.
- A **12-month income/expense trend** line chart, locked behind a rewarded ad.

### Synchronization
- Offline-first: every screen reads Room `Flow`s, and every write goes to Room first.
- A per-row `syncStatus` (`SYNCED`, `PENDING_CREATE`, `PENDING_UPDATE`, `PENDING_DELETE`) and soft-delete tombstones.
- Background sync with WorkManager. It is triggered by **login**, **app startup**, **network reconnect**, the **manual sync button** and a **15-minute periodic** job, and it uses exponential backoff on retryable failures.
- Last-Edit-Wins conflict resolution based on `updatedAt` (see [Sync Strategy](#8-sync-strategy)).

### Settings
- Dark mode toggle, stored in SharedPreferences.
- Logout, with the pending-changes warning.

### Monetization (Google Mobile Ads, Next-Gen SDK)
- **User consent** is collected through the User Messaging Platform (UMP) before any ad is requested.
- An anchored **banner ad** appears on the Statistics screen.
- **Rewarded ads**: each locked Statistics feature unlocks separately for **3 hours**, and only after the reward is actually earned. Unlocks are stored on the device only and are revoked on logout.
- Debug builds use Google's public test ad unit IDs. Release builds use the production IDs. Both are configured per build type in `app/build.gradle.kts`.

---

## 3. Tech Stack

### Android (`android/`)

| Area | Technology | Where it is used |
|---|---|---|
| Language | Kotlin 1.9.24, JVM target 17 | Whole app |
| SDK | `minSdk 29`, `targetSdk`/`compileSdk 35`, AGP 8.7, Gradle 8.9 | `app/build.gradle.kts` |
| UI | XML layouts, ViewBinding, Material Components, RecyclerView + DiffUtil, ConstraintLayout | `ui/**`, `res/layout` |
| Navigation | Navigation Component + Safe Args, one Activity with a bottom navigation bar | `res/navigation/nav_graph.xml`, `MainActivity` |
| Presentation | MVVM: `ViewModel`, `StateFlow` UI state, `SharedFlow` one-off events | `ui/*/*ViewModel.kt` |
| Async | Kotlin Coroutines + Flow | Throughout |
| DI | Hilt, including `hilt-work` for workers | `di/`, `@HiltWorker` |
| Local DB | Room (KSP) | `data/local/` |
| Networking | Retrofit 2 + Gson converter, OkHttp (interceptor + authenticator) | `data/remote/`, `di/NetworkModule.kt` |
| Background work | WorkManager | `data/sync/` |
| Session storage | Proto DataStore + Tink AEAD (key in Android Keystore) | `data/auth/session/`, `src/main/proto/session.proto` |
| Preferences | SharedPreferences (dark mode, statistics filter, username, ad unlocks) | `utils/AppPreferences.kt` |
| Connectivity | `ConnectivityManager.NetworkCallback` exposed as a `StateFlow` | `data/network/` |
| Charts | MPAndroidChart | `ui/statistics/` |
| Ads | Google Mobile Ads Next-Gen SDK + User Messaging Platform | `ads/` |

### Backend (`backend/`)

| Area | Technology | Where it is used |
|---|---|---|
| Language / runtime | Kotlin 2.4, JVM toolchain 21 | `build.gradle.kts` |
| Web framework | Ktor 3 on Netty | `main.kt`, `Application.kt` |
| Serialization | kotlinx.serialization (Ktor ContentNegotiation) | `plugins/Serialization.kt` |
| Auth | Ktor Auth JWT + `com.auth0:java-jwt` (HMAC256), jBCrypt password hashing | `auth/`, `plugins/Authentication.kt` |
| Error mapping | Ktor StatusPages | `plugins/StatusPages.kt` |
| Data access | Exposed (DSL, JDBC, java-time) | `database/table/`, `repository/Exposed*` |
| Database | PostgreSQL (image `postgres:18` in Compose) | `docker-compose.yml` |
| Migrations | Flyway, run automatically at startup | `database/FlywayConfig.kt`, `resources/db/migration/` |
| Packaging | Ktor fat JAR, multi-stage Dockerfile (Temurin 21), Docker Compose for local use | `Dockerfile`, `../docker-compose.yml` |
| Logging | Logback, console output | `resources/logback.xml` |
| Hosting | Render (Docker web service + Render PostgreSQL). Details in `backend/DEPLOYMENT.md` | Release `BASE_URL` |

---

## 4. Architecture

### Android layers

```text
┌──────────────────────────────────────────────────────────────┐
│ UI: Fragments + ViewBinding, dialogs, RecyclerView adapters   │
└──────────────┬───────────────────────────────────────────────┘
               │ user actions ↓        ↑ StateFlow<UiState> / SharedFlow<Event>
┌──────────────▼───────────────────────────────────────────────┐
│ ViewModel (@HiltViewModel)                                   │
└──────┬─────────────────────────────┬─────────────────────────┘
       │                             │ enqueueSync(trigger)
┌──────▼──────────────────────┐ ┌────▼──────────────────────────────────┐
│ Repositories                │ │ SyncScheduler → WorkManager            │
│ Transaction / Category:     │ │   → SyncWorker → SyncManager           │
│   Room only, stamp sync     │ │        ├─ push: DAO ↔ CategoryApi/     │
│   metadata                  │ │        │         TransactionApi        │
│ AuthRepository: AuthApi     │ │        └─ PullManager + MergeEngine    │
│ SettingRepository: logout   │ │                                        │
└──────┬──────────────────────┘ └────┬───────────────────────┬──────────┘
       │                             │                       │
┌──────▼─────────────────────────────▼──┐     ┌──────────────▼──────────┐
│ Room (DAOs, entities, Flow queries)   │     │ Retrofit + OkHttp       │
│ single source of truth for the UI     │     │ AuthInterceptor +       │
└───────────────────────────────────────┘     │ AuthAuthenticator       │
                                              └──────────────┬──────────┘
                                                             │ HTTPS/JSON
                                                       Ktor backend
```

| Layer | Responsibility |
|---|---|
| **UI** (`ui/<feature>/`) | Renders state, forwards user input, handles navigation and dialogs. Feature packages: `splash`, `login`, `register`, `home`, `add`, `detail`, `category`, `statistics`, `setting`. |
| **ViewModel** | Holds a `UiState` (`StateFlow`) and emits one-off events (`SharedFlow`). Validates input and calls repositories. It never calls the network for transaction or category data. It asks for a sync only through `SyncScheduler`. |
| **Repositories** (`data/repository/`, `data/auth/`) | `TransactionRepository` and `CategoryRepository` read and write **Room only**. They stamp `updatedAt` and `syncStatus` themselves through `SyncStatusPolicy` and never trust values from the caller. `AuthRepository` wraps the auth endpoints and turns HTTP and IO errors into result types. `SettingRepository` handles logout. |
| **Local data source** (`data/local/`) | Room entities, DAOs, type converters and relation/aggregate projections. UI queries filter out `deletedAt IS NULL` tombstones. |
| **Remote data source** (`data/remote/`) | Retrofit interfaces, DTOs, DTO↔entity mappers (`mapper/`) and error classification (`AppError`: Network, Server, Unauthorized, Client, Unknown). |
| **Sync** (`data/sync/`) | `SyncScheduler` decides *when* a sync runs (WorkManager unique work). `SyncManager` decides *how*: push, then pull. `PullManager` fetches a full snapshot. `MergeEngine` holds the per-row merge rules. `SyncStatusHolder` exposes the current `SyncState` to the UI. |
| **Session** (`data/auth/session/`) | `SessionManager` is the only access point to the encrypted DataStore. It also keeps in-memory copies of the tokens so that OkHttp can read them synchronously. |

**Mapping.** There is no separate domain layer. Room entities (`TransactionEntity`, `CategoryEntity`) and Room projections (`ExpenseWithCategory`, `CategoryWithExpenseCount`, …) go straight to the UI. The only mapping boundary is **DTO ↔ Entity** in `data/remote/mapper`. That code converts amounts (`Double` ↔ decimal string), timestamps (epoch millis ↔ ISO-8601 UTC) and ids.

> Note: `StatisticsViewModel` queries `TransactionDao` directly instead of going through a repository.

### Backend layers

```text
Route (route/*)  →  Service (service/*)  →  Repository (repository/Exposed*)  →  PostgreSQL
   │ parses/validates the request, reads userId from the JWT
   └ StatusPages: IllegalArgumentException → 400, NoSuchElementException → 404, IllegalStateException → 409
```

- **Routes** parse path and body values (UUIDs, amounts, ISO dates) and call a service. `/categories/**` and `/transactions/**` are wrapped in `authenticate("auth-jwt")`.
- **Services** hold the business rules: validation, ownership checks, idempotent create, Last-Edit-Wins update, default-category immutability and category-in-use checks.
- **Repositories** are Exposed DSL queries. Every query is filtered by `user_id`.

---

## 5. App Flows

### Authentication flow

```text
Register ──(POST /register)──► server creates user + 8 default categories ──► back to Login
Login ──(POST /login)──► { userId, accessToken, refreshToken }
   ├─ if the last user on this device ≠ this user → wipe Room (sync is cancelled first)
   ├─ reset account-scoped preferences
   ├─ save the session (encrypted DataStore)
   ├─ enqueueSync(LOGIN) + schedule periodic sync
   └─ navigate to Home
App start (Splash) ── session? ── no ──► Login
                         └─ yes ─ offline ──► Home (trust the local session)
                                  online ──► POST /auth/refresh
                                               ├─ OK ──► update access token ──► Home + enqueueSync(STARTUP)
                                               ├─ 400/401 ──► clear session ──► Login
                                               └─ network/unknown error ──► Home (keep the session)
Logout ── pending changes? ── yes ──► warning dialog [Sync now | Log out anyway]
   └─► cancel sync work ─► wipe Room ─► POST /auth/logout (best effort) ─► clear prefs + session ─► Splash
```

### Transaction flow (write path)

```text
AddExpenseFragment ─► AddExpenseViewModel.save() (validation)
   ─► TransactionRepository.insertExpense / updateExpense / deleteExpenseById
        ─► Room: updatedAt = now, syncStatus = PENDING_* (or hard delete if never synced)
   ─► Room Flow emits ─► HomeViewModel / StatisticsViewModel ─► UI updates immediately
   (the network is not involved; the change is pushed at the next sync)
```

### Sync flow

```text
Trigger (LOGIN | STARTUP | RECONNECTED | MANUAL | PERIODIC)
  ─► SyncScheduler.enqueueSync()  (unique work "expense_tracker_sync", KEEP, needs a network)
  ─► SyncWorker ─► SyncManager.sync()
        1. PUSH   Room PENDING_* rows ─► POST/PUT/DELETE ─► apply the response to Room
        2. PULL   GET /categories + GET /transactions (full snapshot, in parallel)
                  ─► merge into Room inside one Room transaction
  ─► Room Flows emit ─► UI refreshes;  SyncStatusHolder ─► Home sync label
```

---

## 6. Android ↔ Backend

```text
Android UI ─► ViewModel ─► Repository ─► Room ◄──────────────┐
                                                              │ push/pull (background)
                                         SyncWorker ─► SyncManager ─► Retrofit ─► OkHttp
                                                                                    │ Authorization: Bearer <access>
                                                                                    ▼
                                                                  Ktor (JWT auth) ─► Service ─► Exposed ─► PostgreSQL
```

**API client.** Retrofit with the Gson converter. `BuildConfig.BASE_URL` is set per build type: debug points to a LAN host over HTTP, release to the HTTPS Render deployment. OkHttp uses 15-second connect/read/write timeouts.

**Token handling.**
- `AuthInterceptor` adds `Authorization: Bearer <accessToken>` to every request. The token comes from `SessionManager`'s in-memory copy.
- On a `401`, `AuthAuthenticator` calls `POST /auth/refresh` through a separate "bare" OkHttp/Retrofit client that has no interceptor or authenticator, so a refresh cannot loop. It then saves the new access token and retries the request **once**. Its body is `synchronized`, so if several requests get a `401` at the same time, only one refresh call is made.
- The backend does **not** rotate refresh tokens; only the access token changes. If the refresh request fails with an IO error, the session is kept.

**How the server identifies the user.** An access token is an HS256 JWT with issuer `expense-tracker`, audience `expense-tracker-app` and a `userId` claim. It expires after 15 minutes. `call.currentUserId()` reads the claim, and every repository query is filtered by that id. A resource that belongs to another user is reported as not found (`404`). Creating a resource with an id that another user already owns returns `409`.

**Main endpoints**

| Method & path | Auth | Purpose |
|---|---|---|
| `GET /health` | – | Liveness check (`{"status":"ok"}`); does not check the database |
| `POST /register` | – | Create a user and the default categories |
| `POST /login` | – | Returns `userId`, `email`, `accessToken`, `refreshToken` |
| `POST /auth/refresh` | – | Exchange a refresh token for a new access token |
| `POST /auth/logout` | – | Revoke a refresh token |
| `GET /categories` · `GET /transactions` | JWT | Full list for the current user (pull snapshot) |
| `POST /categories` · `POST /transactions` | JWT | Create with a **client-generated UUID**. Idempotent: repeating the request returns the existing row |
| `PUT /categories/{id}` · `PUT /transactions/{id}` | JWT | Last-Edit-Wins update. A stale update gets the current server row back |
| `DELETE /categories/{id}` · `DELETE /transactions/{id}` | JWT | Hard delete (`204`). Deleting a default or in-use category returns `409` |

**Data mapping.** Amounts are sent as decimal strings (the server stores `NUMERIC(15,2)`, the client stores `Double`). Dates and `updatedAt` are ISO-8601 `OffsetDateTime` in UTC (the client stores epoch millis). The transaction type is `"INCOME"` or `"EXPENSE"`. Ids are UUID strings created by whichever side creates the row: the client for user data, the server for default categories. Ids are never remapped.

**From server into Room.** API responses are never shown to the UI directly. Push responses are written to Room through `applyIfUnchanged(...)`, which only writes if the row still matches what was sent, so a slow response cannot overwrite a newer local edit. Pull snapshots are merged into Room row by row according to `decideMergeAction`. The UI then updates from the Room `Flow`s.

---

## 7. Database

### Local database (Room: `expense_database`, version 1)

| Table | Entity | Key fields |
|---|---|---|
| `categories` | `CategoryEntity` | `id` (String UUID, PK), `name` (**unique index**), `icon`, `isDefault`, `updatedAt`, `syncStatus`, `deletedAt` |
| `expenses` (stores both income and expenses) | `TransactionEntity` | `id` (String UUID, PK), `amount: Double`, `type` (INCOME/EXPENSE), `categoryId` (FK → `categories.id`, `ON DELETE RESTRICT`, indexed), `date` (epoch ms), `title`, `updatedAt`, `syncStatus`, `deletedAt` |

- One category has many transactions. `RESTRICT` means a category that is still in use cannot be deleted.
- `updatedAt` is the local edit time and drives Last-Edit-Wins. `syncStatus` records what still has to be pushed. `deletedAt` marks a soft-deleted row (tombstone) that is waiting for its delete to be pushed.
- Room holds **one account at a time**, so there is no `userId` column. The whole database is wiped on logout or when a different account logs in.

### Server database (PostgreSQL, Flyway `V1__initial_schema.sql`)

| Table | Key columns | Relationships |
|---|---|---|
| `users` | `id UUID PK`, `email UNIQUE`, `password_hash` (BCrypt), `created_at`, `updated_at` | – |
| `refresh_tokens` | `id UUID PK`, `user_id`, `token UNIQUE`, `expires_at`, `created_at`, `revoked_at` | FK → `users` `ON DELETE CASCADE` |
| `categories` | `id UUID PK`, `user_id`, `name`, `icon`, `is_default`, `created_at`, `updated_at` | FK → `users`. `UNIQUE (user_id, name)` |
| `transactions` | `id UUID PK`, `user_id`, `amount NUMERIC(15,2)`, `type CHECK IN ('EXPENSE','INCOME')`, `category_id`, `date`, `title`, `created_at`, `updated_at` | FK → `users`, FK → `categories` |

- **Ownership:** every category and transaction has a `user_id`, and every query filters by it.
- `created_at` is set by the server. `updated_at` is the **client's** edit time and is what the Last-Edit-Wins check compares.
- There is no soft delete on the server. A deleted row is removed, and clients notice the deletion because the row is missing from the next full snapshot.
- Timestamps are `TIMESTAMPTZ`, and the backend runs in UTC.

---

## 8. Sync Strategy

**Model: local-first.** Room is the single source of truth for the UI. The network is only used by the background sync pass, plus login, register and token refresh.

### Local change tracking (`SyncStatusPolicy`)

| Current status | Local edit → | Local delete → |
|---|---|---|
| `PENDING_CREATE` (never reached the server) | stays `PENDING_CREATE` | **hard delete** (the server never saw the row) |
| `SYNCED` / `PENDING_UPDATE` | `PENDING_UPDATE` | tombstone: `PENDING_DELETE` + `deletedAt` |
| `PENDING_DELETE` | blocked | blocked |

### One sync pass: push, then pull

1. **Push**, in dependency order: category creates → category updates → transaction creates/updates → transaction deletes → category deletes.
   - A transaction whose category could not be confirmed on the server is skipped in this pass.
   - A category delete is skipped while any local transaction still references that category.
   - Each response is written to Room only if the row has not changed since the request was sent (`applyIfUnchanged`).
   - Races are handled: if a create's response is stale, a follow-up `PUT` is sent. If the row was hard-deleted while its `POST` was in flight, a compensating `DELETE` is sent, with a tombstone kept if that delete fails.
2. **Pull** runs only if no push failure was retryable. It fetches **all** categories and transactions in parallel. The result is all-or-nothing: if either request fails, or any record cannot be mapped, nothing is merged. The merge runs inside a single Room transaction.

### Conflict handling: Last-Edit-Wins on `updatedAt`

- **Server:** `UPDATE … WHERE updated_at < :incoming`. A stale update gets the current server row back, and the client adopts it.
- **Client merge** (`decideMergeAction`) for each id in local ∪ server:

| Local | On server? | Result |
|---|---|---|
| `SYNCED` | yes / no | use the server row / delete locally |
| `PENDING_CREATE` | no | keep local (push again later) |
| `PENDING_CREATE` / `PENDING_UPDATE` | yes | keep local if `local.updatedAt > server.updatedAt`, otherwise use the server row (**ties go to the server**) |
| `PENDING_UPDATE` | no | delete locally (the row was deleted on the server) |
| `PENDING_DELETE` | yes / no | keep the tombstone / delete locally |
| (none) | yes | insert as `SYNCED` |

### Online, offline and retry
- **Offline:** everything except login and register works. Sync work stays queued because WorkManager's `NetworkType.CONNECTED` constraint is not met, and Home shows **Offline**.
- **Reconnect:** `ExpenseApplication` watches `NetworkMonitor` (a network counts as online only if it has the `INTERNET` and `VALIDATED` capabilities) and enqueues `RECONNECTED` when the device goes from offline to online.
- **Retry:** a network error, timeout or `5xx` → `Result.retry()` with **exponential backoff starting at 30 s**. Errors that retrying will not fix (a `4xx`, or an unmappable record) do not cause a retry; the affected rows stay `PENDING_*` and the status shows **Sync failed**.
- **Concurrency:** all triggers share one unique work item with `KEEP`, so only one sync pass runs at a time. A separate 15-minute periodic worker only enqueues that same work.
- **UI status:** `SyncState` (`IDLE`, `SYNCING`, `SYNCED`, `OFFLINE`, `SYNC_FAILED`) is kept in memory for display only. Whether data is still pending is always decided from the per-row `syncStatus` in Room.

### What is *not* implemented
- Editing data does **not** start a sync by itself. A change is pushed at the next trigger: reconnect, the periodic job (about every 15 minutes), app start or login, or a manual sync.
- There is no incremental ("changes since") pull; every pull downloads the full dataset.
- There is no field-level merge; the whole row wins. Last-Edit-Wins relies on device clocks, and clock skew is not handled.

---

## 9. Screenshots

> The images in `android/screenshots/` were taken from **V1** (no login, older Statistics screen) and no longer match the app, so they are not shown here. The V2 screenshots below still need to be captured.

Planned layout: `screenshots/` at the repository root, using portrait phone captures in light mode unless noted.

| # | File | Screen / state |
|---|---|---|
| 1 | `screenshots/login.png` | Login |
| 2 | `screenshots/register.png` | Register |
| 3 | `screenshots/home.png` | Home: greeting, totals, transaction list, sync status label |
| 4 | `screenshots/home_search.png` | Home with a search query |
| 5 | `screenshots/add_transaction.png` | Add/Edit transaction form |
| 6 | `screenshots/transaction_detail.png` | Transaction detail |
| 7 | `screenshots/category.png` | Category list (default categories with disabled buttons, usage counts) |
| 8 | `screenshots/add_category.png` | Add/Edit category dialog |
| 9 | `screenshots/statistics.png` | Statistics: time filter, bar chart, expense pie chart, banner ad |
| 10 | `screenshots/statistics_locked.png` | Locked "Income by category" / "Monthly trend" cards |
| 11 | `screenshots/statistics_unlocked.png` | Unlocked income pie chart and 12-month trend |
| 12 | `screenshots/settings.png` | Settings (dark mode, logout) |
| 13 | `screenshots/logout_pending.png` | Logout warning with unsynced changes |
| 14 | `screenshots/dark_mode.png` | Any main screen in dark mode |
| 15 | `screenshots/offline.png` | *(optional)* Home showing the **Offline** sync state |

Once the files exist, uncomment the gallery below:

<!--
<p align="center">
  <img src="screenshots/login.png" width="200"/>
  <img src="screenshots/home.png" width="200"/>
  <img src="screenshots/add_transaction.png" width="200"/>
  <img src="screenshots/transaction_detail.png" width="200"/>
</p>
<p align="center">
  <img src="screenshots/category.png" width="200"/>
  <img src="screenshots/statistics.png" width="200"/>
  <img src="screenshots/statistics_unlocked.png" width="200"/>
  <img src="screenshots/settings.png" width="200"/>
</p>
-->

---

## 10. How to Run

### Requirements

| | Version |
|---|---|
| Android Studio | A version that supports AGP 8.7 / Gradle 8.9 (Ladybug or newer) |
| JDK for Android | 17 |
| Android SDK | Platform 35. Device or emulator on API 29 (Android 10) or higher |
| JDK for backend | 21 (Gradle toolchain) |
| Docker | Docker Desktop / Engine with Compose v2 |

### Backend — local development

The database schema is created automatically: **Flyway runs on every startup**, before the server accepts requests. The server listens on `0.0.0.0:8080`, or on `$PORT` if that is set.

**Option A: everything in Docker.** From the repository root:

```bash
docker compose up --build -d
curl http://localhost:8080/health      # → {"status":"ok"}
```

`docker-compose.yml` starts `postgres:18` and the backend image with **development-only placeholder** credentials.

**Option B: PostgreSQL in Docker, backend with Gradle** (the usual choice while editing backend code):

```bash
docker compose up -d postgres
cd backend
# JWT_SECRET is always required. Use any long random value for local development.
export JWT_SECRET="<any-local-dev-secret>"          # PowerShell: $env:JWT_SECRET="<any-local-dev-secret>"
./gradlew run
```

If `APP_ENV` is not set, the backend runs in **development** mode. Any missing `DATABASE_*` variable then falls back to the local Compose database (`localhost:5432/expense_tracker`).

Tests: `./gradlew test` (in `backend/`).

### Android — local development

1. Open the **`android/`** folder in Android Studio and let Gradle sync.
2. **Create `android/keystore.properties`.** `app/build.gradle.kts` reads the release signing config every time the build is configured, so **debug builds fail too** if this file is missing. Git ignores it. For local development you can point it at the standard Android debug keystore:
   ```properties
   storeFile=/absolute/path/to/.android/debug.keystore
   storePassword=android
   keyAlias=androiddebugkey
   keyPassword=android
   ```
3. **Point the debug build at your backend.** The debug `BASE_URL` is hard-coded to a LAN IP in `app/build.gradle.kts`, and that host is the only one allowed to use cleartext HTTP in `app/src/debug/res/xml/network_security_config.xml`. Change **both** files:
   - Physical device on the same Wi-Fi: `http://<your-PC-LAN-IP>:8080/`
   - Android emulator: `http://10.0.2.2:8080/`

   Keep the trailing `/`, because Retrofit requires it.
4. Run the `app` configuration, or from the command line:
   ```bash
   cd android
   ./gradlew :app:installDebug
   ```
5. Register an account, log in, and the first sync downloads the default categories.

Debug builds use Google's **test** AdMob IDs, so ads work without an AdMob account.

### Development vs production

| | Development | Production (release) |
|---|---|---|
| Android build | `debug`: LAN `BASE_URL` over HTTP, test ad IDs | `release`: HTTPS Render `BASE_URL`, production ad IDs, signed with `keystore.properties`, `isMinifyEnabled = false` |
| Backend mode | `APP_ENV` unset → development defaults | `APP_ENV=production`: fails at startup if `DATABASE_URL`, `DATABASE_USER` or `DATABASE_PASSWORD` is missing |
| Configuration | Compose placeholders / shell variables | Hosting provider environment variables. The template is `backend/.env.example` (no real values) |
| Database | Local `postgres:18` container | Render PostgreSQL, reached with a JDBC URL on the internal network |
| Details | – | `backend/DEPLOYMENT.md` (provider choice, Free-tier limits, environment contract, secrets handling) |

> Real secrets (`JWT_SECRET`, database passwords, signing keys) are never committed. `.env`, `keystore.properties` and `keystore/` are git-ignored.

---

## 11. Project Structure

```text
ExpenseTracker/
├── android/                                  # Android app (Gradle project root)
│   ├── app/
│   │   ├── build.gradle.kts                  # build types: BASE_URL, AdMob IDs, signing
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/pahntd/expensetracker/
│   │       │   │   ├── ads/                  # consent, banner, rewarded, feature unlocks
│   │       │   │   ├── data/
│   │       │   │   │   ├── auth/             # AuthRepository, result types
│   │       │   │   │   │   └── session/      # encrypted Proto DataStore session
│   │       │   │   │   ├── local/            # Room: database, dao, entity, relation, converter, sync policy
│   │       │   │   │   ├── network/          # NetworkMonitor (connectivity StateFlow)
│   │       │   │   │   ├── remote/           # Retrofit APIs, DTOs, mappers, interceptor, authenticator, errors
│   │       │   │   │   ├── repository/       # Transaction, Category, Setting repositories, local data wipe
│   │       │   │   │   └── sync/             # SyncScheduler, workers, SyncManager, PullManager, MergeEngine
│   │       │   │   ├── di/                   # Hilt modules (Database, Network, DataStore, WorkManager, …)
│   │       │   │   ├── ui/                   # splash, login, register, home, add, detail, category, statistics, setting
│   │       │   │   ├── utils/                # preferences, validators, formatting, icon mapping
│   │       │   │   ├── ExpenseApplication.kt # Hilt app, WorkManager config, reconnect observer, periodic sync
│   │       │   │   └── MainActivity.kt       # single Activity, bottom navigation, ads init
│   │       │   ├── proto/session.proto       # session schema
│   │       │   └── res/                      # layouts, navigation graph, menus, drawables
│   │       └── debug/res/xml/                # debug-only network security config (LAN cleartext)
│   └── screenshots/                          # V1 screenshots (outdated)
│
├── backend/                                  # Ktor server (Gradle project root)
│   ├── src/main/kotlin/
│   │   ├── main.kt, Application.kt           # startup: env → Flyway → DB → plugins → routes
│   │   ├── api/                              # request/response DTOs + model→response mappers
│   │   ├── auth/                             # JWT config, TokenService, BCrypt, refresh token generator
│   │   ├── config/                           # APP_ENV, PORT
│   │   ├── database/                         # settings, Flyway, connection, Exposed tables
│   │   ├── model/                            # domain models
│   │   ├── plugins/                          # Authentication, Serialization, StatusPages, currentUserId()
│   │   ├── repository/                       # repository interfaces + Exposed implementations
│   │   ├── route/                            # auth/health, category and transaction routes
│   │   └── service/                          # business rules, default categories
│   ├── src/main/resources/db/migration/      # Flyway SQL migrations
│   ├── Dockerfile                            # multi-stage build → non-root JRE 21 image
│   ├── .env.example                          # production environment template (no values)
│   └── DEPLOYMENT.md                         # hosting plan (Render) and environment contract
│
├── docker-compose.yml                        # local PostgreSQL + backend (dev placeholders only)
└── README.md
```

---

## 12. Known Limitations, Bugs and Future Work

### Known limitations (current design or scope)
- **Single account per device.** Room has no per-user scoping. Logging out, or logging in as a different user, wipes all local data, including changes that were never synced. The logout dialog warns about this.
- **Login and registration need the network.** Everything else works offline.
- **Sync is not immediate after an edit.** It waits for the next trigger (see [Sync Strategy](#8-sync-strategy)).
- **Full-snapshot pull.** Every sync downloads every category and transaction for the user. There is no pagination or incremental pull, which will not scale to large datasets.
- **Last-Edit-Wins depends on device clocks**, and the whole row wins. There is no field-level merge.
- **Rows that fail with a non-retryable error** (for example a `400` because a category name already exists on the server) stay `PENDING_*`, and the user has no way to resolve them apart from the generic "Sync failed" label.
- **Category names:** Room enforces a *global* unique `name`, while the server enforces `(user_id, name)`. If a server category and a local pending category have the same name but different ids, the server one is silently ignored during the merge.
- **Amounts:** the client stores `Double` and accepts whole numbers only. The server stores `NUMERIC(15,2)`.
- **Forced logout inside the app.** If the refresh token is rejected while the app is running, the session is cleared but nothing sends the user to Login until the next app start. Periodic sync is also scheduled while logged out, and those runs end with `401`.
- **The authenticator clears the session on any failed refresh response, including `5xx`.** Splash, by contrast, keeps the session on unexpected errors.
- **Room schema** is version 1 with `exportSchema = false` and no migrations. The next schema change needs a migration plan.
- **Large screens:** phones are locked to portrait, and there are no tablet-specific layouts.
- **Backend hardening:**
  - Refresh tokens are stored in plaintext and are not rotated.
  - `/auth/refresh` returns plain-text `401` bodies.
  - Bad credentials return `400` rather than `401`.
  - Two concurrent creates with the same category name would surface as a `500`.
- **Hosting:** the release build points to a Render **Free**-tier environment, which `backend/DEPLOYMENT.md` describes as a temporary verification environment. The service sleeps after 15 minutes idle, the first request can take about a minute and hit the 15-second client timeout, and the Free database expires after 30 days with no backups. It is **not** a permanent production setup.
- **Tests:** Android has only the template example tests. The backend has two tests (health check and removed debug routes). The sync engine has no automated tests.

### Bugs found while writing this README (not fixed)
- **The Android build fails without `android/keystore.properties`,** including debug builds, because the release `signingConfig` is read without checking that the file exists. See [How to Run](#android--local-development).
- **`CategoryRecyclerViewAdapter` disables the Edit/Delete buttons for default categories but never re-enables them.** A recycled row can therefore show a custom category with disabled buttons.
- **`SyncManager.sync` only catches `CancellationException`.** If a Room exception is thrown during the merge, the worker fails and the Home label can stay on "Syncing…".
- **Editing a default category** shows the misleading message "Category already exists !". The UI normally prevents the edit, so this is only reachable if the disabled-button bug above lets it through.

### Future development
- Budgets per category or month.
- Trigger a sync right after local edits (debounced), and pull only changes since the last sync.
- Central handling of an expired session: navigate to Login and stop periodic sync while logged out.
- A permanent production environment (always-on instance, database with backups) and a Google Play release.
- Unit tests for `decideMergeAction`, `SyncManager` and `SyncStatusPolicy`, plus backend service and route tests.
- Export and import (CSV), recurring transactions.

---

## 13. Design Decisions and Trade-offs

A short summary for explaining the project.

- **Why offline-first with Room as the source of truth?** An expense is often recorded right after spending, which may be without a connection. Reading only from Room keeps the UI instant and the same online or offline. The cost is a sync engine to write and maintain.
- **Why per-row `syncStatus` instead of an outbox table?** The pending state stays next to the data, is easy to query (`hasPendingChanges()`), survives process death, and needs no second table to keep consistent. The limitation is that only the latest state of a row is pushed, not every intermediate change, which is fine for Last-Edit-Wins.
- **Why client-generated UUIDs?** A row gets its final id offline, so there is no id remapping after sync, and `POST` can be idempotent by id: a retry after a lost response is safe.
- **Why push before pull?** The merge then compares the server snapshot with Room rows that already reflect this pass's pushes, instead of racing them.
- **Why WorkManager?** It gives network constraints, persistence across process death, exponential backoff, and unique work to prevent overlapping passes. `SyncScheduler` decides *when* to sync and `SyncManager` decides *how*.
- **Why Last-Edit-Wins?** It is simple and predictable for a single-user app where conflicts mostly come from the same person on two devices. The trade-offs are the dependence on device clocks and whole-row resolution.
- **Why Ktor + Exposed + PostgreSQL + Flyway?** The backend uses Kotlin like the app. PostgreSQL gives relational integrity: foreign keys, `UNIQUE (user_id, name)` and a type `CHECK`. Flyway makes schema changes repeatable and runs them at startup.
- **Why short-lived JWT plus a stored refresh token?** Requests are stateless and cheap to verify, the refresh token can be revoked on logout, and a stolen access token is only valid for 15 minutes. Refresh-token rotation is left out for now.

---

## Author

**Phan Đức Trọng**, Android Developer · GitHub: [pahntd](https://github.com/pahntd)
