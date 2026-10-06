@echo off
rem Adds 12 demo clans to the Browse Clans page (safe to run again).
cd /d "%~dp0"
if not exist selenium_run mkdir selenium_run
echo Adding demo clans...
docker exec -i faf-db psql -U clanadmin -d clan_management -v ON_ERROR_STOP=1 < selenium_tests\seed_clans.sql > selenium_run\seed.log 2>&1
type selenium_run\seed.log
echo SEED DONE>> selenium_run\seed.log
