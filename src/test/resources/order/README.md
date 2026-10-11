# Verify Order and Coupon behavior locally

Use `verify-local.ps1` to run the owned Order tests against SQL Server and Redis. The runner accepts only the localhost SQLExpress database `TicketCenter_LIEM02_Test`. It creates temporary OWNER, BUYER, and WORKER principals and a dedicated Redis process, then removes them after verification.

## Prerequisites

Prepare these dependencies before running the script.

- Java 25, Maven, PowerShell 7, `sqlcmd`, and a Redis directory containing `redis-server.exe` and `redis-cli.exe`.
- A canonical TicketsCenter schema in `TicketCenter_LIEM02_Test`, including the runtime roles and business objects.
- The tracked Hold and Order runtime migrations in `database/migrations/029_hold_runtime.sql` and `030_order_runtime.sql`. Install them after the canonical schema and roles. LIEM has explicit approval to integrate these shared dependencies for this task.
- Windows integrated authentication with permission to install the isolated migrations and create temporary test principals.

The Hold dependency provides authorization, selection locks, durable request receipts, release, and expiry. The Order dependency provides these BUYER procedures.

- `dbo.usp_CreateOrderRequest`.
- `dbo.usp_ApplyOrderCouponRequest`.
- `dbo.usp_ReadCheckout`.
- `dbo.usp_ReadOrderCouponEligibility`.

The migrations remove BUYER access to the legacy Hold, Order and Coupon write procedures. SQL owns the transaction and saves the immutable response before committing.

## Run the verification

From the repository root, run the script with explicit local dependency paths.

```powershell
pwsh -NoProfile -File src/test/resources/order/verify-local.ps1 `
  -TcpPort 63657 `
  -RedisDirectory 'C:\tools\redis' `
  -HoldMigrationPath 'database/migrations/029_hold_runtime.sql' `
  -MigrationPath 'database/migrations/030_order_runtime.sql'
```

Use the actual SQLExpress TCP port and dependency locations. The script rejects SQL scripts with database switches, includes, or shell commands. It records normalized dependency checksums, the Git revision, test output, and WAR packaging output under the excluded `.codex/liem-02/completion/verification-*` directory.

Expected result is 103 tests, no failures, no errors, and no skipped tests, followed by successful WAR packaging. The tests include 83 pure Java cases, 16 SQL/Redis cases, three HTTP cases, and one runtime lifecycle case. The runner checks fresh reports for every selected class.

Run browser logic tests separately.

```powershell
node --test src/test/js/order-checkout.test.js src/test/js/ticket-hold.test.js
```

The default Maven suite skips external integration tests. A passing default build does not replace the guarded Order verification. Finance and Swagger HTTP smoke scripts require their own fixtures; do not include them in the ordinary Node unit runner.

## Verify the packaged checkout

Use an isolated application instance with the canonical schema, runtime migrations, restricted database principals, and dedicated Redis. The shared servlet mapping fix and JSTL dependencies are included in this branch so the packaged WAR can start and render its JSPs.

1. Sign in through the existing login form with a verified test buyer.
2. Select a seat and standing tickets, then create a ten-minute Hold.
3. Open checkout and explicitly create the Order. Confirm one Order exists and the Hold remains ACTIVE with its original expiry.
4. Preview a Coupon. Confirm the preview changes neither the Order totals nor the reservation count.
5. Apply, replace, and remove Coupons. Confirm SQL totals and the single reservation agree with the page.
6. Reload and open another checkout tab. Confirm both recover the same Order without creating another one.
7. Change the test Zone price. Confirm the stored Order item prices remain unchanged.
8. Let the Hold expire. Confirm Coupon controls stop accepting changes. After the worker runs, reconcile checkout and confirm the Order is EXPIRED, inventory and Coupon quota are released, and release audit occurs once.

The HTTP integration fixture exercises the real Order servlet, filters, JDBC, and Redis with a test session. The packaged checkout check additionally verifies normal form login, application listeners, JSP rendering, and the Hold worker. Payment initiation belongs to LIEM-03.

## Check the shared Event route

With the same isolated packaged WAR running, set `EVENT_ROUTE_BASE_URL` to its loopback HTTP URL and `EVENT_ROUTE_ORGANIZATION_ID` to the fixture organization UUID. Set `EVENT_ROUTE_EMAIL` and `EVENT_ROUTE_PASSWORD` to the verified fixture buyer credentials in process environment variables, then run:

```powershell
python src/test/resources/order/verify-event-route.py
```

The runner rejects non-loopback targets before login. It checks that small multipart requests reach Event validation, oversized requests return 413, invalid CSRF returns 403, and unrelated Report POST requests remain unsupported. It sends invalid Event data and creates no Event or provider asset. These four packaged WAR checks run separately from the 103 guarded Order cases.
