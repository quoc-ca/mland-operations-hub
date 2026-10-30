[CmdletBinding()]
param(
    [string]$JdbcUrl = $env:WORKSHOP_OTA_TEST_JDBC_URL,
    [string]$JdbcUser = $env:WORKSHOP_OTA_TEST_JDBC_USER,
    [string]$JdbcPassword = $env:WORKSHOP_OTA_TEST_JDBC_PASSWORD
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($JdbcUrl) -or
    [string]::IsNullOrWhiteSpace($JdbcUser) -or
    [string]::IsNullOrWhiteSpace($JdbcPassword)) {
    throw 'Provide JdbcUrl, JdbcUser and JdbcPassword for an isolated non-production MySQL server.'
}

# Scope credentials to this child Maven process only. Do not set TEST_JDBC_* or
# SPRING_FLYWAY_ENABLED globally: other feature suites use different profiles.
$env:WORKSHOP_OTA_TEST_JDBC_URL = $JdbcUrl
$env:WORKSHOP_OTA_TEST_JDBC_USER = $JdbcUser
$env:WORKSHOP_OTA_TEST_JDBC_PASSWORD = $JdbcPassword

$tests = @(
    'WorkshopOtaSnapshotIT',
    'OtaInventorySyncIT',
    'OtaChannelControllerIT',
    'OtaReconciliationApiTest',
    'OtaCurrencyMoneyTest',
    'OtaReconciliationServiceTest',
    'OtaSyncDeliveryServiceTest',
    'OtaSyncDeliveryPersistenceIT',
    'OtaInboundEventServiceTest',
    'OtaConnectivityModuleTest'
) -join ','

# Maven emits JVM/Mockito warnings on stderr; they are not PowerShell failures.
$ErrorActionPreference = 'Continue'
& .\mvnw.cmd -o "-Dtest=$tests" test
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0) {
    throw "Feature 003 verification failed with exit code $LASTEXITCODE."
}

$ErrorActionPreference = 'Continue'
& git diff --check
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0) {
    throw 'git diff --check failed.'
}

Write-Host 'Feature 003 focused verification passed. This does not certify the full repository test suite.'
