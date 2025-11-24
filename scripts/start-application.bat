@echo off
REM Startup script for Windows with Spark JVM arguments
REM This script starts the Spring Boot application with required JVM arguments for Spark

echo Starting Social Web Recommender Application...
echo.

REM Change to project root directory (parent of scripts directory)
cd /d "%~dp0\.."

REM Verify pom.xml exists
if not exist pom.xml (
    echo ERROR: pom.xml not found. Please run this script from the project root or scripts directory.
    pause
    exit /b 1
)

REM Set JVM arguments - use JAVA_TOOL_OPTIONS which is applied to all Java processes
REM This is the most reliable way to ensure arguments are applied
set JAVA_TOOL_OPTIONS=--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED

REM Also set MAVEN_OPTS to ensure Maven itself uses these arguments
set MAVEN_OPTS=%JAVA_TOOL_OPTIONS% -Dfile.encoding=UTF-8

REM Check if Maven is available
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Using Maven to start application...
    echo Current directory: %CD%
    echo.
    echo JVM arguments for Spark are being set via JAVA_TOOL_OPTIONS and MAVEN_OPTS
    echo JAVA_TOOL_OPTIONS: %JAVA_TOOL_OPTIONS%
    echo MAVEN_OPTS: %MAVEN_OPTS%
    echo.
    echo Note: If Spark still fails, try using start-application-direct.bat instead
    echo       which runs Java directly with explicit arguments
    echo.
    REM Set JVM arguments for Spring Boot plugin
    set "JVM_ARGS=--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED -Dfile.encoding=UTF-8"
    
    REM Try multiple methods to ensure arguments are applied
    REM Method 1: Via spring-boot.run.jvmArguments
    REM Method 2: Via JAVA_TOOL_OPTIONS (already set above)
    REM Method 3: Via MAVEN_OPTS (already set above)
    mvn spring-boot:run -Dspring-boot.run.jvmArguments="%JVM_ARGS%"
) else (
    echo Maven not found. Please ensure Maven is installed and in your PATH.
    echo.
    echo Alternatively, you can run the application from your IDE with these VM options:
    echo --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED -Dfile.encoding=UTF-8
    pause
    exit /b 1
)

pause

