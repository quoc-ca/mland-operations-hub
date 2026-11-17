# Script khởi chạy Backend PPSWBS với Microsoft OpenJDK 21
$env:JAVA_HOME = "C:\Users\admin\.jdks\ms-21.0.7"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Khởi chạy PPSWBS Backend (Spring Boot)" -ForegroundColor Cyan
Write-Host " Sử dụng JDK: $env:JAVA_HOME" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan

Set-Location -Path $PSScriptRoot
.\mvnw.cmd spring-boot:run

