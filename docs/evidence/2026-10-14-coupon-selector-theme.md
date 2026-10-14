# Coupon selector and shared component layout — 14/10/2026

Implemented in the existing red / white / black theme:
- Checkout uses a themed select containing currently eligible coupons and a no-coupon choice. Selection automatically applies/removes the coupon using the existing CSRF/idempotency flow; server totals remain authoritative.
- Removed Check / Apply / Remove buttons. Coupon list failure is inline and does not disable payment.
- Added an authenticated, read-only checkout coupon endpoint and migration 068. It enforces Hold ownership, current session, active Hold, sale window, organization, validity and quota; a coupon already reserved by this order remains available under the existing policy.
- Shared controls, panels, spacing, tables and focus states use one stylesheet. Organizer metrics use a balanced 3 + 2 layout. System overview groups scale, cumulative finance and today's revenue; cash details can be expanded.
- Checkout separates tickets from payment totals and removes duplicated ticket/event information.

Verification:
- Maven: BUILD SUCCESS; 1,044 tests, 0 failures/errors, 151 external integration tests skipped.
- JavaScript: 80 passed, 0 failed.
- Full WAR: 17 authentication/authorization/JSP checks passed.
- Migration compiled and installed in the existing dedicated local test database only.
- Real HTTP: eligible coupon discovery before/after order creation, expired/inactive filtering, apply/remove totals (700000 / 560000 VND), anonymous 401, foreign Hold 403, missing Hold 404.
- Browser: checkout, organizer overview and system overview inspected at desktop and 390px mobile; no horizontal document overflow. Screenshots are under `.local/coupon-*.png`.

Deployment: apply `database/migrations/068_checkout_eligible_coupons.sql` before deploying the rebuilt WAR. This session did not change the shared Azure database or replace the main running application. Preview runtime uses port 18084 and isolated test data.

Pre-merge verification: isolated branch build passed; 79 JavaScript tests and 42 checkout tests passed. Added a real SQL regression check for max_discount_amount (20000 VND ceiling) and a browser regression for visible coupon rejection after reconciliation. Reviewer confirmed both fixes and no remaining important findings.
