@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "MAVEN_HOME=C:\Users\10050\apache-maven-3.9.10-bin\apache-maven-3.9.10"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Java 21 not found at %JAVA_HOME% 1>&2
  exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Maven not found at %MAVEN_HOME% 1>&2
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
