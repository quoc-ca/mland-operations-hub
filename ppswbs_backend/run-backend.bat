@echo off
set "JAVA_HOME=C:\Users\admin\.jdks\ms-21.0.7"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ==========================================
echo  Khoi chay PPSWBS Backend (Spring Boot)
echo  Su dung JDK: %JAVA_HOME%
echo ==========================================

cd /d "%~dp0"
call mvnw.cmd spring-boot:run
pause

