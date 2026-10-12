# VUONG-01/06: Event SQL and Admin Refund integration

This records a scoped integration, not final acceptance of #28/#33 or the whole product.

## Source and migration adoption

- Base: dev`80079d11504f136b32c8333a65aac74707e9b992`, including merged Event PR51 and LIEM runtime PR69.
- Event SQL PR56 head`5ece780ba4d0df12252af8abe958d529ec634f05`; incorporated at`78ceb32fc03b18d11e45386634f2cf5844c912a4`.
- Admin Refund PR68 head`7d5358bbd9dba63f0fdd0fb07815b032ecd0104b`; incorporated at`bab5c5e8a4d35346720f2c73cb07808785148589`.
- Execution plan: [Native plan](../../superpowers/plans/2026-10-12-refund-event-integration.md).

Install canonical001–008 first, then retained029 Hold/refund and030 Order/signed check-in, then031–039, followed by the explicit complete identifiers:

1. `040_payment_ticket_runtime_reads.sql`
2. `041_hold_refund_runtime_compatibility.sql`
3. `042_checkin_event_user_lock_order.sql`
4. `043_coupon_admin.sql`
5. `044_buyer_order_history_views.sql`
6. `043_khanh04_admin_refund.sql`
7. `045_event_commands_and_public_seats.sql`

Keep the deployed adapter/image prerequisites for the shared schema; do not reinstall historical functions after their later refinements. The ledger key is the complete basename with checksum. Preserve the two043 domain identities: Khánh reports refund043 already installed on Azure; coupon043/history044 are merged. Only the pending Event043 is adopted as045. Installed bytes are not changed. Event045 SHA256 (LF): `499dc87fe6a55d52f16bc23f3e05ea4292b5225f8e1204b06536c4904cca6b3f`.

All local writes in this run use owned loopback fixtures. No shared database schema/business rows, firewall or existing credentials are changed. Separate OWNER/BUYER/ADMIN test logins are recorded privately with Windows-protected passwords. Actual BUYER login proves public Event projection permissions; other Event mutation cases impersonate restricted database users over the dedicated owner connection.

## Verification ledger

| Check | Observed result |
|---|---|
| Clean latest-dev baseline |823 discovered,718 executed,105 guarded external skips;0 failures/errors |
| Linked Event read → real Hold/Order facades → worker capture → ticket → customer refund → separate Admin approval/replay → settlement blocker |1/1 actual SQL; no skip. RED before Event contract installation, GREEN after explicit adoption |
| Dedicated connection authentication/target guards |5/5; rejects remote/shared targets, JDBC overrides, invalid ports, missing/mixed credentials; credentials redacted. New helper compile RED→GREEN |
| Event SQL/Redis and actual restricted BUYER catalogue |18/18:16 Event mutations/races/cache cases,1 catalogue login/projection/pagination/cache case,1 full-artifact migration apply/reapply preserving receipts/cleanup/grants;0 skips |
| Combined payment/report/check-in/QR/Admin Refund/Redis |58/58 actual cases,0 failures/errors/skips |
| Public Event and retained Admin/check-in OpenAPI contracts |8/8. Public Event mismatch RED: absent typed schemas and obsolete source/owner/pagination metadata; GREEN after matching actual DTOs |
| Complete combined package |871 discovered,749 executed,122 guarded external skips;0 failures/errors. Stale pre-delivery zone-is-planned assertion corrected to retain the undelivered cancellation gate |
| Full combined WAR with actual restricted BUYER/ADMIN logins |47 HTTP/JSP checks +12 headless browser checks pass, including public Event, own-order history/detail, current Admin authority, committed-response replay, retry and stored-text escaping |
| JavaScript and Native guards on the combined worktree |163/163,0 failures/skips |
| Native full financial WAR |17/17 actual auth/session/CSRF/finance/CSV/JSP checks,0 skips; Event JSON validation and shared unsupported-media rejection checked separately |

`RefundEventSqlIntegrationTest` uses a seeded published Event; actual Event creation/publication and concurrency are exercised separately in the16 Event cases. Payment capture is an authoritative-result fixture call, not a live VNPay signature/provider acceptance. Admin approval queues one PENDING attempt, leaves tickets REFUND_PENDING and creates a real settlement blocker; no real transfer is performed.

Initial Event acceptance could not authenticate via Windows TCP on this machine. The SQL-login alternative tightens the target allowlist and keeps credentials outside JDBC URLs; it does not change server authentication policy. Two catalogue seed mistakes (SEATED capacity must be NULL, seat label required) were rolled back/corrected after inspecting constraints; they are fixture mistakes, not application bugs. Reused scan-fixture expired holds were drained through the real bounded worker before its expiry test; clean CI creates fresh databases.

Fixture caches must not be shared across databases: Scan/Admin Redis16379 and Event Redis16479 are dedicated. Native financial smoke uses an unoccupied16480 (SQL fallback; finance Redis disabled), preserving the default Native serve port16379. Rapid Native reseeds with live shared cache produced stale session validation; isolating the fixture restored17/17. This does not accept production seeder/cache invalidation. Native clean recreates target, so browser evidence is copied outside target before further cleans.

The local Tomcat template contained unrelated exercise descriptors. The test JVM was stopped; the harness now excludes those descriptors and emits one owned loopback NIO2 host, matching established Native smoke. Its previous NIO selector pipe failed to create a loopback socket on Windows/JDK25; an IPv4 flag alone did not fix it. No original Tomcat configuration or server network policy was changed.

## Delivered contract scope

Five public Event operations now have typed success schemas matching their actual serializers: list/detail/zones/seats/categories. Filters, stable UUID tiebreaker, paging limits, UUID strings, exact integer VND strings and UTC aliases are documented. Inventory is rechecked by mutations. Source owner is Dong; older no-pagination/Liem catalogue metadata is superseded.

The Redis registry records actual Event TTL30/120/30/30/300, generation invalidation, Event processing/completed leases120/86400 and Admin Refund decision cache86400. Current DB authority precedes replay; cache writes follow commit. Full cross-domain inventory/KPI hook coverage remains a separate gate.

## Remaining acceptance gates

Native finance HTTP, independent review and exact-head/postmerge CI evidence will be recorded in the integration PR and scoped epic update only after they actually finish.

- #28: remaining response-schema/envelope reconciliation across owners, legacy Order read payloads/numeric money, auth namespace/TTL policy, QR caching policy and complete cross-domain invalidation.
- #33: full-product UI consistency/flows, actual production-principal configuration, shared rollout, live Cloudinary/VNPay providers, refund worker/reconciliation/cancellation and final settlement/payout transfers. Independent provider evidence is historical unless rerun on this combined revision.
- Blue standalone Admin Refund styling is the owner's delivered UI; the earlier prototype-aligned finance pages do not prove every owner's screen is aligned.

Whole-task completion remains4/6. Do not close #28/#33 based on this slice or on skipped external jobs.

## Rulings and review

- Ruling: preserve adopted043 identifiers and renumber only pending Event to045; complete-basename ledger and checksums are authoritative. Cost if wrong: shared rollout-order reconciliation; never rewrite installed history.
- Ruling: add explicitly guarded SQL authentication for the dedicated Event fixture because Windows TCP authentication fails here. Cost if wrong: this transport does not prove a production login deployment; actual BUYER login and database-role assertions are separately identified.

Fresh read-only whole-branch review found three Important issues and one Minor issue. All received one fix pass:

| Finding | Repair | Observed verification |
|---|---|---|
| Refund trusted legacy ADMIN grants | Additive046 uses current `tc_users.platform_role`, including underlying review; preserve043 | Demotion/current ADMIN tests RED→GREEN;18 actual Refund cases pass |
| Event retained User before waiting for Event | Additive047 resolves/locks Event before retaining actor authority; preflight retains no User lock | Command409/direct publication1205/read lock conflict RED→GREEN;21 actual Event cases pass |
| Completed Refund replay skipped post-wait authority |046 validates status/auth version/current role after actor/receipt waits | Original043 replay returned a receipt after revocation; repaired path401, durable receipt/attempt count unchanged |
| Rollout recovery snapshot discovered no definitions | Restore regex whitespace and GO escapes; refuse missing expected definitions | Seven043 definitions and nine batches discovered; shared rollout script not executed |

Replay gate fixture uses the uppercase SQL UUID resource and an active transaction, matching the procedure's application lock. Early fixture-only failures were not counted as a product regression. The direct underlying review denial is tested with owner transport; restricted ADMIN facade/list and actual BUYER/ADMIN WAR scope are separately covered.

Ruling: append046 after refund043 and047 after Event045; historical/adopted migration bytes remain intact. The shared rollout must include repairs before acceptance. Local/CI execution is not evidence that the shared Azure database was migrated. No deferred review minors.

The combined review-fix run executed18 Refund +21 Event cases with zero failures/errors/skips. Incorporate dev79a963a (new PR70/71/72 and merged PR68 latest02534f1) before full exact-head CI and re-evaluate affected checks.
