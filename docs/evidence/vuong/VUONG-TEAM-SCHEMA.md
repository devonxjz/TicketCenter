# Team schema compatibility — 2026-10-10

Continue PR [#42](https://github.com/devonxjz/TicketCenter/pull/42) on `feat/vuong/vuong-epic-finance-integration` after read-only inspection of the team's Azure database. No migration or application data was written to Azure. Credentials remain in private local configuration and are excluded from committed fixtures/logs.

## Observed schema and installation

The schema-only local copy matched source metadata: 36 tables, 333 columns, 70 SQL modules, 61 foreign keys, 59 check constraints and 87 indexes before fixture markers/finance additions. Source row data, accounts, permissions and credentials were not copied. `002_team_schema_snapshot.sql` is a fixed test-only snapshot guarded by an empty `TC_Vuong_Test_*` database; its two marker rows enable the dedicated integration gate. Tests seed synthetic UUID-scoped data, retained only in isolated local test databases.

Install owner schema (or one of the isolated fixtures), then **027_team_schema_adapter.sql**, then **027_vuong_finance.sql**. Both migrations were reapplied successfully. The tracked snapshot was also installed from scratch in `TC_Vuong_Test_20261010_TeamSchema_Fresh`, followed by the adapter and finance migration. The canonical fixture remains supported.

Mappings: user_id/buyer_id, captured_at/paid_at, obligations rather than legacy refund attempts, physical versus computed net_payable, and stable UUID audit projection over the unchanged BIGINT identity. Legacy audit inserts use the original identity allocation. Missing commission timestamps receive migration-time defaults, not reconstructed history. Model files, persistence configuration, pom and migration026 are unchanged.

## Actual checks

* Team-schema JDBC: **9 executed, zero skips/failures/errors**. Covers actual repository recalc/confirmation, partial payout/durable provider replay, owner refund mirror/freeze, current authority/commission immutability, legacy audit filtering/redaction/detail identity, constrained database permissions, retained summary aliases/module compilation, compensation retry recovery and 10,000-order CSV aggregation.
* RED/GREEN: failed compensation followed by verified full recovery still returned REFUND before repair; the new test failed 0 expected versus1 actual. Partial recovery and UNKNOWN remain blocked. A missing ticket_gross_amount alias also failed before restoration. Both cases and the nine-case suite subsequently passed.
* CSV case: 10,000 actual SQL orders in one event, one aggregated CSV data row plus header/BOM, gross1,000,000 VND, commission70,000 VND, net930,000 VND. Final local export429ms, 254bytes; sampled heap before/after16,176,960bytes under `-Xmx128m`. The assertion bounds export heap growth below32MiB. Fixture preparation is excluded from export time. This is not evidence of 10,000 exported CSV rows or SQL Server memory usage; the existing writer tests cover long lazy row streams separately.
* Canonical regression after the review fixes:19 JDBC cases, five SQL assertion suites and six real two-session races pass.
* Default Java/package:295 discovered, **243 executed**, 52 guarded skips, zero failures/errors; WAR package succeeds. Node29/29, selective finance HTTP/JSP14/14 and Swagger HTTP10/10 pass. actionlint1.7.7 (without external shellcheck/pyflakes) and whitespace checks pass. WAR SHA256: `B4298CF04806AF64C71DF7794DD35B8B0C39CA60E2F396FCC2E3592D5666634D`.

Private transcripts: `team-schema-red.log`, `team-review-red.log`, `team-review-green.log`, `team-schema-fresh.log`, `team-regression-db/node/package/http/swagger-http.log`, five `team-regression-finance_*.log` files and `team-regression-races.log` under `%LOCALAPPDATA%/TicketCenter/verification`. Credential-bearing Surefire properties are not published as prose or committed files.

## Review decisions and remaining acceptance

The fresh review found the compensation retry blocker and missing migration-order documentation; both were repaired. Retained summary aliases compile against the new view and use the approved accepted-payment/obligation calculations. Old owner mutation procedures are not newly authorized or certified by that compilation check. Failed compensation history is excluded only after the same captured payment has full timestamped, verified current-transfer coverage; no physical obligation/evidence history is deleted or rekeyed.

Full application startup/JPA and register→event→hold→pay→check-in→settlement require the owners' model/schema alignment and operational integration. This finance JDBC adapter does not establish that full flow. Native Excel open/save/reopen acceptance is still pending. Production bank acceptance remains separate from the explicit simulated fixture provider. No issue closure, merge or deployment is implied by these tests.

Expanded Actions is configured to run both canonical19 and team-schema9 cases with zero skips, five SQL suites/six races, actual standalone Redis, packaging and isolated HTTP. The latest revision's remote run is tracked in PR42; earlier successful runs do not prove the new snapshot passes on SQL Server2022.
