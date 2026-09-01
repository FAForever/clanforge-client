$here = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$here\pg\pgsql\bin\pg_ctl.exe" -D "$here\data" -m fast stop
