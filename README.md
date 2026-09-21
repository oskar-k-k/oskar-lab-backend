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

## Central accounts

Migration `V2__create_platform_accounts.sql` adds platform accounts and revocable sessions without changing catalog data. `AccountService` owns registration, password verification, Google linking and onboarding. Public callers cannot invoke `/internal/auth/*`: the platform server supplies `X-Auth-Bridge`, checked against `AUTH_BRIDGE_SECRET` using a constant-time comparison. Use TLS/private networking between services in production.

Create `.env.local` from `.env.example`, using the same bridge secret as the frontend platform, build, then run `./start-local.ps1`. The script loads only the bridge credential and starts the built jar. A missing or short secret disables the internal auth API rather than falling back to a known development secret.

Google claims are accepted only from the trusted platform after Auth.js validates OAuth/OIDC. Never expose a public endpoint that forwards user-supplied Google subjects as trusted identities. Accounts use internal UUIDs; app authorization must check ownership on the server. Passwords and session credentials are never included in account responses. Tests cover registration, hashing, consent, Google onboarding, secure linking, session revocation, the bridge boundary and rate limiting.

Before multi-instance deployment, replace the in-memory attempt limiter with a shared store, add proxy/IP throttling, and schedule expired-session cleanup. Account email verification, password recovery and mail delivery are not configured in this iteration.
