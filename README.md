# TicketsCenter

Backend Java 25/Servlet 6.1 cho quản lý Event và bán vé. Build tạo `target/ticketscenter.war`; SQL Server là source of truth, Redis chỉ phục vụ idempotency và cache public Event.

## Prerequisites

- JDK 25, Maven, SQL Server 2022+; Docker chỉ cần khi chạy container.
- Ba database tách biệt: development, integration và benchmark. Không chạy fixture/benchmark trên database demo hoặc dữ liệu người dùng.

## Configure

```bash
cp .env.example .env.runtime
# export values from the approved secret store; do not commit .env.runtime
mvn -B test
```

All configuration names are listed in `.env.example`. Runtime uses separate SQL identities for buyer, manager, check-in, admin, auth, and worker; migrations use `TC_SQL_MIGRATION_LOGIN` only.

## Database

```bash
database/run-migrations.sh --plan
export TC_SQL_TARGET_DB="$TC_SQL_TEST_DB"
database/run-migrations.sh
mvn -B -Psqlserver-it -Dit.test=Day19IT verify
```

The runner permits only the configured Dev/Test/Bench names and verifies migration checksums. See [database/README.md](database/README.md) and [database runbook](docs/backend/database-runbook.md). Do not pass passwords on a command line.

## Run

```bash
mvn -B package
# Deploy target/ticketscenter.war to Tomcat 11, context path /ticketscenter
curl -f http://localhost:8080/ticketscenter/health/live
curl -f http://localhost:8080/ticketscenter/health/ready
```

For Docker instructions, see [deployment](docs/backend/deployment.md). Container and Azure validation are not claimed until their respective environments are available.

## Handoff status

[Backend acceptance](docs/evidence/backend-acceptance.md) is the authoritative PASS/FAIL/BLOCKED record. Current blockers include unprovisioned local runtime-role credentials, no approved benchmark database, and an unavailable local Docker daemon.
