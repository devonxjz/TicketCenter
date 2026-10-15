const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
class Element {
  constructor(tag='div') { this.tagName=tag; this.children=[]; this.handlers={}; this.style={}; this.dataset={}; this.hidden=false; this.disabled=false; this.value=''; this._text=''; }
  set textContent(value) { this._text=String(value); this.children=[]; }
  get textContent() { return this._text + this.children.map(x=>x.textContent).join(''); }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children=nodes; this._text=''; }
  addEventListener(name,handler) { this.handlers[name]=handler; }
  setAttribute(name,value) { this[name]=value; }
}
async function run(name, routes, search='') {
  const ids=new Map(), calls=[]; const get=id=>{ if(!ids.has(id)) ids.set(id,new Element()); return ids.get(id); };
  const document={body:{dataset:{context:'/tickets'}},getElementById:get,createElement:tag=>new Element(tag),title:''};
  const context=vm.createContext({document,location:{origin:'https://example.test',search},URL,URLSearchParams,Date,BigInt,Map,
    FormData:class { *[Symbol.iterator]() { yield* []; } },
    fetch:async url=>{ calls.push(url); const path=url.replace('/tickets','').split('?')[0]; const result=routes[path]; if(!result) throw new Error('Unexpected request: '+path); return {ok:!result.error,json:async()=>result}; }
  });
  const jsp=fs.readFileSync('src/main/webapp/WEB-INF/views/public/'+name+'.jsp','utf8');
  const script=[...jsp.matchAll(/<script>([\s\S]*?)<\/script>/g)][0][1];
  new vm.Script(script).runInContext(context);
  await new Promise(resolve=>setImmediate(resolve));
  return {get,calls};
}
(async()=>{
  const id='11111111-1111-1111-1111-111111111111', zone='22222222-2222-2222-2222-222222222222';
  const event={eventId:id,title:'<script>alert(1)</script>',category:'Music',coverImageUrl:'javascript:alert(1)',venueName:'Venue',venueAddress:'Address',description:'<img onerror=alert(1)>',startTime:'2026-11-01T12:00:00Z',endTime:'2026-11-01T15:00:00Z',saleStartTime:'2026-10-01T00:00:00Z',saleEndTime:'2026-10-31T00:00:00Z',minPrice:'300000'};
  const categories={'/api/event-categories':{data:[]}};
  const catalog=await run('index',{...categories,'/api/events':{data:[event],total:13,pageSize:12}});
  assert.equal(catalog.get('events').children.length,1); assert.equal(catalog.get('next').disabled,false);
  const card=catalog.get('events').children[0].children[0]; assert.equal(card.children.length,1); assert.match(card.textContent,/<script>/);
  await catalog.get('next').handlers.click(); await new Promise(resolve=>setImmediate(resolve)); assert.match(catalog.calls.at(-1),/page=2/);
  const empty=await run('index',{...categories,'/api/events':{data:[],total:0,pageSize:12}}); assert.match(empty.get('status').textContent,/Chưa có/);
  const failure=await run('index',{...categories,'/api/events':{error:{message:'Unavailable'}}}); assert.equal(failure.get('status').textContent,'Unavailable');
  const invalid=await run('event-detail',{},'?eventId=bad'); assert.equal(invalid.calls.length,0);
  assert.equal(invalid.get('event-hold-link').hidden,true);
  const detail=await run('event-detail',{
    ['/api/events/'+id]:{data:event},['/api/events/'+id+'/zones']:{data:[{zoneId:zone,name:'VIP',basePrice:'300000',availableSeats:2,totalCapacity:3,zoneType:'SEATED'}]},
    ['/api/zones/'+zone+'/seats']:{data:[{rowName:'A',seatLabel:'A1',status:'AVAILABLE'},{rowName:'A',seatLabel:'A2',status:'HELD'},{rowName:'B',seatLabel:'B1',status:'SOLD'}]}
  },'?eventId='+id);
  assert.equal(detail.get('title').textContent,event.title); assert.equal(detail.get('cover').src,undefined);
  assert.equal(detail.get('description').textContent,event.description);
  assert.equal(detail.get('event-hold-link').href,'/tickets/select-tickets?eventId='+id);
  assert.equal(detail.get('event-hold-link').hidden,false);
  const legacy=await run('event-detail',{['/api/events/'+id]:{data:event},['/api/events/'+id+'/zones']:{data:[]}},'?id='+id);
  assert.equal(legacy.get('event-hold-link').href,'/tickets/select-tickets?eventId='+id);
  assert.equal(legacy.get('event-hold-link').hidden,false);
  const panel=detail.get('zones').children[0].children[0]; await panel.children[2].handlers.click();
  assert.equal(panel.children[4].children.length,2); assert.match(panel.children[4].children[0].children[2].className,/bg-warning/);
  const hidden=await run('event-detail',{['/api/events/'+id]:{error:{message:'Not found'}}},'?eventId='+id); assert.equal(hidden.calls.length,1); assert.equal(hidden.get('status').textContent,'Not found');
  assert.equal(hidden.get('event-hold-link').hidden,true);
  console.log('PASS: catalog rendering, pagination, empty/error, literal text, unsafe image URL, invalid/hidden Event, Zone/Seat rows and status colors.');
})().catch(error=>{console.error(error);process.exitCode=1;});
