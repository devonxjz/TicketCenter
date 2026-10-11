"""Real HTTP acceptance against the full WAR and a disposable local fixture."""
import http.cookiejar
import json
import os
import pathlib
import urllib.request
import urllib.error
import uuid

ROOT=pathlib.Path(__file__).resolve().parents[5]
base=os.environ.get('TC_SCAN_WEB_BASE','http://127.0.0.1:18081/ticketscenter')
assert base.startswith('http://127.0.0.1:')
props={}
for line in (ROOT/'target/khanh02-web-fixture.properties').read_text().splitlines():
    if '=' in line and not line.startswith('#'):
        key,value=line.split('=',1);props[key]=value
checks=[]

def client(): return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def request(c,path,expected,body=None,headers=None):
    req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode('utf-8'),headers=headers or {})
    try: response=c.open(req,timeout=20)
    except urllib.error.HTTPError as e: response=e
    content=response.read().decode('utf-8')
    assert response.status==expected,f'{path}: HTTP {response.status}, expected {expected}'
    checks.append({'path':path.split('?')[0],'status':response.status})
    return content
def login(identifier):
    c=client();csrf=json.loads(request(c,'/api/auth/csrf',200))['data']['token']
    request(c,'/api/auth/login',200,{'identifier':identifier,'password':os.environ['TC_SCAN_FIXTURE_PASSWORD']},{'Content-Type':'application/json','X-CSRF-Token':csrf})
    csrf=json.loads(request(c,'/api/auth/csrf',200))['data']['token']
    return c,csrf

anon=client();request(anon,'/auth/login',200);request(anon,'/api/check-ins/events',401)
staff,csrf=login(props['staff']);other,other_csrf=login(props['other']);event=props['event']
page=request(staff,'/organizer/checkin-scanner?eventId='+event,200)
for marker in ['id="camera"','id="image"','id="manual"','id="history"','html5-qrcode.min.js','scanner.js']:assert marker in page
request(staff,'/api/events/'+event+'/check-in-window',200)
request(staff,'/api/events/'+event+'/check-ins?pageSize=20',200)
request(staff,'/api/events/'+event+'/check-ins?pageSize=101',400)
request(other,'/api/events/'+event+'/check-ins',403)
request(other,'/organizer/checkin-scanner?eventId='+event,403)
for path in ['/assets/checkin/scanner.js','/assets/checkin/vendor/html5-qrcode.min.js']:request(staff,path,200)
payload={'eventId':event,'qrContent':(ROOT/'target/khanh02-web-qr.txt').read_text()}
key=str(uuid.uuid4());headers={'Content-Type':'application/json','X-CSRF-Token':csrf,'Idempotency-Key':key}
request(staff,'/api/check-ins',403,payload,{'Content-Type':'application/json','Idempotency-Key':key})
request(staff,'/api/check-ins',400,payload,{'Content-Type':'application/json','X-CSRF-Token':csrf})
request(other,'/api/check-ins',403,payload,{'Content-Type':'application/json','X-CSRF-Token':other_csrf,'Idempotency-Key':str(uuid.uuid4())})
first=json.loads(request(staff,'/api/check-ins',200,payload,headers))['data']
assert first['status']=='CHECKED_IN_SUCCESS' and first['ticketId']==props['ticket'] and first['customerName']=='Khách mời Khánh 02'
assert json.loads(request(staff,'/api/check-ins',200,payload,headers))['data']==first
headers['Idempotency-Key']=str(uuid.uuid4())
duplicate=json.loads(request(staff,'/api/check-ins',409,payload,headers))['data'];assert duplicate['status']=='ALREADY_CHECKED_IN' and duplicate['checkedInAt']==first['checkedInAt']
headers['Idempotency-Key']=str(uuid.uuid4());invalid=json.loads(request(staff,'/api/check-ins',400,{'eventId':event,'qrContent':'forged.signature'},headers))['data'];assert invalid['status']=='INVALID_SIGNATURE'
logs=json.loads(request(staff,'/api/events/'+event+'/check-ins',200))['data'];assert sum(r['result']=='SUCCESS' for r in logs)==1
for row in logs:
    assert not {'qrContent','ticketCode','passwordHash','email','paidAmount'} & row.keys()
(ROOT/'target/khanh02-http-result.json').write_text(json.dumps({'checks':checks,'passed':len(checks)},indent=2),encoding='utf-8')
print(f'Full WAR HTTP/JSP acceptance passed: {len(checks)} checks; signed scan, replay, duplicate, fake QR, CSRF, current membership and private history verified.')
