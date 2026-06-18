@echo off
setlocal

if defined CODEX_PYTHON_EXE (
  if exist "%CODEX_PYTHON_EXE%" (
    "%CODEX_PYTHON_EXE%" %*
    exit /b %ERRORLEVEL%
  )
)

set "REPO_PYTHON=%~dp0..\python\cpython-3.10.16-windows-x86_64-none\python.exe"
if exist "%REPO_PYTHON%" (
  "%REPO_PYTHON%" %*
  exit /b %ERRORLEVEL%
)

set "PYTHON_EXE=%LOCALAPPDATA%\Programs\Python\Python310\python.exe"
if exist "%PYTHON_EXE%" (
  "%PYTHON_EXE%" %*
  exit /b %ERRORLEVEL%
)

set "UV_EXE=%USERPROFILE%\.local\bin\uv.exe"
if exist "%UV_EXE%" (
  "%UV_EXE%" run --no-project --python 3.10 python %*
  exit /b %ERRORLEVEL%
)

py -3 %*
exit /b %ERRORLEVEL%
