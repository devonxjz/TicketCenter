# Event dossier opening

The admin event review list fetched dossiers successfully, but rendered the dossier beneath the table without moving the viewport or announcing loading. This made the action appear inactive.

The dossier now opens immediately with a loading notice, moves keyboard focus and the viewport to its heading, and hides previous content and decisions until all three authorized reads complete. Reduced-motion preferences are respected. Errors remain inside the visible dossier with a retry action. Closing invalidates outstanding reads and restores focus to the originating list action, or the status filter if the list has been replaced. Existing revision guards and approval prerequisites remain enforced.

Validation:
- Before implementation, three new regression cases failed for opening visibility, closing, and retry; the existing stale-response case passed.
- After implementation, all four new cases passed. Existing approval policy and inventory race tests passed with DOM harness support for focus, scrolling, and media queries.
- Full local JavaScript suite: 202 tests, 189 passed, 13 environment-dependent tests skipped, zero failures. Isolated finance/Swagger HTTP fixture suites excluded from shared Azure execution.
- Jasper compiled the changed event approval JSP with zero errors.
- Read-only checks against the local server confirmed that pending dossier detail, layout, and commission policy APIs returned HTTP 200. No events were published or rejected for verification.

Browser automation was unavailable because the local browser URL was blocked by the tool's security policy. Visibility, focus, loading, retry, close, and stale responses were verified using the real page script in a DOM harness; visual appearance still requires a manual browser check.
