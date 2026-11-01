"use strict";
const test = require("node:test"), assert = require("node:assert/strict"), fs = require("node:fs"), vm = require("node:vm");

function fixture(networkFailure = false) {
    const target = '/app/events/show?eventId=abc&selection="vip"';
    const calls = [], elements = new Map(); let submit, fallback = false;
    const element = values => ({classList:{add(){},remove(){}},...values});
    const form = element({action:'https://tickets.example/app/auth/login?redirect=' + encodeURIComponent(target),
        addEventListener:(_, fn)=>{submit=fn;},submit:()=>{fallback=true;}});
    for(const [id,value] of Object.entries({loginForm:form,identifier:element({value:'buyer'}),password:element({value:'secret'}),
        'login-csrf':element({value:'csrf-value'}),alertBox:element({}),btnLogin:element({})})) elements.set(id,value);
    const metas = {'context-path':'/app','csrf-token':'csrf-value'};
    const document = {getElementById:id=>elements.get(id),
        querySelector:selector=>({content:metas[selector.match(/name="([^"]+)"/)[1]]})};
    const window = {location:{href:'https://tickets.example/app/auth/login'}};
    const fetch = async (path,options)=>{calls.push({path,options});if(networkFailure)throw new Error('network lost');
        return {ok:true,json:async()=>({data:{authenticated:true,redirect:target}})};};
    const script = fs.readFileSync('src/main/webapp/assets/js/auth/login.js','utf8');
    vm.runInNewContext(script,{document,window,fetch,URL});
    return {target,calls,form,window,submit:()=>submit({preventDefault(){}}),fallback:()=>fallback};
}

test('login sends the encoded return query to the API and navigates using its approved response',async()=>{
    const f=fixture();await f.submit();
    assert.equal(f.calls[0].path,'/app/api/auth/login?redirect=' + encodeURIComponent(f.target));
    assert.equal(f.calls[0].options.headers['X-CSRF-Token'],'csrf-value');
    assert.deepEqual(JSON.parse(f.calls[0].options.body),{identifier:'buyer',password:'secret'});
    assert.equal(f.window.location.href,f.target);
});

test('network fallback posts the native auth form with its original encoded return path',async()=>{
    const f=fixture(true);await f.submit();assert.equal(f.fallback(),true);
    assert.equal(new URL(f.form.action).pathname,'/app/auth/login');
    assert.equal(new URL(f.form.action).searchParams.get('redirect'),f.target);
});
