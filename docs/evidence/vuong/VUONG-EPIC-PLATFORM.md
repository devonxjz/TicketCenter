# VUONG integration — Task 3 platform isolation

Baseline: integration plan commit `0f32844cce0419f62d456e6dd5e5b2cad249862a`.
Worktree: `vuong-platform-test-isolation`; branch `feat/vuong/vuong-epic-platform-isolation`.
Implementation gate commit: `c0f9b31` (skip-ci). CI changes follow as a separate commit.

## Observed verification, 10 October 2026

| Check | Observed result |
|---|---|
| Harmless engine gate probes before implementation | 5 assertions failed: setup executed without gate; no DB or mail work in probes |
| Gate/config/target policy tests | 13 passed, 0 failures/errors/skips |
| Environment credential resolution regression | Initially 1 assertion failed, then 3 configuration cases passed |
| Report skipped-method regression | Initially 1 assertion failed (preceding self-closing pure case incorrectly labeled skipped), then corrected |
| Bare `mvn -B test`, final local task baseline | 205 discovered, 182 executed, 23 external skips, 0 failures/errors |
| Safe clean PowerShell launcher before environment addition | 202 discovered, 179 executed, 23 external skips; Java and Node passed |
| Node initializer/report tests | 14 passed, 0 failures/skips (includes missing required suite RED → GREEN) |
| Explicit DB gate probe on authorized private UUID fixture | 1 real `JpaConnectionTest.testDatabaseConnectivity` passed, 0 failures/errors/skips; read-only marker preflight and JDBC query only |
| Workflow syntax/static validation | All 7 workflows accepted by actionlint 1.7.7, shellcheck/pyflakes integrations disabled; YAML parsing also passed |
| Locked-source check | `pom.xml` and `src/main/java` unchanged from Task 3 baseline |
| Whitespace check | `git diff --check` passed |

The real JDBC probe used private environment authentication supplied by the parent task, target `TC_Vuong_Test_20261010_Finance`, loopback and `loginTimeout=5`. Credential values were never placed in commands, output or committed files. An initial probe invocation failed because its output file was held inside the directory that Maven clean attempted to remove; moving capture outside that directory corrected the launcher invocation. This was not a failed DB assertion and no DB work occurred in that initial attempt.

## Delivered behavior

Auto-discovered JUnit execution conditions disable known DB/SMTP tests before class setup. New tagged DB tests and IT classes receive the same gate. Mixed classes retain pure methods. Explicit DB activation validates the dedicated name, loopback URL, bounded login timeout and fixture marker; missing activation configuration fails before setup. Private credentials can be bound through `DB_*` and normalized SMTP environment keys without argv/report property secrets. Default Maven and launcher execute no DB or SMTP paths.

CI no longer selects absent DayXXIT names or unconditionally claims tests passed. Safe default checks produce actual report counts and named skipped methods. Manual disposable DB job requires actual finance/read-side suite source files and disallows zero execution/skips. Publishing/login/latest require explicit dispatch on main/dev. HTTP smoke uses selective isolated deployment with no shared persistence listener/seed/worker. Production is a manually dispatched protected simulation; automatic release/tag publication removed.

## Gates still open

GitHub workflows were not dispatched, pushed or deployed in this task. The isolated HTTP smoke workflow was statically validated, not run here. Owner JPA/seed suites require compatible owner base schema separate from the finance-only fixture. Full SMTP, real provider, owner catalog login flow, E2E browser roles, SQL finance permissions/concurrency and CSV Excel acceptance are not established by this task's default suite or single JDBC connection probe. No schema migration/data mutation was run by this platform worker.

Ruling: keep finance/read-side DB acceptance explicit/manual and keep legacy owner JPA suites separately pending — the new UUID fixture is not the full owner database — cost if wrong: future CI needs additional compatible owner fixture work rather than broadening defaults unsafely.

Final self-check reviewed trigger guards, class/method setup ordering, env authentication precedence, output redaction and report skip counting. Parent task performs integration review; no additional subagent was spawned.
