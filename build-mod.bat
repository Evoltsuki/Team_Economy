@echo off
setlocal
pushd "%~dp0"
call gradlew.bat releaseMod %*
set "buildExit=%ERRORLEVEL%"
popd
if not "%buildExit%"=="0" echo Build failed. No new verified mod was delivered. Check the errors above.
if "%~1"=="" pause
exit /b %buildExit%
