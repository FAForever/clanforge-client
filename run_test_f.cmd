@echo off
rem Project Acceptance Test F (REST API): runs the Test F unit tests, the Test F
rem integration tests against a throwaway PostgreSQL database, and the whole suite.
rem Output goes to testf_run\ . Double-click to run.
setlocal
cd /d "%~dp0"
if not exist testf_run mkdir testf_run
if exist testf-project.zip (echo Unpacking the uploaded main snapshot... & powershell -NoProfile -Command "Expand-Archive -Force testf-project.zip ." & del testf-project.zip)
set OUT=testf_run
echo started %date% %time%> %OUT%\status.txt

echo [1/4] Java version...
java -version > %OUT%\java.txt 2>&1
type %OUT%\java.txt

echo [2/4] Test F unit tests (no database needed)...
call mvnw.cmd -B --no-transfer-progress test -Dtest=ClanRestApiAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false > %OUT%\unit.log 2>&1
>> %OUT%\status.txt echo unit exit code %errorlevel%

echo [3/4] Test F integration tests against a throwaway database "clan_testf"...
docker info > nul 2>&1
if errorlevel 1 (echo SKIPPED integration: Docker is not running>> %OUT%\status.txt & echo Docker is not running, skipping the integration tests. & goto :suite)
rem reuse the faf-db container from another checkout if there is one, compose would clash on its name
docker inspect faf-db > nul 2>&1
if errorlevel 1 (docker compose up -d db > %OUT%\docker.log 2>&1) else (docker start faf-db > %OUT%\docker.log 2>&1)
set /a tries=0
:waitdb
docker exec faf-db pg_isready -U clanadmin > nul 2>&1 && goto :dbup
set /a tries+=1
if %tries% geq 30 (echo FAILED database did not start>> %OUT%\status.txt & goto :suite)
timeout /t 2 /nobreak > nul
goto :waitdb
:dbup
docker exec faf-db dropdb -U clanadmin --if-exists clan_testf >> %OUT%\docker.log 2>&1
docker exec faf-db createdb -U clanadmin clan_testf >> %OUT%\docker.log 2>&1
set SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/clan_testf
set SPRING_DATASOURCE_USERNAME=clanadmin
set SPRING_DATASOURCE_PASSWORD=devpassword
call mvnw.cmd -B --no-transfer-progress test -Dtest=ClanRestApiAcceptanceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false > %OUT%\integration.log 2>&1
>> %OUT%\status.txt echo integration exit code %errorlevel%
set SPRING_DATASOURCE_URL=
set SPRING_DATASOURCE_USERNAME=
set SPRING_DATASOURCE_PASSWORD=
docker exec faf-db dropdb -U clanadmin --if-exists clan_testf >> %OUT%\docker.log 2>&1

:suite
echo [4/4] Whole test suite, as CI runs it (no database)...
call mvnw.cmd -B --no-transfer-progress test > %OUT%\suite.log 2>&1
>> %OUT%\status.txt echo suite exit code %errorlevel%
if exist target\surefire-reports xcopy /y /q target\surefire-reports\*.txt %OUT%\reports\ > nul 2>&1

echo DONE>> %OUT%\status.txt
type %OUT%\status.txt
echo Finished. Results are in %OUT%\
