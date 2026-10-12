$ErrorActionPreference='Stop'
Add-Type -TypeDefinition ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'dong-sql-bridge.cs')))
# Test-only loopback transport. Does not enable TCP, restart SQL Server or change its configuration.
[DongSqlBridge]::Run().GetAwaiter().GetResult()
