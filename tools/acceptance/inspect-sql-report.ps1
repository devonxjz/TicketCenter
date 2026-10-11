$ErrorActionPreference='Stop'
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected database'}
$b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$b['Data Source']='tcp:'+$settings['db.host']+',1433';$b['Initial Catalog']=$settings['db.name']
$b['User ID']=$settings['db.user'];$b['Password']=$settings['db.password'];$b['Encrypt']=$true;$b['TrustServerCertificate']=$false
$b['Connect Timeout']=60;$b['Pooling']=$false
$cn=[System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
function Query($sql){
 $cmd=$cn.CreateCommand();$cmd.CommandText=$sql;$cmd.CommandTimeout=60
 $r=$cmd.ExecuteReader();$rows=@()
 while($r.Read()){$row=[ordered]@{};for($i=0;$i -lt $r.FieldCount;$i++){$row[$r.GetName($i)]=if($r.IsDBNull($i)){$null}else{$r.GetValue($i)}};$rows += [pscustomobject]$row}
 $r.Dispose();$cmd.Dispose();return ,$rows
}
try{
 $cn.Open()
 $meta=[ordered]@{}
 $meta.identity=Query "SELECT DB_NAME() db,USER_NAME() principal,IS_MEMBER('db_owner') is_owner,IS_ROLEMEMBER('tc_buyer') buyer,IS_ROLEMEMBER('tc_worker') worker"
 $meta.modules=Query 'SELECT o.name,o.type,m.definition FROM sys.objects o JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0 ORDER BY o.name'
 $meta.columns=Query 'SELECT OBJECT_NAME(c.object_id) table_name,c.name,type_name(c.user_type_id) type_name,c.max_length,c.is_nullable FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id ORDER BY t.name,c.column_id'
 $meta.indexes=Query 'SELECT OBJECT_NAME(i.object_id) table_name,i.name,i.is_unique,i.filter_definition,c.name column_name,ic.key_ordinal,ic.is_included_column FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id JOIN sys.tables t ON t.object_id=i.object_id ORDER BY t.name,i.name,ic.key_ordinal'
 $meta.checks=Query 'SELECT OBJECT_NAME(parent_object_id) table_name,name,definition FROM sys.check_constraints'
 $meta.permissions=Query 'SELECT dp.name principal,p.state_desc,p.permission_name,OBJECT_NAME(p.major_id) object_name,COL_NAME(p.major_id,p.minor_id) column_name FROM sys.database_permissions p JOIN sys.database_principals dp ON dp.principal_id=p.grantee_principal_id WHERE dp.name LIKE ''tc_%'' ORDER BY dp.name,object_name,permission_name'
 $meta.members=Query 'SELECT r.name role_name,u.name principal FROM sys.database_role_members m JOIN sys.database_principals r ON r.principal_id=m.role_principal_id JOIN sys.database_principals u ON u.principal_id=m.member_principal_id'
 $meta.migrations=Query 'SELECT version,checksum_sha256 FROM dbo.tc_schema_migrations ORDER BY version'
 $meta|ConvertTo-Json -Depth 10|Set-Content target/sql-report-current.json -Encoding UTF8
 $meta.identity|ConvertTo-Json -Compress
 $meta.migrations|Format-Table -AutoSize
 Write-Output ('Read-only inventory saved: '+$meta.modules.Count+' SQL modules')
}finally{$cn.Dispose()}
