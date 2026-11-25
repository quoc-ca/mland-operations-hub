[CmdletBinding()]
param(
    [switch]$SkipPackage
)

# Verifies the fix for the Member-facing bug: checkout's "Thanh toan qua VNPay" button always failed with
# a generic "An unexpected error occurred" when the backend ran with both the "local" and "retail-vnpay"
# Spring profiles active at once. See VnpayRetailPaymentAdapter.java and LocalRetailPaymentAdapter.java for
# the profile-expression fix, and RetailExceptionHandler.java for the advice-ordering fix.
#
# Run this from the ppswbs_backend directory:
#   .\scripts\verify-vnpay-payment-adapter-fix.ps1
#
# After it passes, restart the backend (see the printed instructions) and retry checkout in the browser.

$ErrorActionPreference = 'Stop'

$tests = @(
    'RetailPaymentPortProfileTest',
    'RetailExceptionHandlerPrecedenceTest',
    'VnpayRetailPaymentAdapterTest',
    'RetailCheckoutQuotePreviewServiceTest',
    'RetailShipmentCreationServiceTest',
    'RetailShipmentLifecycleTest',
    'StaffRetailShipmentServiceTest',
    'StaffRetailShipmentControllerTest',
    'RetailOrderShipmentFulfilmentSyncTest',
    'CartControllerTest'
) -join ','

Write-Host "Running focused tests: $tests"
$ErrorActionPreference = 'Continue'
& .\mvnw.cmd "-Dtest=$tests" test
$testExitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($testExitCode -ne 0) {
    throw "Focused tests failed with exit code $testExitCode. Fix before rebuilding/restarting the backend."
}
Write-Host 'Focused tests passed.'

if (-not $SkipPackage) {
    Write-Host 'Packaging the backend (skips tests here; they already ran above)...'
    $ErrorActionPreference = 'Continue'
    & .\mvnw.cmd -DskipTests package
    $packageExitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($packageExitCode -ne 0) {
        throw "mvn package failed with exit code $packageExitCode."
    }
    Write-Host 'Package succeeded: target\ppswbs_backend-0.0.1-SNAPSHOT.jar'
}

Write-Host ''
Write-Host '=== Next steps (manual) ==='
Write-Host '1. Stop any already-running backend process (the fix is not loaded until you restart).'
Write-Host '2. Start it again with the SAME profiles you were using before, for example:'
Write-Host '     mvn spring-boot:run "-Dspring-boot.run.profiles=local,retail-vnpay"'
Write-Host '   or, if you run the packaged jar:'
Write-Host '     java -jar target\ppswbs_backend-0.0.1-SNAPSHOT.jar'
Write-Host '   (profiles still come from SPRING_PROFILES_ACTIVE in .env or your environment.)'
Write-Host '3. Repeat the checkout flow in the browser and click "Thanh toan qua VNPay" again.'
Write-Host '   - With "local" active (even together with "retail-vnpay"), it now succeeds using the local'
Write-Host '     mock redirect (https://local.mock/vnpay/checkout/...) instead of failing with'
Write-Host '     "Checkout hold expired or is unavailable".'
Write-Host '   - For a real VNPay sandbox run, use ONLY "retail-vnpay" (no "local"), per RUN-PROJ-DEV.md.'
