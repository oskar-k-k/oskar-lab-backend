$ErrorActionPreference = 'Stop'
$configuration = Join-Path $PSScriptRoot '.env.local'
if (Test-Path -LiteralPath $configuration) {
    foreach ($line in Get-Content -LiteralPath $configuration) {
        if ($line -match '^(AUTH_BRIDGE_SECRET)=(.+)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
if (-not $env:AUTH_BRIDGE_SECRET -or $env:AUTH_BRIDGE_SECRET.Length -lt 32) {
    throw 'Set AUTH_BRIDGE_SECRET in backend/.env.local to the platform bridge secret (at least 32 characters).'
}
$application = Join-Path $PSScriptRoot 'build/libs/backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $application)) { throw 'Build the backend before starting it.' }
& java -jar $application
