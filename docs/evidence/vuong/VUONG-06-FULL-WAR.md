# Full WAR, real identity and finance acceptance

Scope of #33/#28, based on dev `c75f41ce4834cac10a1ac241accfd7e21a4f55bc`. This adds a full annotation deployment probe and fixes a real startup blocker. It does not complete the cross-owner purchase/payment/ticket/check-in/refund chain or change the whole-task total of 4/6.

## Failure and correction

An unmodified full annotation deployment of the previous WAR failed before listener startup: Tomcat reported `EventApiServlet` and `FinanceReportServlet` both mapped to `/api/organizations/*`. The existing isolated HTTP tests did not deploy the Event servlet and could not expose this collision. The new `ServletMappingTest` failed on that same duplicate before production code changed.

ReportServlet now retains its report/export/options mappings and its named dispatcher. A REQUEST-only organization filter dispatches only reports/overview operations to that name and passes Event creation and other owners' paths through. It passes the original organization route as a request attribute, so both context roots and named forwarding preserve UUID/path validation. Unsupported readside methods return405/AllowGET. An unavailable dispatcher fails closed with503. The existing SQL authorization checks still run in the Report/Dashboard service.

The bounded full WAR utility keeps real annotation scanning, all listeners, six persistence principals, Hibernate schema validation, real login/session/CSRF and scheduled cancellation/refund workers. There is no fixture session servlet or deployment descriptor disabling listeners. It requires a marked `TC_Vuong_Test_*` database on loopback, checks its marker through local Windows authentication before startup and replaces runtime configuration with private local-only values. External mail/payment/image configuration is removed; provider endpoints point to loopback and payout is unavailable. The SQL login is a private test db_owner for all principals: this verifies wiring, not production least-privilege grants.

## Runtime prerequisite

The locked POM does not package JSTL. With plain Tomcat the actual `/auth/login` JSP failed with `Unable to get JAR resource ... jakarta.tags.core`. The test utility explicitly installs locally supplied Jakarta Tags API and GlassFish JSTL3.0.1 in its private Tomcat base. No POM, model, persistence configuration or installed Tomcat is edited. A deployment must likewise provision these runtime jars; a WAR on bare Tomcat alone is not sufficient. Existing owner runtime guidance uses the same dependencies in `docs/references/EVENT-IMAGES-VERIFY.md`.

## Reproduction

Build the WAR, then run with PowerShell7 on Windows:

```powershell
./src/test/scripts/full-war-smoke.ps1 `
  -ConfigPath '<private fixture JSON>' `
  -TomcatHome '<Tomcat11 home>' -JavaHome '<JDK25 home>' `
  -JstlApiJar '<jakarta.servlet.jsp.jstl-api-3.0.1.jar>' `
  -JstlImplementationJar '<jakarta.servlet.jsp.jstl-3.0.1.jar>'
```

Private JSON fields are `database,url,user,password`; never commit it. The JDBC URL must name the same dedicated database at127.0.0.1:1433. The database needs the UUID team snapshot, finance migrations027 and report migration028 already applied. The utility seeds a random-password acceptance ADMIN and the application's existing demo accounts only in this marked test database. It stops its own JVM and removes credential-bearing configuration/auth copies after the probe. Logs remain under a unique ignored `target/full-war-smoke` directory. The Node suite is opt-in via the utility; an unset fixture is a named skip, never full-WAR evidence.

## Verification

- Mapping RED reproduced the actual duplicate; clean package GREEN:395discovered,335executed,60guarded external skips, zero failure/error. The four added cases cover uniqueness and owned route dispatch/pass-through/failure behavior.
- Full WAR against local team-schema clone `TC_Vuong_Test_20261010_TeamSchema_Sqlcmd`: readiness and real identity succeeded with Hibernate validation enabled. Real ADMIN/MANAGER/STAFF/CUSTOMER sessions, login JSP, financial JSPs, SQL-backed report/overview/audit, CSV authorization, tenant/path validation, session/CSRF rotation, denied mutation withoutCSRF, logout and owner Event route coexistence are tested.
- Existing isolated finance HTTP/JDBC/JSP regression20/20 without skips after switching its fixture to the same named route dispatcher. This remains separate evidence with a test-only session bridge and does not replace the full-WAR probe.

Final fresh local counts and exact-head/postmerge CI links are recorded in the associated PR. Full cross-domain E2E and real external provider acceptance remain unchecked in #33 and epic34.

Fresh full WAR16/16 and URL safety7/7 passed with zero skips. The SQL Server driver's duplicate database/server properties were identified during review; the utility rejects duplicates/target overrides and constructs its own canonical JDBC URL instead of forwarding caller URL properties. Safe Node56/56 also passed.
