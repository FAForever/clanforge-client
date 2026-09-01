$here = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$here\pg\pgsql\bin\psql.exe" -h 127.0.0.1 -p 5433 -U clanadmin -d clan_management @args
