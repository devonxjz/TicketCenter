# LIEM-01 Hold verification

Verification on 2026-10-11 covers code revision `dacd8f36e9480210b0326dd335758e65b8ebbf29`, based on merged `dev` revision `28ce056b97d778edcdf99b91e856c017b5c35e7b`.

Buyers can select standing tickets and seats, create a ten-minute Hold, reload its current state, and cancel it. The annotated worker releases expired inventory. SQL determines inventory, prices, authorization, and durable request replay. Redis failure permits recovery from SQL. SQL failure returns 503.

## Observed checks

| Check | Result | Scope |
| --- | --- | --- |
| Default `mvn -B test` | 413 tests, zero failures or errors, 59 skipped | External opt-in remains disabled. Skipped cases are not acceptance evidence. |
| Guarded Hold verifier | 76 tests, zero failures, errors, or skips | Actual localhost SQL Server, Redis, Tomcat HTTP, and runtime listener. Includes 18 SQL cases, three HTTP cases, and one listener case. |
| Local Node test files | 80 tests, zero failures or skips | Includes 30 Hold UI tests. Two separate HTTP smoke scripts were excluded. |
| WAR package | Passed | Current scoped source. Packaging alone does not prove unmodified application startup. |
| Browser with normal login | Passed with the proposals below | Existing listeners, filters, real password login form, and restricted AUTH, BUYER, and WORKER principals. |

The browser selected A1 at 200,000 VND and two Floor tickets at 150,000 VND. Selection and the committed Hold showed 500,000 VND. Checkout and reload retained the same Hold and server-based countdown. Cancellation in a second tab removed the current Hold after the first tab refreshed. SQL confirmed the original expiry interval was exactly 600,000 milliseconds and standing inventory returned.

A second Hold began with the same 600,000 millisecond SQL interval. The verifier shortened only that disposable fixture's expiry. The UI returned to selection after expiry. The actual scheduled worker released inventory and produced exactly one release audit. A refreshed catalog showed all eight Floor tickets available. This acceleration does not measure ten minutes of wall-clock browser activity.

The narrow-screen screenshot was visually inspected. The browser reported a 448-pixel CSS viewport despite the requested 390-pixel override. It does not establish exact 390-pixel acceptance.

## Required proposals and acceptance limits

Issue [LIEM-01](https://github.com/devonxjz/TicketCenter/issues/14) prohibits database, model, config, OpenAPI, and Event inventory edits. The following dependencies remain excluded local proposals pending a scope exception or owner handoff:

- `029_hold_runtime.sql` provides durable Hold request facades, canonical create/release locking, snapshots, expiry, and minimal role grants. LF-normalized UTF-8 SHA-256 is `dac85d2eab69f941c562843e6281ce324add5aff6b75331bcce3c28ab2d6c625`.
- `startup-route-fix.patch` resolves duplicate `/api/organizations/*` mappings between Event and report servlets. Without it, the full annotated WAR cannot start.
- `jstl-dependency-proposal.patch` adds JSTL API and implementation 3.0.1. Without it, the normal login JSP fails to resolve `jakarta.tags.core`.

The normal browser test ran a separate WAR with the two servlet replacements and JSTL jars. Its SHA-256 was `512867fef820496cc0ba016e3ef531e023f4e0a386d1b2a80f753498f61681da`. The SQL proposal was installed only in dedicated localhost test databases. This is conditional integration evidence, not acceptance of the unmodified WAR or proof of Azure installation.

Runtime needs explicit existing `db.buyer.user`, `db.buyer.password`, `db.worker.user`, and `db.worker.password` settings. Hold code never falls back to a privileged shared login. Other teams' Event cleanup and demo-seeding warnings remain outside this verification.

Independent UI review reproduced session-switch and stale-continuation defects, which were fixed before the final 30 UI cases passed. Backend review found the response-size limit incorrectly applied to a large seat catalog. The corrected path passed the real 1,101-seat catalog case. The final diverse-model follow-up hit its usage limit and returned no final verdict. Parent verification inspected the changes and reran all 76 guarded tests.

Temporary fixture rows and SQL principals were removed. The isolated audit trigger remains enabled. No test Java, Python, or Redis processes remained at the cleanup check. No Azure business writes, LIEM-01 push, PR, merge, or deployment occurred during this completion.

## Reproduction

Use the guarded runner described in [LIEM01-HOLD-RUNTIME.md](../references/LIEM01-HOLD-RUNTIME.md). Supply the reviewed SQL dependency and an explicit Redis runtime directory. The runner rejects an unexpected SQL database or non-loopback target and requires all selected tests to run without skips.

Detailed logs and browser images remain excluded under `.codex/liem-01/completion`. The final guarded run is `verification-20261011-093615`. The normal browser fixture scripts are local acceptance tools and are not a production installation method.
