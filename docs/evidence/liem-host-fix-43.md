# LIEM Host fix 43 verification

Issue [43](https://github.com/devonxjz/TicketCenter/issues/43) implements Cloudinary Event images and the Manager upload form. The implementation branch is `codex/liem-43-event-images`, based on `origin/dev` at `71e02ade2b2de094729ed77b0ecd21d26019f196`.

## Observed behavior

The Manager form creates a draft and uploads either its selected image or generated category artwork. Editing without selecting a file preserves the stored cover. Published Events permit cover replacement through the existing `/api/events/{eventId}/cover` route. Catalog, detail, Manager list, and editor render the stored Cloudinary URL.

Current SQL permissions, session version, CSRF, image validation, immutable request keys, and optimistic Event versions guard writes. Provider calls occur outside SQL transactions. Committed request snapshots recover a lost acknowledgement. Cleanup considers only assets allocated by this workflow and checks references before deletion.

Read the [API and recovery contract](../references/EVENT-IMAGES.md) and [verification instructions](../references/EVENT-IMAGES-VERIFY.md) to repeat these checks.

## Automated checks

| Check | Result | Scope |
|---|---|---|
| Focused Java tests | 34 passed, zero failures, errors, or skips | Service recovery, multipart parsing, image generation, JDBC mapping, navigation, and CSRF helpers |
| JavaScript tests | 9 passed | Multipart fields, file validation, retry keys, published cover mode, and draft Keep mode |
| SQL integration tests | 12 passed | Actual local SQL transactions, version races, permission revocation, SQL failure, uncertain commit, default-image recovery, reference protection, and runtime roles |
| HTTP integration tests | 5 passed | Actual Tomcat servlet, multipart parser, request validation filter, large image upload, errors, CSRF, replay, and read routes |
| WAR packaging | Passed | Application compilation and packaging after focused tests |
| Cloudinary probe | Passed | Actual upload, same-content retry, different-content rejection, lookup, HTTPS decoding, and deletion of the probe's own assets |

SQL and HTTP checks used the dedicated loopback database `TicketCenter_LIEM43_Test`. Provider failures in those tests were injected with a fake storage provider. The separate provider probe and browser session used the configured Cloudinary account.

One added filter test initially called an unmapped route. Tomcat returned 404 before the filter could run. The test now calls a mapped Event route outside the upload allowlist and observes 413. This correction changed the test target, not production behavior.

Final boundary review also found malformed timestamps escaped input validation as `DateTimeParseException`, which the servlet would classify as a service error. The parser now reports them as invalid input. The regression failed with the original parser and passed after the fix. Actual HTTP verification checks a 400 response with no Event mutation.

## Browser checks

An isolated Tomcat 11.0.25 fixture ran production Event servlets and JSPs with actual local SQL and Cloudinary. The browser created a draft without a file, edited it while preserving the cover, and replaced a published Event's cover. The stored replacement decoded at 1200 by 675 pixels and appeared on catalog and detail.

Desktop and 375-pixel mobile checks covered catalog, detail, Manager list, and editor. Manager navigation stacks above the form on mobile. The final mobile form measured 328 pixels wide with no document overflow. The event table scrolls inside its responsive container.

The checks found and corrected two integration errors. SQL returned uppercase UUID text in editor options, while Event DTO UUIDs were lowercase. Options now use canonical UUID values. JSP EL treated `UserPrincipal` as a record and could not resolve the boolean bean-style properties. Header and sidebar conditions now call the existing predicate methods explicitly.

The fixture used a test session. It does not prove production login or deployment. Its temporary SQL principal, Event rows, and allocated provider assets were removed after the session.

## Shared database migration

On 2026-10-10, migration 028 was installed on the verified target `TicketCenterticke`. It added request and cleanup metadata and recorded the normalized UTF-8, LF checksum below.

```text
028_event_image_workflow
266ae006a03cf108c2036e3b2cd2300bb076714483985efd2bf3c4e48ac47e34
```

The inventory contained nine Events. Three had no image and six used unavailable `example.test` fixture URLs. Apply generated category artwork, uploaded it, and verified each provider resource, HTTPS image, and fresh SQL reference. It retained existing source files and did not delete old assets.

```text
First apply: migrated=9, verified=0, failed=0
Second apply: migrated=0, verified=9, failed=0
Final SQL: Events=9, synchronized HTTPS aliases and public IDs=9, version=1 rows=9
Requests: MIGRATE COMMITTED=9
Assets: AVAILABLE=9
```

Each Event's image fields and version changed. The migration update does not modify its title, schedule, organization, or status. The second apply allocated no additional requests or uploads.

Detailed inventory reports, provider logs, and screenshots remain in the local excluded `.codex/host-fix/` directory. They contain no credentials and are not staged.

## Review and remaining delivery checks

The integrator inspected the implementation and removed repeated image CSS. Initial independent review attempts reached usage limits. A subsequent independent source review found a no-file CREATE retry defect. Category artwork was generated before fingerprinting, so a later category change could reject the original request key. The correction fingerprints supplied input, returns committed replay before generation, and recovers an already accepted asset for a pending default-image request. Two actual SQL regressions cover both cases and reject a second upload.

The reviewer rechecked the correction and returned PASS with no remaining feature blocker. No-comments review also returned PASS. It retained one external provider timeout explanation and found no new suppression or constraint comment. The review did not verify hosting or whole-application startup.

Global Hibernate startup remains outside this feature. Existing Event category and inherited timestamp mappings conflict with canonical SQL. The feature uses JDBC and its isolated verifier does not start the application's seeders or unrelated workers. The consolidated function and view scripts also need dependency ordering during clean database setup.

The branch has not been pushed, merged, or deployed. No GitHub issue was closed. Production login, whole-application startup, and hosting require separate verification.

Model the Domain shaped the request and asset state machines. Prove It Works required actual SQL, provider, browser, and migration rerun evidence before reporting these results.
