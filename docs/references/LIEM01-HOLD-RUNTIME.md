# Hold runtime contract

The Hold API uses the existing session cookie and `UserPrincipal.authVersion`. SQL checks the current account version, ACTIVE status, email verification, and Hold ownership. Redis never authorizes a request or proves an inventory commit.

`POST /api/holds` accepts the strict `eventId` and `selections` body. A selection contains a canonical Zone UUID, an optional Seat UUID, and a quantity. The server accepts one to eight tickets from one Event. SQL reads prices and sets a lifetime of exactly 600 seconds after the lifecycle and inventory locks.

Mutations require a nonzero UUID `Idempotency-Key` and the session's `X-CSRF-Token`. Create returns HTTP 201. Cancel accepts `{}` at `POST /api/holds/{holdId}/cancel` and returns HTTP 200. The body contains `id`, `eventId`, `createdAt`, `expiresAt`, `outcome`, and `items`. Prices are integer VND strings throughout the DECIMAL(19,0) range. UUIDs and timestamps come from SQL. Snapshots also contain `eventTitle`, item `zoneName`, and nullable `seatLabel` for display. The durable receipt retains those exact labels on replay.

A mutation response is an immutable historical receipt. Repeating the same normalized command and key returns the same body and status after expiry or cancellation. A changed command with that actor's key returns HTTP 409. Create and cancel share the same key namespace. Retain the original key and command after HTTP 503 or an uncertain reply.

`GET /api/me/hold` returns `serverTime` and either `hold: null` or a currently ACTIVE, unexpired SQL Hold. Reloads read this endpoint. `GET /api/hold-options?eventId={UUID}` returns current sellable inventory and canonical Zone and Seat UUIDs. Catalog reads accept Events with more than 1,000 seats. Selection does not reserve stock.

Responses use UTF-8 and `Cache-Control: no-store`. Success bodies are bare JSON objects. The Hold servlet returns errors under `error.code` and `error.message`. Shared filters can return the existing codes `CSRF_TOKEN_INVALID`, `PAYLOAD_TOO_LARGE`, and `CONTENT_TYPE_INVALID` before the servlet runs. Those correspond to HTTP 403, 413, and 415. Their existing error bodies also contain `correlationId`.

## SQL and Redis ownership

`HoldRepository` invokes `usp_CreateHoldRequest`, `usp_CancelHoldRequest`, `usp_ReadActiveHold`, and `usp_ReadHoldOptions` through BUYER. It invokes `usp_ExpireHolds` through WORKER. SQL owns each transaction and commit. Java does not wrap a facade in an outer transaction.

The SQL dependency serializes the durable actor, `HOLD_MUTATION`, and request-key tuple. It locks Events in SQL Server UUID order, then the actor, Holds, Orders, Zones, and Seats. Create discovers the old and new Event union before locking. A changed preflight rolls back and retries the whole transaction. Release locks only its target. The canonical procedures always acquire their lock helper, including direct legacy callers.

JDBC retries known deadlocks, preflight changes, lock timeouts, and recoverable connection or query timeouts at most three times. Every retry invokes the same facade with the same actor, command, key, and fingerprint. SQL serializes recovery behind an unknown original commit. Other SQL failures return HTTP 503 with safe text.

Redis keys are `tc:idem:hold:{actor}:{key}` and `tc:user:active-hold:{actor}`. Pending entries, receipts, and markers expire within 600 seconds. Active projection retention also ends at the SQL expiry instant. SQL retains the immutable request receipt independently of Redis.

Current reads always return the SQL body. The cache fill token comes from the lookup before that SQL read. A concurrent committed cancel or expiry invalidates the token and prevents an old reader from filling it. Mutations update Redis only after a confirmed SQL result. A rolled-back mutation leaves the cache unchanged. Redis failure falls back to the durable SQL contract. SQL failure returns 503 even when Redis has a matching receipt or projection.

`HoldRuntime` owns one BUYER pool and one WORKER pool, each with a maximum size of one and the shared connection budget. It requires explicit `db.buyer.user`, `db.buyer.password`, `db.worker.user`, and `db.worker.password`. It uses the existing `db.url` and Redis configuration keys. It does not fall back to the shared database login.

The annotated listener starts expiry before the first buyer request, processes up to 50 candidates, and schedules another run after five seconds. Release preserves CONSUMED Holds. Shutdown stops scheduling, closes its pools, and prevents a later resolver from recreating them.

## Required database dependency

The backend requires the narrow SQL facade dependency. The proposal is currently local and excluded at `.codex/liem-01/completion/029_hold_runtime.sql`. Issue14 forbids LIEM database publication, so the proposal requires an approved scope exception or database-owner handoff before it becomes a tracked migration. Java runtime completion does not establish that the production database has installed this dependency.

The add-on needs the generic request ledger, missing Hold audit timestamps, the five narrow facade procedures, corrected canonical create and release locking, and explicit facade EXECUTE grants. It retains legacy positional signatures through optional output and result-suppression arguments. Runtime principals receive no new base-table inventory grants.

The guarded local verifier records `LIEM01_LOCAL_PROPOSAL_{sha256}` in `tc_schema_migrations`. The checksum is SHA-256 over LF-normalized UTF-8 source. These records describe isolated test installations. An approved canonical installation needs its own stable migration version, checksum, installation record, and owner review.

## Run the guarded local verification

Create the canonical development schema in the dedicated local SQL Server database `TicketCenter_LIEM01_Test`. Supply an extracted Redis runtime directory and the approved or local proposal file explicitly. The runner uses Windows authentication only for local setup and temporary fixture-principal creation.

```powershell
& src/test/resources/ticketing/verify-local.ps1 `
	-RedisDirectory 'D:/path/to/Redis-runtime' `
	-MigrationPath '.codex/liem-01/completion/029_hold_runtime.sql' `
	-TcpPort 63657
```

The runner rejects non-loopback JDBC targets, another database name, missing role credentials, unguarded test selectors, and dependency scripts that change the database or include another script. It creates separate temporary OWNER, BUYER, and WORKER logins and a dedicated loopback Redis process. It removes those principals and shuts down that process afterward. Fixture cleanup deletes only its actor, Event, inventory, Order, payment, ledger, audit, and Redis records.

The allowlist includes the Hold DTO, parser, expiry-policy, Redis, SQL, HTTP, and runtime suites. Every selected suite must have passing tests with zero skips. The runner stores the revision, dependency checksum, migration log, test log, and WAR build log under excluded `.codex/liem-01/completion/verification-*`.

SQL tests cover ten buyers competing for one Seat, standing capacity, whole-selection rollback, one actor with two keys and Events, same-key races, changed payloads, cross-operation keys, lost committed replies, Redis loss and outage, SQL outage, current authorization on replay, foreign and unknown Holds, sale and Hold expiry crossed during lock waits, expired cross-Event replacement, an actual payment deadlock, a changed preflight Event union, duplicate workers, CONSUMED protection, cache rollback, stale fills, full 19-digit prices, a 1,101-seat catalog, and selected A1/Floor labels retained in historical replay.

The HTTP suite uses real Tomcat11 requests, cookies, shared CSRF and request-validation filters, and SQL current authorization. Its session endpoint is a test fixture. It does not prove normal application login or the complete packaged WAR startup. The runtime suite starts the actual listener and observes expiry before any HTTP request, then verifies pool shutdown. Full normal login, packaged WAR, and browser acceptance are separate integration checks.
