#!/usr/bin/env pwsh
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$FeatureDirectory,
    [string]$RepoRoot,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path }

function Read-CaseFrontMatter {
    param([string]$Path)
    $raw = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    if ($raw -notmatch '(?s)\A---\s*\r?\n(.*?)\r?\n---\s*\r?\n') {
        throw 'Missing YAML front matter.'
    }
    $values = @{}
    foreach ($line in ($Matches[1] -split "`r?`n")) {
        if ($line -match '^([a-z_]+):\s*(.*?)\s*$') { $values[$Matches[1]] = $Matches[2] }
    }
    return @{ Raw = $raw; Values = $values }
}

function Add-Issue {
    param([System.Collections.Generic.List[object]]$Issues, [string]$Path, [string]$Message)
    $Issues.Add([PSCustomObject]@{ path = $Path; message = $Message })
}

$featurePath = Join-Path $RepoRoot "ppswbs-spec/features/$FeatureDirectory"
$backendPath = Join-Path $RepoRoot 'ppswbs_backend'
$casesPath = Join-Path $featurePath 'test-results'
$issues = [System.Collections.Generic.List[object]]::new()
$seenIds = @{}
$seenMethods = @{}

if (-not (Test-Path -LiteralPath $featurePath -PathType Container)) {
    Add-Issue $issues $featurePath 'Feature directory does not exist.'
} elseif (-not (Test-Path -LiteralPath $casesPath -PathType Container)) {
    Add-Issue $issues $casesPath 'test-results directory does not exist.'
}

$cases = if (Test-Path -LiteralPath $casesPath -PathType Container) {
    @(Get-ChildItem -LiteralPath $casesPath -Filter '*.md' -File | Sort-Object Name)
} else { @() }

if ($cases.Count -eq 0 -and $issues.Count -eq 0) {
    Add-Issue $issues $casesPath 'No unit-test case Markdown files found.'
}

$requiredKeys = @('id', 'feature', 'test_level', 'test_case_type', 'source_ref', 'source_class', 'source_method', 'test_class', 'test_method')
$requiredHeadings = @('## Test design', '### Preconditions', '### Test data and actions', '### Expected assertions', '### Exception and log expectation', '## Execution')

foreach ($case in $cases) {
    try { $parsed = Read-CaseFrontMatter $case.FullName } catch {
        Add-Issue $issues $case.Name $_.Exception.Message
        continue
    }
    $raw = $parsed.Raw
    $meta = $parsed.Values
    foreach ($key in $requiredKeys) {
        if (-not $meta.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($meta[$key])) {
            Add-Issue $issues $case.Name "Missing front matter key: ${key}."
        }
    }
    foreach ($heading in $requiredHeadings) {
        if (-not $raw.Contains($heading)) { Add-Issue $issues $case.Name "Missing required heading: ${heading}." }
    }
    foreach ($field in @('Date created', 'Date updated', 'Author', 'Last updated by')) {
        if ($raw -notmatch "(?m)^\*\*$([regex]::Escape($field))\*\*:\s*(?!\[|TBD\b)(.+\S)\s*$") {
            Add-Issue $issues $case.Name "Missing or placeholder metadata field: ${field}."
        }
    }
    if ($raw -notmatch '<!-- EXECUTION:START -->' -or $raw -notmatch '<!-- EXECUTION:END -->') {
        Add-Issue $issues $case.Name 'Execution markers are required.'
    }

    if ($meta.ContainsKey('id')) {
        if ($meta.id -notmatch '^UTC-\d{3}-\d{3,}$') { Add-Issue $issues $case.Name 'id must match UTC-<feature>-<NNN>.' }
        if ($meta.id -match '^UTC-(\d{3})-' -and $Matches[1] -ne $FeatureDirectory.Substring(0, 3)) {
            Add-Issue $issues $case.Name 'id feature number does not match feature directory.'
        }
        if ($case.Name -notmatch "^$([regex]::Escape($meta.id))--[a-z0-9-]+\.md$") { Add-Issue $issues $case.Name 'Filename must be <id>--<lowercase-slug>.md.' }
        if ($seenIds.ContainsKey($meta.id)) { Add-Issue $issues $case.Name "Duplicate id also used by $($seenIds[$meta.id])." } else { $seenIds[$meta.id] = $case.Name }
    }
    if ($meta.feature -and $meta.feature -ne $FeatureDirectory) { Add-Issue $issues $case.Name 'feature must match the feature directory name.' }
    if ($meta.test_level -ne 'unit') { Add-Issue $issues $case.Name 'test_level must be unit.' }
    if ($meta.test_case_type -notin @('N', 'A', 'B')) { Add-Issue $issues $case.Name 'test_case_type must be N, A, or B.' }

    if ($meta.source_ref -notmatch '^(spec|tasks)\.md#[A-Za-z0-9._-]+$') {
        Add-Issue $issues $case.Name 'source_ref must be spec.md#<requirement> or tasks.md#<task>.'
    } else {
        $parts = $meta.source_ref -split '#', 2
        $sourceDoc = Join-Path $featurePath $parts[0]
        if (-not (Test-Path -LiteralPath $sourceDoc)) { Add-Issue $issues $case.Name "Traceability source does not exist: $($parts[0])." }
        elseif ((Get-Content -Raw -Encoding utf8 $sourceDoc) -notmatch [regex]::Escape($parts[1])) { Add-Issue $issues $case.Name "Traceability anchor not found: $($meta.source_ref)." }
    }

    foreach ($pair in @(@('source_class', 'source_method', 'main'), @('test_class', 'test_method', 'test'))) {
        $className = $meta[$pair[0]]; $methodName = $meta[$pair[1]]; $kind = $pair[2]
        if ([string]::IsNullOrWhiteSpace($className) -or [string]::IsNullOrWhiteSpace($methodName)) { continue }
        if ($className -notmatch '^[A-Za-z_$][A-Za-z0-9_$.]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$') { Add-Issue $issues $case.Name "Invalid $kind class FQCN."; continue }
        $base = if ($kind -eq 'main') { 'src/main/java' } else { 'src/test/java' }
        $javaPath = Join-Path $backendPath ($base + '/' + ($className -replace '\.', '/') + '.java')
        if (-not (Test-Path -LiteralPath $javaPath)) { Add-Issue $issues $case.Name "$kind class source does not exist: $className."; continue }
        $java = Get-Content -Raw -Encoding utf8 $javaPath
        if ($java -notmatch "(?m)\b$([regex]::Escape($methodName))\s*\(") { Add-Issue $issues $case.Name "$kind method does not exist: $className#$methodName." }
        if ($kind -eq 'test') {
            if ($className -notmatch 'Test$') { Add-Issue $issues $case.Name 'test_class must end in Test, not IT or Tests.' }
            if ($java -notmatch 'org\.junit\.jupiter') { Add-Issue $issues $case.Name 'test_class must use JUnit Jupiter.' }
            if ($java -match '@SpringBootTest|@AutoConfigureMockMvc|@TestPropertySource|\bMockMvc\b') { Add-Issue $issues $case.Name 'Integration-test marker found in a unit-test class.' }
            $target = "$className#$methodName"
            if ($seenMethods.ContainsKey($target)) { Add-Issue $issues $case.Name "Duplicate test target also used by $($seenMethods[$target])." } else { $seenMethods[$target] = $case.Name }
        }
    }
}

$result = [ordered]@{ feature = $FeatureDirectory; cases = $cases.Count; valid = ($issues.Count -eq 0); issues = @($issues) }
if ($Json) { $result | ConvertTo-Json -Depth 4 } else {
    if ($issues.Count -eq 0) { Write-Output "PASS: $($cases.Count) unit-test case(s) validated for $FeatureDirectory." }
    else { foreach ($issue in $issues) { Write-Output "FAIL: $($issue.path): $($issue.message)" } }
}
if ($issues.Count -gt 0) { exit 1 }
