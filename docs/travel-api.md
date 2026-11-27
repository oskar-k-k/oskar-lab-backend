# Travel timelines and platform resource access

The travel module is part of the existing backend and PostgreSQL database. Migration
`V8__add_shared_resources_and_travel.sql` adds its tables; migrations V1–V7 are unchanged.
The TravelTime frontend at `/apps/traveltime` provides the authenticated browser adapter.
Personal trip imports are operator actions and never part of schema migrations or fixtures.

## Model

- A trip owns entries. An entry is one booking or activity, not an entire day's layout.
- FLIGHT, BOAT, TRAIN, BUS and TRANSFER have DEPARTURE and ARRIVAL points, in that order.
- HOTEL and APARTMENT have CHECK_IN and CHECK_OUT points.
- ACTIVITY, RESTAURANT and TOUR have START and an optional END point.
- Every point contains a local date, place name, address, IANA time zone and explicit time semantics.
  Empty/unknown addresses are represented by an empty string. Unknown times are null with
  UNSPECIFIED semantics; never invent midnight. Supported dates range from 1900 through 2200.
- EXACT is a scheduled time. NOT_BEFORE is allowed only for CHECK_IN, NOT_AFTER only for
  CHECK_OUT. A window/deadline is not a promised arrival/departure time.
- Known times are validated against the zone's rules and returned with their UTC offset.
  Supply an explicit matching `utcOffset` during a daylight-saving overlap; nonexistent
  local times are rejected. Exact endpoint pairs return elapsed `durationMinutes` using
  instants, including overnight flights and date-line crossings. Windows do not yield a duration.
- Attachments belong to an entry and may optionally reference one of its point roles.
  Changing an entry's type/points cannot remove a role that still has attachments (409).

Clients can flatten entry points for a chronological view while retaining entry IDs to
connect flights and stays. Hotel check-in/check-out need not be rendered as a giant card;
other activities remain independent entries between them. Entry pages are ordered by the
first point's local date/time, then ID; unknown first-point times sort near the end of the day.
This is booking order, not a globally UTC-sorted list of all individual points. Fetch all
relevant pages before rendering a complete trip timeline.

## Authentication and reusable sharing

All endpoints are **server-to-server** under `/internal/travel/trips`. They require the
existing `X-Auth-Bridge` secret and `X-User-Id` from a verified, complete platform session.
A browser must use its app's server adapter; never expose the bridge key or forward a
browser-supplied identity header. Existing platform auth and user IDs remain authoritative.

`platform_resources` records a resource type (`travel.trip`) and immutable owner account ID.
`platform_resource_viewers` stores normalized email grants. `ResourceAccessService` is
product-neutral: another module can use another resource type and the same ownership/read
rules without importing travel code. No unrestricted generic resource-management HTTP API
is exposed. Product-specific controllers bind a fixed resource type.

The creator becomes owner; callers cannot select an owner email or impersonate an account
via request JSON. Only the owner can edit/delete data, see the sharing list, add addresses
or revoke them. Grants are read-only and can be created before the recipient registers.
They do not send email, reveal whether an account exists, or grant access to other trips.
Removal applies to subsequent reads/downloads, including already-known attachment URLs;
it cannot recall copies a viewer has already downloaded.

Password registration alone does not prove email ownership. `platform_accounts.verified_email`
is set only by the central verified Google login when the provider's verified email matches
the account's canonical email. A mismatching Google login clears this proof. Existing
accounts have no backfilled proof: sign in through Google again after upgrading. Local
accounts must securely link Google and then sign in through Google to establish proof.
After verification, other login methods for that same account may also access its shares.
A future email verification mechanism belongs in central auth, not in the travel module.
Incomplete accounts and forged/unverified identities are denied. Unknown and inaccessible
trip/entry/document IDs both return 404; a viewer attempting owner actions receives 403.

## Endpoints

The following paths are relative to `/internal/travel/trips`:

| Method | Path | Result / permission |
| --- | --- | --- |
| GET | `?page=0` | Owned and verified-email-shared trips, 20/page |
| POST | root | Create a private trip (201) |
| GET | `/{tripId}` | Trip, owner ID, version and `canEdit` |
| PUT | `/{tripId}` | Owner updates trip metadata |
| DELETE | `/{tripId}?version=N` | Owner deletes entire trip (204) |
| GET | `/{tripId}/entries?page=0` | Grouped entries, 20/page |
| POST | `/{tripId}/entries` | Owner creates booking/activity (201) |
| GET | `/{tripId}/entries/{entryId}` | One authorized entry |
| PUT | `/{tripId}/entries/{entryId}` | Owner updates entry and points atomically |
| DELETE | `/{tripId}/entries/{entryId}?version=N` | Owner deletes entry and attachments (204) |
| GET | `/{tripId}/viewers` | Owner-only sharing list |
| POST | `/{tripId}/viewers` | Owner adds `{ "email": "viewer@example.invalid" }` (201) |
| DELETE | `/{tripId}/viewers/{viewerId}` | Owner revokes grant (204) |
| GET | `/{tripId}/entries/{entryId}/attachments` | Metadata, no document bytes |
| POST | `/{tripId}/entries/{entryId}/attachments?pointRole=DEPARTURE` | Owner uploads multipart `file` (201); role optional |
| GET | `/{tripId}/entries/{entryId}/attachments/{attachmentId}` | Authorized binary download |
| DELETE | `/{tripId}/entries/{entryId}/attachments/{attachmentId}` | Owner deletes attachment (204) |

Create requests use `version: 0`. PUT sends the last-read version of the edited object;
stale writes return 409. Trip versions protect trip metadata; each entry has its own version,
including point-only edits. A trip delete deletes its current contents. `hasMore` means a
full page was returned; a final empty page is possible. Page bounds are 0–10000.
POST operations are not automatically retried or idempotent: after a lost response, refresh
the list before asking the user to retry. No infinite client retry or optimistic grant is implied.

Example trip body:

```json
{"title":"Example journey","startDate":"2030-12-01","endDate":"2030-12-10","notes":"","version":0}
```

Example flight body (entirely synthetic):

```json
{
  "type": "FLIGHT",
  "title": "Example outbound flight",
  "notes": "",
  "reference": "",
  "version": 0,
  "points": [
    {"role":"DEPARTURE","date":"2030-12-01","time":"20:00:00","timeZone":"Europe/Berlin","utcOffset":null,"timeMeaning":"EXACT","placeName":"Example departure airport","address":""},
    {"role":"ARRIVAL","date":"2030-12-02","time":"12:00:00","timeZone":"Asia/Bangkok","utcOffset":null,"timeMeaning":"EXACT","placeName":"Example arrival airport","address":""}
  ]
}
```

Errors are safe JSON codes, e.g. `invalidTravel`, `invalidEmail`, `conflict`, `notFound`,
`attachmentPointInUse`, `entryLimit`, `viewerLimit`, `attachmentLimit`, `fileTooLarge`
or `unsupportedAttachment`. Responses use private/no-store; binary downloads also use
nosniff, attachment disposition with encoded filenames and a sandbox CSP. The multipart
resolver returns a safe 413 before controller dispatch for oversized uploads, without
logging request payloads. Missing identity headers and malformed UUIDs/JSON return 400.

## Storage, privacy and operational limits

Travel dates, locations, booking references, notes, sharing emails and file contents are
personal data. They are processed only for the requested private timeline and sharing.
Recipients are the owner, accounts with an active verified-email grant, and the existing
backend/database/backup operators. No new external service, mailbox integration, analytics,
browser permission, dependency or third-party asset is introduced. Existing infrastructure
access/security logs still apply; do not enable request-body, bridge-header or SQL-value logging.

File bytes are stored exclusively through the central `storage.FileStorageService` in
OrbitStorage, using the `traveltime` app project and its scoped key. Migration V11 preserves the
`thailand` namespace on pre-existing attachments and deletion jobs; downloads and deletion
use the attachment's stored project, never a caller-controlled namespace. PostgreSQL holds file IDs,
lengths, MIME metadata and SHA-256 hashes. Downloads are verified before returning bytes.
Uploads are never automatically replayed; a lost upstream response requires operator
reconciliation rather than silently creating duplicate objects. The previous bytea table is
migration-only: `/internal/storage/migrations/travel` is disabled by default and copies a
bounded batch, verifies each cloud round trip, then removes legacy bytes. Normal reads/writes
never fall back to database/file-system contents.

PDF, PNG and JPEG are accepted after size, filename and signature checks: at most 5 MiB/file,
20 attachments/entry, 100 attachments/trip, 100 grants/trip and 1000 entries/trip. HTTP multipart
requests are capped at 6 MiB. OrbitStorage adds app-wide quota, daily byte, rate and concurrency
limits. There is no inline active-document preview, SVG upload or malware-scanning claim.
Owners remain responsible for permission to upload/share their documents and images.

Records remain until owner deletion or an operator-handled deletion request. Deleting an
entry/trip removes its metadata and commits a durable cloud-deletion intent in the same
transaction. A background worker retries OrbitStorage deletions; no cloud deletion is
performed for a rolled-back domain deletion. Physical deletion can lag while storage is down. PostgreSQL
foreign keys also cascade owner-account deletion through resources and trip contents.
Revoking a share deletes that grant, not the owner's data. Backups can retain older copies;
backup retention and physical erasure must follow the actual operator policy, which has not
been confirmed. The central privacy notice includes travel data and verified email proof.

Before opening this API to a public multi-user app, establish account-wide storage quotas,
upload/request rate limits, backup retention and a tested restore process. Per-trip limits
alone do not bound one account's aggregate storage. Existing provider/hosting disclosures
and the platform's unresolved release/privacy gaps still apply. No production migration,
email delivery, restore of a real backup or import of the missing Thailand files was performed.

## Verification and frontend contract

Run `./gradlew test build`. HTTP tests cover all entry types, ownership isolation, pending
invitations, email proof, revocation, read-only viewers, stale writes, uploads/downloads,
malformed inputs, pagination and deletion. Domain tests cover local times, DST gaps/overlaps,
date-line crossings and unknown hours. Fixtures contain no real travel or account data.

`TravelPostgresTests` is opt-in and requires an isolated local PostgreSQL database named
`traveltest`, user `traveltest`, synthetic password `synthetic-travel-database-password`,
and `TRAVEL_TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:<port>/traveltest`. It enables
Flyway and Hibernate validation, checks storage references and deletion/cascades, and never targets production.

`OpenApiTests` exports the real schemas to `build/openapi/backend.json`. Sync this into the
frontend `@oskar-lab/api` contract and regenerate types. Travel endpoints remain backend-only;
the TravelTime Next.js adapter exposes allowlisted same-origin `/api/travel` browser paths
with central sessions, bounded request bodies, origin checks and private/no-store responses. The existing
Thailand page reads its migrated JSON/documents through the protected backend storage
adapter. Converting those records into editable owned trips is a separate integration step.
Missing source files from another computer cannot be reconstructed by storage migration.


### Optional per-point Maps share embeds

`TravelPoint.mapEmbedUrl` is nullable and stores only a user-supplied key-free Google Maps
share embed (`https://www.google.com/maps/embed?pb=...`, optional `hl`). Arbitrary hosts,
credentials, API-key paths/parameters, fragments and duplicate/unknown parameters are rejected.
The server never requests the URL or geocodes the address. Existing owner/viewer permissions
apply to reading/writing this field. It is deleted with its point; existing backup retention
applies. Browsers must request separate consent before loading the external iframe. No Google
Cloud credentials or billing account is needed for these saved share embeds. Accuracy depends
on selecting the correct place in Google Maps before copying its embed link.

Bookings accept optional `bookingSource` (plain text, max 160 characters), e.g. `Booking.com`. Returned entries include an empty string when no source is known. Omission on update preserves the current value for older clients; an empty string clears it. It is private booking metadata governed by the existing trip authorization and deletion rules. No link or third-party request is created from this field.

Stay points optionally accept `timelineTime` plus `timelineUtcOffset` for a separate planned time on the point date. These fields are allowed only on CHECK_IN/CHECK_OUT points, validated against the saved IANA zone, and persisted independently of accommodation policy times. Gaps are rejected and ambiguous local times require an explicit offset. Unknown plans remain null. The fields share trip authorization, retention and deletion rules.
