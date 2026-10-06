@echo off
rem Runs the Selenium UI tests: database, app (dev profile), then pytest.
rem Output goes to selenium_run\ . Double-click to run.
setlocal
cd /d "%~dp0"
if not exist selenium_run mkdir selenium_run
set OUT=selenium_run
set TESTS=%*
if "%TESTS%"=="" set TESTS=selenium_tests
echo started %date% %time%> %OUT%\status.txt

echo [1/5] Starting the database (docker compose up -d)...
echo step1 docker>> %OUT%\status.txt
docker compose up -d > %OUT%\docker.log 2>&1
if errorlevel 1 (echo FAILED docker>> %OUT%\status.txt & echo Docker failed, see %OUT%\docker.log & goto :end)

echo [2/5] Starting the app with the dev profile...
echo step2 app>> %OUT%\status.txt
curl.exe -s -o nul http://localhost:8080/login && (echo app already running>> %OUT%\status.txt & goto :appup)
start "Clanforge app (close me when finished)" /min cmd /c "mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev > %OUT%\app.log 2>&1"

echo [3/5] Waiting for http://localhost:8080 (up to 6 minutes)...
set /a tries=0
:waitloop
timeout /t 5 /nobreak > nul
set /a tries+=1
curl.exe -s -o nul http://localhost:8080/login && goto :appup
if %tries% geq 72 (echo FAILED app did not start>> %OUT%\status.txt & echo App did not start, see %OUT%\app.log & goto :end)
goto :waitloop
:appup
echo app up>> %OUT%\status.txt

echo [4/5] Setting up Python packages in %OUT%\venv...
echo step4 python>> %OUT%\status.txt
set PY=python
where py > nul 2>&1 && set PY=py -3
if not exist %OUT%\venv\Scripts\python.exe %PY% -m venv %OUT%\venv > %OUT%\pip.log 2>&1
%OUT%\venv\Scripts\python.exe -m pip install -q -r selenium_tests\requirements.txt >> %OUT%\pip.log 2>&1
if errorlevel 1 (echo FAILED pip>> %OUT%\status.txt & echo pip failed, see %OUT%\pip.log & goto :end)

echo [5/5] Running the Selenium tests in a visible Chrome window...
echo step5 pytest>> %OUT%\status.txt
%OUT%\venv\Scripts\python.exe -m pytest %TESTS% --headed --slow 0.4 -p no:cacheprovider -rA --junitxml=%OUT%\results.xml > %OUT%\pytest.log 2>&1
echo pytest exit %errorlevel%>> %OUT%\status.txt
type %OUT%\pytest.log | findstr /r /c:"passed" /c:"failed" /c:"error"

:end
echo DONE>> %OUT%\status.txt
echo.
echo Finished. Results are in %OUT%\pytest.log
echo The app keeps running in its own minimised window; close it when you're done.
pause
