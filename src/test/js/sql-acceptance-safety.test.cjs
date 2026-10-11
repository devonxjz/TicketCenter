'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),{spawnSync}=require('node:child_process');
const shell=process.env.TC_TEST_POWERSHELL;
test('SQL acceptance tools refuse shared DBs and redirected URLs before any SQL or output', {skip:process.platform!=='win32'||!shell},async t=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'tc-sql-acceptance-'));
 try{
  for(const script of ['sql-inventory','backup-restore','finance-benchmark','seed-browser'])for(const bad of [
   {database:'TicketCenterticke',url:'jdbc:sqlserver://127.0.0.1:1433;databaseName=TicketCenterticke;'},
   {database:'TC_Vuong_Test_Local_Guard',url:'jdbc:sqlserver://remote:1433;databaseName=TC_Vuong_Test_Local_Guard;'},
   {database:'TC_Vuong_Test_Local_Guard',url:'jdbc:sqlserver://127.0.0.1:1433;databaseName=TC_Vuong_Test_Local_Guard;serverName=remote;'}
  ])await t.test(script+' '+bad.database+' '+bad.url.split('//')[1].split(';')[0],()=>{
   const config=path.join(root,'fixture.json'),output=path.join(root,'results');fs.writeFileSync(config,JSON.stringify(bad));
   const args=['-NoProfile','-File',`tools/acceptance/${script}.ps1`,'-ConfigPath',config];
   if(script!=='seed-browser')args.push('-OutputDirectory',output);
   if(script==='backup-restore')args.push('-RestoredDatabase','TC_Vuong_Test_Local_Guard_Restore');
   const result=spawnSync(shell,args,{encoding:'utf8',windowsHide:true,timeout:10000});assert.ifError(result.error);
   assert.notEqual(result.status,0);assert.match(result.stderr,/Dedicated local fixture configuration required/);assert.equal(fs.existsSync(output),false);
  });
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});
