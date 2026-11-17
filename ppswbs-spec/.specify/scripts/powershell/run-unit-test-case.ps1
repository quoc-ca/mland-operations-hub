#!/usr/bin/env pwsh
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CasePath,
    [Parameter(Mandatory = $true)][string]$ExecutedBy,
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path }

function Read-FrontMatter {
    param([string]$Raw)
    if ($Raw -notmatch '(?s)\A---\s*\r?\n(.*?)\r?\n---\s*\r?\n') { throw 'Missing YAML front matter.' }
    $result = @{}
    foreach ($line in ($Matches[1] -split "`r?`n")) { if ($line -match '^([a-z_]+):\s*(.*?)\s*$') { $result[$Matches[1]] = $Matches[2] } }
    return $result
}

$resolvedCase = (Resolve-Path -LiteralPath $CasePath).Path
$raw = [System.IO.File]::ReadAllText($resolvedCase, [System.Text.Encoding]::UTF8)
$meta = Read-FrontMatter $raw
if ([string]::IsNullOrWhiteSpace($ExecutedBy) -or $ExecutedBy -match '^\s*(TBD|Codex|agent)\s*$') { throw 'ExecutedBy must name the responsible human team member.' }

$featuresRoot = (Resolve-Path (Join-Path $RepoRoot 'ppswbs-spec/features')).Path.TrimEnd([char]'\')
if (-not $resolvedCase.StartsWith($featuresRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'CasePath must be under ppswbs-spec/features/<feature>/test-results/.'
}
$relative = $resolvedCase.Substring($featuresRoot.Length + 1) -replace '\\', '/'
if ($relative -notmatch '^([^/]+)/test-results/[^/]+\.md$') { throw 'CasePath must be under ppswbs-spec/features/<feature>/test-results/.' }
$feature = $Matches[1]
& (Join-Path $PSScriptRoot 'validate-unit-test-cases.ps1') -FeatureDirectory $feature -RepoRoot $RepoRoot
$validationExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
if ($validationExit -ne 0) { exit $validationExit }

$backend = Join-Path $RepoRoot 'ppswbs_backend'
$target = "$($meta.test_class)#$($meta.test_method)"
$runStarted = [datetime]::UtcNow
Push-Location $backend
try { & mvn '-q' "-Dtest=$target" test; $mavenExit = $LASTEXITCODE } finally { Pop-Location }

$report = Join-Path $backend ('target/surefire-reports/TEST-' + $meta.test_class + '.xml')
if (-not (Test-Path -LiteralPath $report)) {
    throw "Maven exited $mavenExit and no Surefire report was produced for $target; execution evidence was not changed."
}
$reportInfo = Get-Item -LiteralPath $report
if ($reportInfo.LastWriteTimeUtc -lt $runStarted.AddSeconds(-2)) {
    throw "Maven did not refresh the Surefire report for $target; execution evidence was not changed."
}
[xml]$xml = Get-Content -Raw -Encoding utf8 $report
$testcase = @($xml.testsuite.testcase | Where-Object { $_.name -eq $meta.test_method }) | Select-Object -First 1
if (-not $testcase) { throw "Surefire report does not contain testcase $target; execution evidence was not changed." }

$status = if ($testcase.error) { 'ERROR' } elseif ($testcase.failure) { 'FAIL' } elseif ($testcase.skipped) { 'SKIPPED' } else { 'PASS' }
$failure = if ($testcase.error) { $testcase.error.message } elseif ($testcase.failure) { $testcase.failure.message } else { '' }
$failure = (($failure -replace '[\r\n]+', ' ') -replace '\s+', ' ').Trim()
$runMatch = [regex]::Match($raw, '(?m)^- Runs:\s*(\d+)\s*$'); $runs = if ($runMatch.Success) { [int]$runMatch.Groups[1].Value } else { 0 }
$stats = @{}; foreach ($name in @('PASS', 'FAIL', 'ERROR', 'SKIPPED')) {
    $m = [regex]::Match($raw, "(?m)^- ${name}:\s*(\d+)\s*$"); $stats[$name] = if ($m.Success) { [int]$m.Groups[1].Value } else { 0 }
}
$stats[$status]++
$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$bangkokZone = [System.TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')
$bangkokDate = [System.TimeZoneInfo]::ConvertTimeFromUtc([datetime]::UtcNow, $bangkokZone).ToString('yyyy-MM-dd')
$execution = @"
<!-- EXECUTION:START -->
- Latest status: $status
- Executed at: $now
- Executed by: $ExecutedBy
- Command: `mvn -q -Dtest=$target test`
- Surefire evidence: `target/surefire-reports/TEST-$($meta.test_class).xml` testcase `$($meta.test_method)`
- Actual result: $(if ($failure) { $failure } else { $status })
- Runs: $($runs + 1)
- PASS: $($stats.PASS)
- FAIL: $($stats.FAIL)
- ERROR: $($stats.ERROR)
- SKIPPED: $($stats.SKIPPED)
<!-- EXECUTION:END -->
"@
if ($raw -notmatch '(?s)<!-- EXECUTION:START -->.*?<!-- EXECUTION:END -->') { throw 'Execution markers are missing; execution evidence was not changed.' }
$updated = [regex]::Replace($raw, '(?s)<!-- EXECUTION:START -->.*?<!-- EXECUTION:END -->', $execution)
$updated = [regex]::Replace($updated, '(?m)^\*\*Date updated\*\*:.*$', "**Date updated**: $bangkokDate")
$updated = [regex]::Replace($updated, '(?m)^\*\*Last updated by\*\*:.*$', "**Last updated by**: $ExecutedBy")
[System.IO.File]::WriteAllText($resolvedCase, $updated, [System.Text.UTF8Encoding]::new($false))
& (Join-Path $PSScriptRoot 'validate-unit-test-cases.ps1') -FeatureDirectory $feature -RepoRoot $RepoRoot
$postValidationExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
if ($postValidationExit -ne 0) { exit $postValidationExit }
Write-Output "Recorded $status for $target."
if ($mavenExit -ne 0 -and $status -eq 'PASS') { throw "Maven exited $mavenExit despite a passing testcase; inspect Maven output." }
if ($status -in @('FAIL', 'ERROR')) { exit 1 }
