# Event images

Issue [43](https://github.com/devonxjz/TicketCenter/issues/43) reuses `StorageService`, `CloudinaryStorageService`, and the Event cover route from [DONG-04](https://github.com/devonxjz/TicketCenter/issues/11).

## API contract

All writes require the current session, `X-CSRF-Token`, and a UUID `Idempotency-Key`. The database checks the actor's current `ACTIVE` status, `auth_version`, active `MANAGER` membership, and approved organization before claiming, finalizing, and replaying a request.

| Method and route | Input | Behavior |
|---|---|---|
| `POST /api/organizations/{organizationId}/events` | Multipart Event fields, optional `file` | Creates a draft. Missing image generates category artwork and uploads it. |
| `POST /api/events/{eventId}/edit` | Multipart Event fields, `expectedVersion`, optional `file` | Edits a draft or rejected Event. Missing file keeps the image. |
| `POST /api/events/{eventId}/cover` | Multipart `expectedVersion` and `file` | Replaces a draft, rejected, or published Event cover. Other fields are rejected. |
| `GET /api/events` | None | Published Events, limited to 100. |
| `GET /api/events/{eventId}` | UUID | Published Event detail. |
| `GET /api/manager/events?organizationId={UUID}` | Current session | Managed Events, limited to 100. |
| `GET /api/manager/events/{eventId}` | Current session | Managed Event detail. |
| `GET /api/manager/event-options` | Current session | Current managed organizations and global categories. |

Event fields are `title`, `description`, `categoryId`, `venueName`, `venueAddress`, `saleStart`, `saleEnd`, `startTime`, and `endTime`. Dates use UTC ISO 8601 and millisecond precision. The schedule satisfies `saleStart < saleEnd <= startTime < endTime`.

Success responses contain `data`. Lists contain an array. Options contain `organizations` and `categories`. Event JSON contains UUIDs, integer `version`, `coverImageUrl`, and `imagePublicId`. Read routes query SQL directly, so this implementation has no Event cache to invalidate.

Images are decoded as PNG or JPEG, at most 5 MiB and 12 million pixels. The parser rejects selected empty files, duplicate parts, unknown fields, multiple files, and invalid UTF-8. The container limits the full multipart request to 5,400,000 bytes. SVG and WEBP are not accepted by the existing decoder.

## Persistence and recovery

Migration `028_event_image_workflow.sql` adds request and cleanup metadata. It reuses `tc_events.event_image`, `event_image_public_id`, and `cover_image_url`. Both URL aliases change in the same transaction. SQL contains no image bytes.

`tc_event_image_requests` has `PENDING`, `COMMITTED`, and `ABORTED` states. The actor and request UUID identify an immutable payload fingerprint and committed result snapshot. Same-key replay checks current permission and returns that snapshot. Different input with the same key returns `409`. SQL version checks reject concurrent stale edits.

An image has the immutable public ID `ticketcenter/events/{eventId}/{requestId}`. Cloudinary uploads use `overwrite=false` and retain a SHA-256 context value. A retry verifies that value before reusing an existing image. Cloudinary documents the [upload options and context fields](https://cloudinary.com/documentation/image_upload_api_reference).

For a create request without a file, the fingerprint records the supplied Event fields and a category-default marker. It excludes generated PNG bytes, so later category or host font changes cannot invalidate a committed replay. The service claims the request before generating artwork. A pending retry looks up its allocated immutable public ID and keeps an already accepted asset. That ID comes from the durable intent, not from a client-supplied URL. If no resource exists, the service generates artwork and uploads it with overwrite disabled. Supplied files retain SHA-256 content verification.

The workflow commits the intent before contacting Cloudinary, then finalizes the Event, result snapshot, and asset metadata together. No SQL transaction stays open during upload. A lost commit acknowledgement leaves the result recoverable by key. Upload or SQL failure preserves the previous Event cover.

`tc_event_image_assets` has `AVAILABLE`, `DELETE_PENDING`, and `DELETED` states. Cleanup claims a lease, excludes pending requests, checks Event URL/public-ID references and User avatar references, then calls the provider outside SQL. Failed deletion retries after five minutes. A pending save older than one day with an expired lease becomes aborted. The UI rotates a key only after a definitive `REQUEST_ABORTED` response. It keeps the key on transient failures.

Cleanup only considers IDs allocated by this workflow. Unknown legacy assets are retained. Asset attachment through this workflow locks the asset record and rejects `DELETE_PENDING` or `DELETED` IDs. Other upload implementations must use this workflow before attaching these IDs.

## Migration command

`vn.ticketscenter.event.image.EventImageMigration` accepts `audit|apply|cleanup expectedDatabase report.tsv`. It verifies `DB_NAME()` before work and rejects an existing report path. Audit and apply inventory require a database owner connection. Apply uses version checks and the same request protocol as the API.

Source downloads allow HTTPS on `images.unsplash.com` and `res.cloudinary.com`, reject private addresses and redirects, and enforce byte, pixel, and time limits. Unavailable or unapproved sources receive original category artwork. The report records that reason. Each migrated image is checked through the provider, HTTPS delivery, and a fresh SQL read. Existing source files are retained. A rerun verifies existing Cloudinary assets before allocating new work.

Global Hibernate mappings remain outside this feature. In particular, the existing Event category enum and inherited `updated_at` do not match canonical SQL. The new Event workflow uses JDBC. Feature tests run without global seeders, refund workers, or cancellation workers. Whole-application authentication and hosting deployment are separate from the verified feature fixture.
