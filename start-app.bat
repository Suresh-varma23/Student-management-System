@echo off
title Student Management System Bootstrapper
echo ======================================================================
echo   STUDENT MANAGEMENT SYSTEM - PORTAL BOOTSTRAPPER
echo ======================================================================
echo.

:: 1. Check Java JDK 21
echo [1/3] Checking for local Java installation...
if exist "C:\Program Files\Java\jdk-21" (
    echo  - Found Java JDK 21 at: C:\Program Files\Java\jdk-21
    set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    set "PATH=C:\Program Files\Java\jdk-21\bin;%PATH%"
) else (
    echo [ERROR] Java JDK 21 was not found at C:\Program Files\Java\jdk-21
    echo Please install Java JDK 21 or update this script with your Java path.
    pause
    exit /b
)
echo  - Verifying Java execution...
java -version
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java was detected but failed to run.
    pause
    exit /b
)
echo.

:: 2. Check or download Apache Maven locally
echo [2/3] Checking for local Apache Maven...
set "MAVEN_DIR=%~dp0.maven"
set "M2_HOME=%MAVEN_DIR%\apache-maven-3.9.6"
set "PATH=%M2_HOME%\bin;%PATH%"

if exist "%M2_HOME%\bin\mvn.cmd" (
    echo  - Found Apache Maven locally in .maven folder.
) else (
    echo  - Local Apache Maven not found. Downloading Maven 3.9.6 automatically...
    if not exist "%MAVEN_DIR%" mkdir "%MAVEN_DIR%"
    
    echo  - Downloading Maven zip archive...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%MAVEN_DIR%\maven.zip'"
    
    echo  - Extracting Maven archive...
    powershell -Command "Expand-Archive -Path '%MAVEN_DIR%\maven.zip' -DestinationPath '%MAVEN_DIR%' -Force"
    
    echo  - Cleaning up download artifacts...
    del "%MAVEN_DIR%\maven.zip"
    echo  - Local Maven successfully prepared!
)
echo  - Verifying Maven execution...
call mvn -version
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven failed to execute.
    pause
    exit /b
)
echo.

:: 3. Run the application
echo [3/3] Compiling project and launching Spring Boot server...
echo.
echo ======================================================================
echo   THE PORTAL IS LAUNCHING!
echo   Once booted, access the application in your browser at:
echo   http://localhost:8080
echo ======================================================================
echo.
call mvn spring-boot:run
pause
