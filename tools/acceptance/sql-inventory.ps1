#Requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath,[Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'fixture.ps1')
$fixture=Open-AcceptanceFixture $ConfigPath
try {
 $output=New-AcceptanceOutput $OutputDirectory
 $catalog=[ordered]@{database=$fixture.config.database;capturedAt=[DateTime]::UtcNow.ToString('o');scope='Catalog existence, not behavior acceptance';
  objects=Invoke-AcceptanceQuery $fixture.connection "SELECT SCHEMA_NAME(schema_id) schema_name,name,type,type_desc FROM sys.objects WHERE is_ms_shipped=0 AND type IN ('U','V','P','FN','IF','TF','TR','C','D','F','PK','UQ') ORDER BY type,name";
  columns=Invoke-AcceptanceQuery $fixture.connection "SELECT OBJECT_NAME(object_id) table_name,name,TYPE_NAME(user_type_id) data_type,max_length,precision,scale,is_nullable FROM sys.columns WHERE object_id IN(SELECT object_id FROM sys.tables WHERE is_ms_shipped=0) ORDER BY object_id,column_id";
  indexes=Invoke-AcceptanceQuery $fixture.connection "SELECT OBJECT_NAME(i.object_id) table_name,i.name,i.is_primary_key,i.is_unique,i.type_desc,i.filter_definition,STRING_AGG(CONVERT(nvarchar(max),c.name+CASE WHEN ic.is_included_column=1 THEN ' INCLUDE' ELSE CASE WHEN ic.is_descending_key=1 THEN ' DESC' ELSE ' ASC' END END),', ') WITHIN GROUP(ORDER BY ic.is_included_column,ic.key_ordinal,ic.index_column_id) columns FROM sys.indexes i JOIN sys.index_columns ic ON i.object_id=ic.object_id AND i.index_id=ic.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE i.name IS NOT NULL AND i.object_id IN(SELECT object_id FROM sys.tables WHERE is_ms_shipped=0) GROUP BY i.object_id,i.name,i.is_primary_key,i.is_unique,i.type_desc,i.filter_definition ORDER BY table_name,i.name";
  permissions=Invoke-AcceptanceQuery $fixture.connection "SELECT r.name principal,p.state_desc,p.permission_name,OBJECT_NAME(p.major_id) object_name,p.minor_id column_id FROM sys.database_permissions p JOIN sys.database_principals r ON r.principal_id=p.grantee_principal_id WHERE r.name LIKE 'tc[_]%' ORDER BY r.name,p.permission_name,p.major_id";
  memberships=Invoke-AcceptanceQuery $fixture.connection "SELECT r.name role_name,m.name member_name FROM sys.database_role_members rm JOIN sys.database_principals r ON r.principal_id=rm.role_principal_id JOIN sys.database_principals m ON m.principal_id=rm.member_principal_id ORDER BY r.name,m.name"}
 $catalog | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output 'catalog.json') -Encoding utf8
 & node (Join-Path $PSScriptRoot 'summarize-inventory.cjs') (Join-Path $output 'catalog.json') | Set-Content -LiteralPath (Join-Path $output 'spec-mapping.md') -Encoding utf8
 if($LASTEXITCODE -ne 0){throw 'SPEC mapping generation failed.'}
 Write-Output ('SQL_INVENTORY_PASS '+$output)
}finally{$fixture.connection.Dispose()}
