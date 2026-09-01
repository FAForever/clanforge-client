$here = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$here\pg\pgsql\bin\pg_ctl.exe" -D "$here\data" -o "-p 5433" -l "$here\server.log" start
