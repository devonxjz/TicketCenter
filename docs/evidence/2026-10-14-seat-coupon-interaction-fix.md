# Seat selection and coupon interaction repair

The live booking page disabled every free seat until a quantity was entered. The checkout coupon form was nested inside the order summary, which stayed hidden until a separate create-order action. The pay-now path also initiated a provider payment directly, bypassing coupon review.

Available seats can now be selected directly. The seated quantity follows checked seats in direct-selection mode; quantity-first selection remains supported. Held and sold seats stay unavailable, and the shared eight-ticket limit includes quantities reserved in other zones.

The coupon form is available for an active hold before an order exists. An explicit preview or apply creates the order through the existing idempotent client, checks that the actor and hold still match, then performs the coupon operation. Loading checkout alone remains read-only. Pay-now opens checkout, prepares the order and waits for the customer to review the coupon and explicitly initiate payment.

## Verification

- Three regression tests failed before the repair and passed afterward: direct seat selection, coupon application before a separate creation step, and pay-now stopping at checkout.
- JavaScript suite excluding two dedicated isolated HTTP harnesses: 205 tests, 192 passed, 13 environment-dependent skips, zero failures.
- Maven package: BUILD SUCCESS; 1,037 Java tests, zero failures/errors, 151 external-environment skips.
- Live browser against the running local app connected to Azure: selected CAT 1A seats A1 and A2 directly without increasing quantity first; held both seats; entered SUKIEN005 on checkout before an order existed; Apply created the order and applied the coupon. Subtotal 4,000,000 VND, discount 200,000 VND, total 3,800,000 VND. No browser console errors.
- Screenshot retained privately at `.local/booking-coupon-fixed.png`.
- Updated the four affected static/JSP files in the active runtime and rebuilt the WAR. No provider payment was initiated. Test holds are released after verification.

The finance and Swagger HTTP harnesses require their own isolated fixture and were not run against the shared database. An initial unconfigured invocation failed at those fixture prerequisites; it is not included in the passing JavaScript result above.
