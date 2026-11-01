@echo off
setlocal
cd /d "%~dp0"

git rev-parse --show-toplevel >nul 2>&1
if errorlevel 1 (
    echo Error: this folder is not a Git repository.
    pause
    exit /b 1
)

git remote get-url origin >nul 2>&1
if errorlevel 1 (
    echo Error: Git remote "origin" is not configured.
    pause
    exit /b 1
)

for /f "delims=" %%B in ('git branch --show-current') do set "BRANCH=%%B"
if not defined BRANCH (
    echo Error: HEAD is detached. Check out a branch before running this script.
    pause
    exit /b 1
)

echo.
echo Target branch: %BRANCH%
echo Current changes:
git status --short
echo.
set /p "COMMIT_MSG=Commit message (blank uses 'Update Limen'): "
if not defined COMMIT_MSG set "COMMIT_MSG=Update Limen"

set /p "CONFIRM=Stage all non-ignored changes, commit, and push? [y/N] "
if /i not "%CONFIRM%"=="y" (
    echo Cancelled.
    pause
    exit /b 0
)

git add -A
if errorlevel 1 goto :error

echo.
echo Changes staged for commit:
git status --short
git diff --cached --quiet
if errorlevel 2 goto :error
if errorlevel 1 goto :commit

echo Nothing to commit.
pause
exit /b 0

:commit
git commit -m "%COMMIT_MSG%"
if errorlevel 1 goto :error

git push -u origin "%BRANCH%"
if errorlevel 1 goto :error

echo.
echo Commit pushed to origin/%BRANCH%.
pause
exit /b 0

:error
echo.
echo Operation failed. Review the Git output above; no force push was attempted.
pause
exit /b 1
