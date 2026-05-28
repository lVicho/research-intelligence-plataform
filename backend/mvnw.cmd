@echo off
setlocal

where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven is required to use this lightweight project wrapper.
  echo Install Maven 3.6.3 or newer, then rerun this command.
  exit /b 1
)

mvn %*
