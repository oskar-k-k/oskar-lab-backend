# Oskar Lab Backend

Requires Java 21 and Docker Desktop.

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

The API listens on http://localhost:10081. Port 10080 is intentionally avoided because the Fetch standard blocks it. PostgreSQL is published on localhost:10054; its container still listens on port 5432.
Compose uses the explicit project name `oskar-lab` to avoid conflicts with other repositories named `backend`.
Database data is stored in the persistent `oskar-lab_oskar-lab-postgres-data` Docker volume.

Override `SERVER_PORT`, `DATABASE_URL`, `DATABASE_USERNAME`, or `DATABASE_PASSWORD` when needed.

```powershell
.\gradlew.bat test build
```

If Java reports `Unable to establish loopback connection` on Windows, use a short existing socket directory, for example from this repository:

```powershell
New-Item -ItemType Directory -Force ../.run | Out-Null
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=D:/Projects/WebDev/Oskar-Lab/.run"
.\gradlew.bat --gradle-user-home "$env:USERPROFILE/.gradle" bootRun
```

## App catalog and migration

`POST /apps` lists the catalog and `GET /apps/{id}` reads an entry. The old `/projects` endpoints remain aliases during migration.

Flyway version 1 renames an existing `projects` table to `apps`, preserving IDs and records, or creates `apps` in a new database. It also renames the developer-reference slug `core` to `core-design`. Existing schemas are baselined at version 0 so this migration runs on upgrades. Hibernate validates the migrated schema instead of modifying it implicitly.

The database remains shared. Product separation in the frontend does not require separate databases. Authentication remains managed centrally by the platform frontend.

## Travel timelines

The travel module adds typed bookings with connected departure/arrival or check-in/check-out
points, private attachments and reusable platform owner/viewer permissions. See
[docs/travel-api.md](docs/travel-api.md) for endpoints, payloads, verified-email invitations,
storage/deletion behavior, migration and tests. It uses the existing backend and database;
the legacy Thailand page is not automatically migrated or deployed by this change.

## Central accounts

Migration `V2__create_platform_accounts.sql` adds platform accounts and revocable sessions without changing catalog data. `AccountService` owns registration, password verification, Google linking and onboarding. Public callers cannot invoke `/internal/auth/*`: the platform server supplies `X-Auth-Bridge`, checked against `AUTH_BRIDGE_SECRET` using a constant-time comparison. Use TLS/private networking between services in production.

Create `.env.local` from `.env.example`, using the same bridge secret as the frontend platform, build, then run `./start-local.ps1`. The script loads only the bridge credential and starts the built jar. A missing or short secret disables the internal auth API rather than falling back to a known development secret.

Google claims are accepted only from the trusted platform after Auth.js validates OAuth/OIDC. Never expose a public endpoint that forwards user-supplied Google subjects as trusted identities. Accounts use internal UUIDs; app authorization must check ownership on the server. Passwords and session credentials are never included in account responses. Tests cover registration, hashing, consent, Google onboarding, secure linking, session revocation, the bridge boundary and rate limiting.

Before multi-instance deployment, replace the in-memory attempt limiter with a shared store, add proxy/IP throttling, and schedule expired-session cleanup. Account email verification, password recovery and mail delivery are not configured in this iteration.

Migration V3 preserves username display case and backfills a separate unique lowercase lookup key. Login and duplicate detection remain case-insensitive for both local and Google accounts.

## Production deployment

The Dockerfile builds and tests a Java 21 image. `.github/workflows/deploy.yml`
deploys pushes to `main` when `DEPLOY_ENABLED=true`; it also supports manual runs.
GitHub holds only a dedicated restricted deployment SSH key, not the root key.
The frontend repository's `deploy/README.md` documents the shared deployment
scripts installed at `/opt/oskar-lab` on the server. Production uses its own
Postgres instance and volume, separate from Lilsi. A database dump precedes each
backend update; Flyway then applies pending migrations on startup. Production
disables automatic baselining. No local user records are copied automatically.
# OpenAPI

Set `OPENAPI_ENABLED=true` for local API exploration. The running backend serves
`/v3/api-docs` and `/swagger-ui/index.html`. Documentation is disabled by default
on deployed instances. Internal operations still require the server bridge key;
the specification is not an authorization mechanism. Never put real credentials
in example payloads or generated files. Swagger's external validator and stored
authorization are disabled; its assets are served locally.

Run `./gradlew test --tests '*OpenApiTests' --rerun-tasks` to export the real
controller contract to `build/openapi/backend.json` using an isolated H2 database.
Then run `npm run api:sync` in the sibling frontend and commit its refreshed schema
and client types. See `frontend/packages/api/README.md` for the full workflow.
Keep response nullability, required fields and enums accurate when changing DTOs.
The dynamic auth action map remains runtime-validated by AccountService; its
OpenAPI action parameter is an enum and its JSON map intentionally allows unknown
values. Contract generation itself does not alter authentication or credential storage.


## Travel packing lists

`GET/PUT /internal/travel/trips/{tripId}/packing` reads or atomically replaces an ordered
packing tree with a last-read aggregate `version`. Existing travel bridge authentication and
trip ownership apply: verified viewers can read, only the owner can edit or check items.
V15 adds normalized trip list, person, group and item tables. Person names are display labels,
not account identities or new sharing grants. Stable UUIDs and positions preserve order and
checks; aggregate optimistic locking rejects concurrent edits with 409. Cascading foreign keys
remove packing data with the trip or account. Counts are derived, never stored separately.
Limits: 12 people, 40 groups/person, 100 groups total, 100 items/group, 500 items total;
names are bounded to 80/100/200 characters. Duplicate IDs and cross-trip row reuse are rejected.

Private names, group labels, item text and checks are stored solely to provide the checklist in
the existing backend database and its backups. Existing hosting/authentication logs and authorized
trip viewers remain recipients; no new third party, analytics, geolocation or browser storage is
introduced. They remain until removed by the owner or trip/account deletion. Backup deletion
schedules remain subject to the existing operator review; no new retention promise is made.
