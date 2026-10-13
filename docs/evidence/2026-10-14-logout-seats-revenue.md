# Logout, seat selection and revenue verification

The initial handoff was pushed to dev at 47e05f7, retaining the teammate updates and current JSP formatting. The following changes continue that handoff.

## Changes and observed causes

- Logout previously sent a bodyless POST without Content-Type and was rejected with 415. The shared client now sends JSON `{}` with session CSRF; the latest teammate filter compatibility fix is retained.
- The overview showed today's UTC revenue and seven days, making historical paid orders appear absent. Admin and manager now also display cumulative database revenue and net cash flow, with today's amounts identified separately. Missing amounts display an em dash instead of an invented zero.
- Seated booking now loads actual row, number and availability through the public seat API. Available IDs from hold-options remain the booking authority. Held, sold and unavailable seats are disabled; selecting another seat never re-enables them. If layout loading fails, the actual available-seat list remains usable. Quantity limits and SQL hold checks are preserved.
- The dedicated presentation listing had already started, so normal cancellation correctly refused it. It was withdrawn through database publication-archive metadata and an audit entry instead of falsifying lifecycle dates, marking payments/refunds successful or deleting business records. Migrations 064–067 preserve financial history and consistently exclude archived listings from public, managed and check-in selections, including the canonical EventRead procedure's count and rows.
- The presentation guide now points to the existing organization event KỊCH ĐOÀN THÀNH LỘC: LỘ HÀNG (LEAKED), priced 300,000/350,000/400,000 VND in Azure. That event's current inventory is standing; seated functionality was verified on Tinh Hà Say Hi Concert – Đêm 2, without inventing a seat layout for the theater event.

## Fresh checks

- Full Maven package: 1,037 tests, 886 passed, 151 external-service tests skipped, zero failures/errors.
- JavaScript unit suite: 194 tests, 181 passed, 13 environment-dependent tests skipped, zero failures. The separate isolated financial and Swagger HTTP fixture suites were not run against shared Azure.
- JSP source compilation completed with zero errors.
- Live HTTP on the restarted server: all four showcase accounts log in and log out, logout returns success and `/api/me` then returns 401.
- Admin cumulative revenue: 610,211,200 VND; cumulative net cash flow: 580,700,200 VND. Manager cumulative revenue/net cash flow: 5,640,000 VND. These are database-ledger figures, not independently verified bank receipts.
- Archived presentation listing is absent from public catalog, manager listing and staff selection. Historical orders, tickets and payments remain intact.
- Actual seated hold for A1 at 1,200,000 VND was confirmed by a subsequent current-hold read, then cancelled and confirmed released. No payment or external money transfer was made by this check.
- The site remains available on http://127.0.0.1:18082/ticketscenter/ using the requested main configuration file. Credential files are private and ignored by Git.

Browser visual verification remains unavailable because the browser tool rejected the local URL under its access policy. This evidence establishes server, database and tested client behavior; it does not claim screenshots or production readiness.
