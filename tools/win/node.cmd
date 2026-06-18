@echo off
setlocal

for /f "delims=" %%I in ('powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-node.ps1"') do set "NODE_EXE=%%I"

if not exist "%NODE_EXE%" (
  echo node.exe not found. 1>&2
  exit /b 1
)

"%NODE_EXE%" %*
