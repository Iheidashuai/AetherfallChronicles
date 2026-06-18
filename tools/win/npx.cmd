@echo off
setlocal

set "NPX_CLI=D:\CSO2server\Server\node_modules\npm\bin\npx-cli.js"
if not exist "%NPX_CLI%" (
  echo npx CLI not found at %NPX_CLI% 1>&2
  exit /b 1
)

for /f "delims=" %%I in ('powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-node.ps1"') do set "NODE_EXE=%%I"

if not exist "%NODE_EXE%" (
  echo node.exe not found. 1>&2
  exit /b 1
)

for %%D in ("%NODE_EXE%") do set "PATH=%%~dpD;%PATH%"
"%NODE_EXE%" "%NPX_CLI%" %*
