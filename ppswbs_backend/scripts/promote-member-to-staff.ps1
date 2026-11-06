# Script: Promote member account to STAFF and assign to location #1
# Cách dùng: chạy từ PowerShell trên Windows
# Yêu cầu: MySQL đang chạy trên localhost:33377

param(
    [string]$AccountId = "",
    [string]$DbHost = "localhost",
    [int]$DbPort = 33377,
    [string]$DbUser = "ppswbs_local",
    [string]$DbPass = "sa123123",
    [string]$DbName = "ppswbs_dev"
)

$ErrorActionPreference = "Stop"

# Kiểm tra mysql.exe có sẵn không
$mysqlPath = @(
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe",
    "C:\mysql\bin\mysql.exe",
    (Get-Command mysql.exe -ErrorAction SilentlyContinue).Path
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $mysqlPath) {
    Write-Host "MySQL CLI không tìm thấy. Cài từ: https://dev.mysql.com/downloads/mysql/" -ForegroundColor Red
    Write-Host "Hoặc dùng phương pháp khác như MySQL Workbench, DBeaver, hoặc docker exec..." -ForegroundColor Yellow
    exit 1
}

Write-Host "Dùng MySQL: $mysqlPath" -ForegroundColor Cyan

function Invoke-MySqlQuery {
    param(
        [string]$SqlQuery
    )

    $env:MYSQL_PWD = $DbPass
    $result = & $mysqlPath -h $DbHost -P $DbPort -u $DbUser -D $DbName -e "$SqlQuery" 2>&1
    Remove-Item env:\MYSQL_PWD -ErrorAction SilentlyContinue

    if ($LASTEXITCODE -ne 0) {
        Write-Host "MySQL error: $result" -ForegroundColor Red
        exit 1
    }

    return $result
}

function Get-MemberAccounts {
    $sql = "SELECT id, login_email, role, status FROM accounts WHERE role = 'MEMBER' AND status = 'ACTIVE' ORDER BY id LIMIT 10;"

    Write-Host "Searching for MEMBER ACTIVE accounts..." -ForegroundColor Cyan
    $accounts = Invoke-MySqlQuery -SqlQuery $sql

    if (-not $accounts) {
        Write-Host "No MEMBER ACTIVE accounts found!" -ForegroundColor Red
        return $null
    }

    Write-Host "Found accounts:" -ForegroundColor Green
    Write-Host $accounts

    return $accounts
}

function Promote-ToStaff {
    param([string]$TargetAccountId)

    $sql = @"
UPDATE accounts
SET role = 'STAFF',
    version = version + 1
WHERE id = $TargetAccountId
  AND role = 'MEMBER'
  AND status = 'ACTIVE';

INSERT INTO account_location_assignments (account_id, location_id, assigned_at, active)
SELECT $TargetAccountId, 1, NOW(6), 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM account_location_assignments
    WHERE account_id = $TargetAccountId
      AND location_id = 1
      AND active = 1
);

SELECT
    a.id,
    a.login_email,
    a.role,
    a.status,
    GROUP_CONCAT(CASE WHEN ala.active = 1 THEN ala.location_id END ORDER BY ala.location_id SEPARATOR ',') AS active_locations
FROM accounts a
LEFT JOIN account_location_assignments ala ON ala.account_id = a.id
WHERE a.id = $TargetAccountId
GROUP BY a.id, a.login_email, a.role, a.status;
"@

    Write-Host "Promoting account $TargetAccountId to STAFF..." -ForegroundColor Cyan
    $result = Invoke-MySqlQuery -SqlQuery $sql

    Write-Host $result

    if ($result -match "STAFF") {
        Write-Host "SUCCESS! Account promoted to STAFF" -ForegroundColor Green
        return $true
    }
    else {
        Write-Host "ERROR: Account not updated. Verify account ID and that it's MEMBER/ACTIVE." -ForegroundColor Red
        return $false
    }
}

# Main
Write-Host "=== MEMBER to STAFF Promotion Script ===" -ForegroundColor Yellow

# Get list of MEMBER accounts
$accounts = Get-MemberAccounts

if ($null -eq $accounts) {
    exit 1
}

# Prompt for account selection
if ([string]::IsNullOrWhiteSpace($AccountId)) {
    Write-Host ""
    $selection = Read-Host "Enter account ID from list above (or press Enter to skip)"
    if ([string]::IsNullOrWhiteSpace($selection)) {
        Write-Host "Cancelled." -ForegroundColor Yellow
        exit 0
    }
    $AccountId = $selection
}

# Promote account
$success = Promote-ToStaff -TargetAccountId $AccountId

if ($success) {
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "1. Log out from the app completely"
    Write-Host "2. Log back in with the same email address"
    Write-Host "3. Navigate to http://localhost:5173/staff/retail-shipments"
    Write-Host "4. You should see GHTK orders to manage"
}
else {
    exit 1
}
