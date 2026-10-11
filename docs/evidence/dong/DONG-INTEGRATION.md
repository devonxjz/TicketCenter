# Dong integration contract — DONG-01–05 / #13 / #45

This is the concrete owner handoff for PR #51. It describes implemented source,
not a claim that the shared database, OpenAPI or all cross-domain flows have been
deployed. Adopt the SQL coordination PR before enabling the Event management API.

## HTTP and routing

All IDs are UUID strings, money is an integer VND string, dates are ISO-8601 UTC.
Success responses use `data`; list pagination is alongside `data`.

| Method and path | Access / payload / result |
|---|---|
| GET `/api/events` | Guest; `keyword` max100, `categoryId`, UTC `from` inclusive / `to` exclusive, `sort`, `page`, `pageSize` default12/max50. PUBLISHED only; min/max price and sale availability. |
| GET `/api/events/{eventId}` | Guest; PUBLISHED detail. `eventId` plus compatible `id`, `saleStartTime`/`saleEndTime` plus `saleStart`/`saleEnd`. |
| GET `/api/events/{eventId}/zones` | Guest; PUBLISHED inventory projection, standing quantity or seated availability. |
| GET `/api/zones/{zoneId}/seats` | Guest; PUBLISHED seat UUID/row/number/label/status only. No customer/hold/order identifiers. |
| GET `/api/event-categories` | Guest; category UUID/name/slug. |
| GET `/api/events/{eventId}/image` | Guest; current PUBLISHED cover redirect302, no-store. Missing/hidden Event404. |
| GET `/api/event-organizations` | Current ACTIVE approved-organization MANAGER; selectable organizations. |
| GET `/api/organizations/{organizationId}/events` | Current MANAGER of this organization; `status`, `page`, `pageSize` default20/max100; includes private drafts/rejection reason. |
| GET `/api/organizations/{organizationId}/events/{eventId}` | Current MANAGER; parent ownership validated. |
| POST `/api/organizations/{organizationId}/events` | Current MANAGER. JSON draft returns201; multipart uses existing immutable image workflow and returns200, generates category artwork when file omitted. |
| POST `/api/events/{eventId}/edit` | MANAGER; JSON partial edits, optional `expectedVersion`. After publish JSON allows description only. Multipart full edits use existing image intent contract. |
| POST `/api/events/{eventId}/delete` | MANAGER; draft only, no transactions; physical deletion is atomic with children and receipt. |
| POST `/api/events/{eventId}/submit` | MANAGER; draft/rejected, valid schedule and nonempty inventory; pending locks edits. |
| GET `/api/events/{eventId}/layout` | MANAGER; private zone/seat layout. |
| POST `/api/events/{eventId}/zones` | MANAGER; `name`, `zoneType`, `basePrice`; SEATED requires `rows`/`seatsPerRow`, STANDING requires `capacity`. Returns201. |
| POST `/api/zones/{zoneId}/edit` | MANAGER; capacity cannot fall below allocated inventory; layout immutable after publish, price may change. |
| POST `/api/zones/{zoneId}/delete` | MANAGER; draft/rejected, unallocated inventory and no FK-dependent transactions. |
| POST `/api/events/{eventId}/cover` | MANAGER; multipart `file`. With `expectedVersion`, use merged immutable image workflow, including its published-cover exception; file-only compatibility is draft/rejected. |
| GET `/api/admin/events` and `/api/admin/events/{eventId}` | Current ACTIVE ADMIN; list/details, same pagination/status parameters. |
| GET `/api/admin/events/{eventId}/layout` | ADMIN; full review inventory. |
| GET `/api/admin/events/{eventId}/commission-rules` | ADMIN; same-org rules effective now and at Event start. |
| POST `/api/admin/events/{eventId}/publish` | ADMIN; `commissionRuleId`, optional `adminNote`/`expectedVersion`; canonical `usp_PublishEvent`. |
| POST `/api/admin/events/{eventId}/reject` | ADMIN; `reason`10–500, optional `expectedVersion`; pending only. |

JSON create fields: `title`5–200, `description`1–5000, `categoryId`,
`venueName`, `venueAddress`, `saleStartTime`, `saleEndTime`, `startTime`, `endTime`.
Schedule: now < saleStart < saleEnd <= start < end. Multipart uses the existing
`saleStart`/`saleEnd` names and UUID `Idempotency-Key` plus optional PNG/JPEG/WEBP
`file` (max5MiB,12million pixels). Seat matrix max1000 rows/columns and10000 seats;
standing capacity max10000000.

Every mutation needs the current authenticated session, `X-CSRF-Token` and
`Idempotency-Key`. JSON commands accept case-sensitive `[A-Za-z0-9._:-]{1,128}`;
immutable image requests use UUID keys. Hashes include normalized operation,
resource and scope. Replay rechecks current SQL rights; changed payload409.
Private responses use no-store and never cache permission decisions. Detail
ignores list status/page filters; disappearance returns404. Unknown category on
create/edit returns400 without mutation/receipt. Oversized versioned multipart
returns413 before image intent/provider work.

Catalog errors: `INVALID_EVENT_QUERY`400, `EVENT_NOT_FOUND`404,
`EVENT_CATALOG_UNAVAILABLE`503. Management errors include `AUTH_REQUIRED`401,
`SESSION_EXPIRED`401, `CSRF_TOKEN_INVALID`403, `EVENT_FORBIDDEN`403,
`INVALID_EVENT_INPUT`400, `EVENT_CONFLICT`409 and `EVENT_DEPENDENCY_UNAVAILABLE`503.
Image workflow retains its existing `EventException` error codes; coordinator
must document both protocols rather than change their public errors silently.

After dev PR54, EventApiServlet owns `/api/organizations/*` and its multipart
configuration. EventManagementRoutesFilter claims only Event paths; Vuong's
OrganizationReadsideRoutesFilter forwards reports/overview to the named
FinanceReportServlet. Catalog owns public Event/Zone reads. No Event listener
changes Report's multipart configuration. Full annotation WAR acceptance checks
create/upload and organization reports/overview in the same deployment.

## Redis contract and cross-owner invalidation

| Key | Payload / TTL |
|---|---|
| `tc:event:list:v1:<filterHash>` | Public paginated JSON /30s |
| `tc:event:detail:v1:<eventId>` | Public detail JSON /120s |
| `tc:event:zones:v1:<eventId>` | Public inventory JSON /30s |
| `tc:zone:seats:v1:<zoneId>` | Public seat JSON /30s |
| `tc:event:categories:v1` | Lookup JSON /300s |
| `tc:event:cache-generation:v1` | Monotonic generation, no expiry; compare on refill. |
| `tc:idem:event:<actorId>:<key>` | JSON commands; NX lease120s, completed receipt24h. |
| `tc:idem:event-review:<actorId>:<key>` | Admin commands; same lease/receipt. |
| `tc:idem:event-image:<actorId>:<requestUUID>` | Immutable image coordination; same lease/receipt, durable SQL intent authoritative. |

No private organization/admin read cache is produced. Invalidation also removes
retained private namespaces if an older deployment used them. Redis failure on
GET falls back to SQL; SQL errors are not cached. New Event writes requiring a
Redis lease fail503 before the business mutation when Redis is unavailable;
durable SQL replay still works. Postcommit Redis completion failure never turns a
committed write into an error.

The dependency for other owners is the public `EventCacheInvalidator` interface.
At application startup the servlet-context singleton is obtained with
`EventCatalogRuntime.cache(context)`. Inject that same instance into an owner
service; call `invalidateCommitted(eventId, organizationId)` only after its outer
DB transaction has returned successfully. Worker processes may instantiate
`RedisEventCatalogCache` with their configured Redis provider; keys/generation
are shared. Never call before commit or while provider I/O holds a DB transaction.

| Owner | Commit that requires Event invalidation |
|---|---|
| Liêm | Hold allocation/release/expiry/consume, ticket issuance or another seat/standing inventory change. |
| Khánh | Completed refund inventory return, cancellation status/inventory changes; check-in if that path changes Seat status exposed publicly. |
| Lookup owner | Category changes also delete `tc:event:categories:v1`; Event mutation does not delete stable lookups. |

Generation increments before scanning old keys, preventing stale in-flight
reads from refilling. Requested/completed epochs prevent an older scan from
clearing a newer failed invalidation. Pending work makes the shared instance
bypass cache and retry. Hard crashes/multiple nodes still require TTL bounds or a durable
cross-domain invalidation mechanism; this handoff does not claim one is present.
The owner hooks above are integration obligations, not changes to their code.

## SQL and deployment handoff

Issue #45 covers public Seat projection/grant, scoped read/authorize/command/
receipt procedures and durable receipts. The companion SQL proposal keeps SP12
as the only publication path, but aligns its authority with current ACTIVE
`tc_users.platform_role`, rather than the stale legacy role table. It also checks
approved organization and rule validity at Event start. It does not synchronize
or grant any user role. Other legacy procedures are outside this change.

New draft deletions atomically retain private cleanup_json in the SQL receipt.
Runtime journals it after commit and again on authorized replay; journal failure
preserves successful deletion and recovery data. This fifth JDBC column never
appears in HTTP JSON. Verify all asset references before provider deletion;
automatic deletion of compatibility assets is not claimed. Pair updated SQL and
caller during adoption.

Apply the separately reviewed new Event migration after baseline027/028 and
before deploying PR51. Do not rewrite applied migrations, apply this proposal to
shared/production DBs without integration approval, or reuse the customer-refund
PR50's proposed029 number. Smoke buyer PUBLISHED-only Seat projection and
manager/admin read/execute through the actual deployment principals. Dedicated
acceptance uses restricted database-user impersonation over Windows transport;
that is not proof of production-login grants.

Update12 October: the pending Event file is043_event_commands_and_public_seats.sql.
Current dev has029_hold_runtime/030_order_runtime; PR65/67 reserve031–042 and
also contain common image/actor/routing integration work. Review the combined
deployment before adoption; current20-test acceptance does not cover all those
unmerged migrations. EventManagementRepository now scopes each JDBC work item to
its authenticated actor and restores the previous connection context; Event SQL
command/SP12 restore that context too. No common persistence/config edits were made.

Deployment also needs existing JSTL API3.0.0/implementation3.0.1, configured
Cloudinary secrets, Redis and a persistent writable orphan-journal directory.
The Event PR does not edit locked POM/config/persistence/OpenAPI or deploy to a
shared database. All contract updates above are ready for the platform owner to
adopt; final merge/review and shared deploy remain separate from local evidence.
