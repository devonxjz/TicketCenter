'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawnSync}=require('node:child_process');
const powershell=process.env.TC_TEST_POWERSHELL;
test('full WAR rejects JDBC redirection before SQL marker lookup or startup',{skip:!powershell},async t=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'tc-full-war-guard-'));
 try{
  const database='TC_Vuong_Test_Guard',prefix='jdbc:sqlserver://127.0.0.1:1433;databaseName='+database+';';
  for(const [name,url] of [
   ['duplicate database',prefix+'databaseName=AnotherDatabase;'],
   ['database alias duplicate',prefix+'database=AnotherDatabase;'],
   ['server override',prefix+'serverName=example.invalid;'],
   ['failover override',prefix+'failoverPartner=example.invalid;'],
   ['remote host','jdbc:sqlserver://example.invalid:1433;databaseName='+database+';'],
   ['different database','jdbc:sqlserver://127.0.0.1:1433;databaseName=AnotherDatabase;']
  ])await t.test(name,()=>{
   const config=path.join(root,'fixture.json');fs.writeFileSync(config,JSON.stringify({database,url,user:'unused',password:'unused'}));
   const result=spawnSync(powershell,['-NoProfile','-File','src/test/scripts/full-war-smoke.ps1','-ConfigPath',config,'-TomcatHome',root,'-JstlApiJar',config,'-JstlImplementationJar',config,'-JavaHome',root],{encoding:'utf8',windowsHide:true,timeout:10000});
   assert.ifError(result.error);assert.notEqual(result.status,0);assert.match(result.stderr,/Unsafe fixture JDBC configuration/);
   assert.equal(fs.existsSync(path.join(root,'webapps')),false);
  });
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});
