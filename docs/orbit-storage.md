# Central OrbitStorage integration

Every Oskar Lab app uses the existing OrbitStorage service for durable file bytes.
Domain records, permissions, file IDs, MIME metadata and SHA-256 remain in PostgreSQL.
`storage.FileStorageService` is transport infrastructure: callers authorize their own
domain/resource before private file access. No public download-by-ID API is exposed.

## Configuration

`ORBIT_BASE_URL` is an internal origin. `ORBIT_PROJECT_KEYS_FILE` points to a private JSON
map from app slug to `{projectId, key}`; no admin credential is installed in the backend.
`ORBIT_ASSET_MANIFEST` is an exact-path migration allowlist partitioned into `publicAssets`
and `privateAssets`. Configuration is mounted read-only as a directory so atomic replacements
remain visible. Backend UID 10001 alone can read its directory/credential file.

Backend and OrbitStorage share the private-purpose external Docker network
`oskar-lab-storage`. OrbitStorage's database is not attached; no public port is added.
The operator script `frontend/deploy/orbit_storage.py` provisions `oskar-lab/<app-slug>`
projects. OrbitStorage uses `<project-uuid>/<file-uuid>` keys at the existing bucket root:
each app has its own prefix. Empty S3 prefixes are virtual, not physical empty folders.
Existing non-Oskar projects and their keys/data are not modified.

Provisioned limits per app: 256 MiB quota, 10 MiB/object, 200 MiB admitted uploads/day,
20 uploads/minute and two concurrent uploads. Travel applies its stricter 5 MiB file policy.
Per-account fair-use limits remain necessary before public multi-user uploads are enabled.

## Lifecycle and failures

The client sends known-length raw bytes and the scoped app key. It checks project identity,
returned size/type, download length and SHA-256. Redirects are disabled. Uploads are never
automatically retried; network/provider failures return safe codes rather than upstream bodies.
If the domain transaction rolls back after upload, an independent transaction queues object
deletion. Domain deletion commits its deletion intent with removal of references. A bounded
background worker deletes cloud objects outside database transactions and retries failures;
an upstream 404 is already deleted. Rollback cannot remove a still-visible document.
Physical deletion may lag while storage is down.

A total database outage while recording rollback cleanup, or a lost upload response before
the object ID is known, still requires operator reconciliation. Monitor cleanup failures and
OrbitStorage accounting; do not claim these cases were deleted. The legacy bytea table and
repository are migration-only. The bridge-protected `/internal/storage/migrations/travel`
endpoint is disabled unless `orbit.migration-enabled=true`; it copies twenty rows at most,
verifies cloud round trips, then removes legacy bytes. Coordinate conversion with a maintenance
window. Normal reads/writes never fall back to database or filesystem bytes.

## Public and private assets

`GET /public-assets/{app}/{path}` serves only approved public paths. The frontend production
build enables `ORBIT_ASSETS_ENABLED=true` and rewrites existing catalog/profile/SVG/favicon
URLs before local files. Originals remain versioned for development/recovery, not as a
production fallback. Private manifest entries cannot be requested through this endpoint.
Public SVGs are trusted source assets and receive a restrictive CSP.

The legacy Thailand adapter checks the frontend Google allowlist and a backend verified-email
allowlist. Existing users must sign in through Google again after the verification upgrade.
Default health reports index readiness; `?probe=liveness` reports process availability only.
Missing trip data still mean the travel timeline is unavailable, even when its process is live.
Converting the legacy JSON into editable owned trips remains a separate UI/data integration.

The initial copy on 2026-11-24 provisioned 12 projects and verified all 17 public assets by
downloading and comparing SHA-256. The private source directory was empty: zero private
travel sources were copied. Re-running the script verifies/reuses unchanged mappings and
never deletes originals or non-Oskar objects. Configured cloud region: `nbg1`; this is not a
guarantee about every backup's physical location. Missing originals on another computer
cannot be recreated by migration.

Browser-only drafts, ephemeral try-on images, compiled JS/CSS, build fonts, code-generated
drawings, source repositories and database backups are not silently copied into app storage.
That would change their existing lifecycle or deployment semantics.

## Privacy, rights and operations

Files now pass through OrbitStorage and the existing Hetzner Object Storage account. Browsers
use same-origin Oskar Lab URLs and receive no cloud credential/direct cloud URL. No browser
cookies/IP headers/bridge keys are sent to OrbitStorage. No analytics, mailbox access, new
runtime dependency or third-party visual asset is introduced. Original asset notices/rights
remain applicable. OrbitStorage is Apache-2.0 and remains a separately deployed service.
The central privacy notice describes cloud storage and deferred physical deletion.

Back up Oskar Lab metadata, OrbitStorage metadata and cloud objects together. Preserve IDs
and mappings on restore. Provider versioning/lifecycle and backups may retain older copies;
final operator retention policy and a restore drill remain unresolved. Per-app quota is not
per-user fairness. Automated tests use synthetic data, never real private bookings.

Tests cover known-length HTTP, namespace isolation, redirects, quota/rate errors, corrupt and
truncated downloads, manifest visibility, rollback cleanup and retry behavior. The PostgreSQL
test applies V1-V10 with schema validation. Live verification additionally checks every migrated
public URL, private denial and container health. No destructive original-file cleanup is part
of the cutover.
