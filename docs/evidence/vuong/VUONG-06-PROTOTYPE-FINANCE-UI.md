# VUONG-06 live finance UI — prototype alignment

Date: 11/10/2026. Base: dev cdaa0b4. Branch:
`feat/vuong/vuong-06-prototype-finance-ui`. Scope: existing finance pages;
whole #28/#33 remain open and the epic remains 4/6.

## Shared design and ownership

The live pages use the existing `assets/css/style.css` white/red/black tokens,
typography, flat panels and dashboard navigation. Finance-only responsive
styles extend those tokens. Admin reuses the existing `layout/admin-navbar.jsp`
and `admin-notifications.js`. Navbar markup stays unchanged; notification row
removal is confined to its existing organization approval table. Manager gets its
own report navigation without Admin controls. Existing owner approval links
remain available. No owner servlet, schema, model or business contract changes.

| Existing prototype frame | Live page |
|---|---|
| UI-17 organization report | `/finance/reports` |
| UI-18 Admin overview | `/finance/overview` |
| UI-22 commission / settlement | `/finance/commission`, `/finance/settlements` |
| UI-23 system audit | `/finance/audit` |

Frame numbers describe checked-in prototype files and do not renumber issues.
Summary cards, filters, table panels, pagination, CSV help and current-page
navigation share finance JSP fragments. Existing DOM IDs, scope/CSRF attributes,
integer VND formatting, UTC boundaries, filter sequencing, idempotency and
disabled business controls retain their previous behavior.

Exact GET/HEAD menu aliases route `/admin` and `/admin/dashboard` to overview,
`/admin/settlements` to settlements, `/admin/audit-logs` to audit and
`/organizer/reports` to reports. Canonical pages still authorize against SQL.
Other methods stay with their original servlet; unrelated owner routes remain
untouched. Old static menu URLs have no supported query contract; aliases do
not carry arbitrary query parameters into financial filters.

## Actual verification

- Native Windows, JDK25, Maven3.9.16, Tomcat11.0.25, isolated restored SQL fixture.
  Fresh full WAR: 17/17, no failures/skips, actual login/session/CSRF/SQL role
  checks and five rendered JSPs. Includes GET/HEAD aliases and POST ownership.
- Safe Java package: 408 executed, 60 guarded external skips, zero failure/error.
  Safe JavaScript contracts: 59/59, zero skips. Skips are not counted as PASS.
- New rendered header/current menu/Manager-exclusion assertions first failed
  against the old WAR. Alias assertions then failed against static menu pages.
  Browser found a footer UTF-8 fragment defect, reproduced by rendered HTTP
  assertion; explicit page encoding fixed it on the fresh full WAR.
- Real browser: all five Admin pages and Manager report navigation, desktop,
  mobile and tablet; [recorded observations](prototype-ui/browser-results.json).
  Shared notification dropdown loaded actual SQL-backed queue data and kept its
  organization approval link. No browser console errors in the recorded run.
- Report EVENT_SALES, year UTC filter: 25 total rows, page1 has20, page2 has5;
  total gross remains exactly9007199254743393 VND on both pages. CSV downloaded
  from page2 contains header+25 rows, exact9007199254740993 VND and quoted tab
  formula protection for the synthetic `=SUM(1,1)` event name.
- Locked 5%+2 VND rule attached to25 events remains disabled. Previously
  confirmed settlement stays100 gross /7 commission /93 net, cannot recalculate
  or confirm again; payout remains disabled without its provider.
- Audit detail loads allowed fields, 20 rows per page; overview shows seven UTC
  daily rows. Wide tables scroll inside their panel instead of widening the page.
- Tablet navbar regression: client753px / page814px before fix; after scoped
  flex wrapping both753px. At320px viewport, client/page305px and notification
  dropdown288px fit the screen. Shared navbar markup remains unchanged.
- Fresh independent review found the shared notification approval fallback could
  remove a matching finance audit row. Actual-script regression RED:16/18, two
  failures; GREEN:18/18 after confining lookup/removal to `#pendingOrgTableBody`.
  Both attributed and legacy owner approval rows still update; finance rows stay.

Screenshots: [desktop report](prototype-ui/report-desktop.png),
[mobile report](prototype-ui/report-mobile.png),
[confirmed settlement](prototype-ui/settlement-desktop.png),
[tablet overview](prototype-ui/overview-tablet.png).
All displayed data and identifiers are synthetic local fixtures. Credentials,
raw provider responses and private database configuration are not published.

## Remaining acceptance

During delivery, dev received Liêm's Hold runtime through PR#58 (3a79b6b).
Merged that base into the finance branch without conflicts; the reviewed finance
file contents stayed identical. Fresh combined Native package: 435 Java cases
executed/60 guarded external skips, zero failure/error; all89 JavaScript cases
(59 finance/shared checks +30 Hold checks) and full WAR17/17 passed. Earlier408
and59 counts above record the original finance revision, not the larger base.

This implements the finance UI slice. It does not accept all24 UI flows,
connected purchase/payment/ticket/check-in/refund/cancellation workers or real
providers. #28 still needs cross-owner OpenAPI/Redis reconciliation; #33 still
needs final connected business E2E. See [backend acceptance](../backend-acceptance.md).
