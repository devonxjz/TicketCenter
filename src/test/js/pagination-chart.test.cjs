const test=require('node:test'),assert=require('node:assert/strict');
const {chartData}=require('../../main/webapp/assets/js/core/pagination-chart.js');
test('chart keeps exact database money above Number.MAX_SAFE_INTEGER',()=>{
 const [day]=chartData([{date:'2026-10-13',cohortGrossRevenue:'9007199254740993',netCashFlow:'4503599627370496'}]);
 assert.equal(day.revenue,9007199254740993n);assert.equal(day.revenueHeight,100);assert.equal(day.cashHeight,49);
});
test('negative cash flow uses the same scale as revenue and preserves its sign',()=>{
 const [day]=chartData([{date:'2026-10-13',cohortGrossRevenue:'100',netCashFlow:'-200'}]);
 assert.equal(day.revenueHeight,50);assert.equal(day.cashHeight,100);assert.equal(day.cash,-200n);
});
test('empty and zero transaction days never fabricate chart values',()=>{
 assert.deepEqual(chartData([]),[]);
 const [day]=chartData([{date:'2026-10-13',cohortGrossRevenue:'0',netCashFlow:'0'}]);
 assert.equal(day.revenueHeight,0);assert.equal(day.cashHeight,0);
});

const {paginationModel,renderPagination,paginateItems}=require('../../main/webapp/assets/js/core/pagination-chart.js');
test('pagination bounds selections and shows exact small page ranges',()=>{
 assert.deepEqual(paginationModel({page:-4,total:18,pageSize:6}),{page:1,totalPages:3,pages:[1,2,3]});
 assert.equal(paginationModel({page:99,total:18,pageSize:6}).page,3);
 assert.deepEqual(paginationModel({page:1,total:0,pageSize:6}).pages,[1]);
});
test('large pagination ranges are bounded with first/last and ellipses',()=>{
 assert.deepEqual(paginationModel({page:50,totalPages:1000000}).pages,[1,null,49,50,51,null,1000000]);
 assert.deepEqual(paginationModel({page:1,totalPages:100}).pages,[1,2,3,4,5,null,100]);
 assert.deepEqual(paginationModel({page:100,totalPages:100}).pages,[1,null,96,97,98,99,100]);
});
function fixture(){
 const doc={activeElement:null,createElement:tag=>new Element(tag)};
 class Element {
  constructor(tag){this.tagName=tag.toUpperCase();this.ownerDocument=doc;this.children=[];this.dataset={};this.attrs={};this.listeners={};this.classList={add(){}};}
  append(child){this.children.push(child);child.parentElement=this;}
  replaceChildren(){this.children=[];}
  setAttribute(name,value){this.attrs[name]=value;}
  getAttribute(name){return this.attrs[name];}
  addEventListener(name,fn){this.listeners[name]=fn;}
  contains(el){return this.children.includes(el);}
  focus(){doc.activeElement=this;}
  click(){this.listeners.click?.();}
  closest(){return null;}
  after(host){this.pager=host;}
 }
 return {doc,host:new Element('nav'),element:tag=>new Element(tag)};
}
test('numbered page selection, boundary disabled state, aria-current, and keyboard focus work',()=>{
 const {host,doc}=fixture(),selected=[];
 renderPagination(host,{page:1,total:18,pageSize:6,onPageChange:page=>selected.push(page)});
 assert.equal(host.children[0].disabled,true);
 assert.equal(host.children[1].getAttribute('aria-current'),'page');
 host.children[0].click();host.children[1].click();host.children[3].click();
 assert.deepEqual(selected,[3]);
 host.children[1].focus();host.onkeydown({key:'End',preventDefault(){}});
 assert.equal(doc.activeElement,host.children.at(-1));
 renderPagination(host,{page:3,total:18,pageSize:6,onPageChange:page=>selected.push(page)});
 assert.equal(host.children.at(-1).disabled,true);
 host.children.at(-1).click();assert.deepEqual(selected,[3]);
});
test('paginating existing data preserves row actions and reveals the selected page',()=>{
 const {host,element}=fixture();for(let i=0;i<15;i++)host.append(element('article'));
 const first=host.children[0],last=host.children[14];
 paginateItems(host,{pageSize:6});assert.equal(first.hidden,false);assert.equal(last.hidden,true);
 host.pager.children.find(e=>e.textContent==='3').click();
 assert.equal(first.hidden,true);assert.equal(last.hidden,false);assert.equal(host.children.length,15);
});

test('busy pagination prevents all selections and rerender replaces stale controls',()=>{
 const {host}=fixture(),selected=[];
 renderPagination(host,{page:2,total:18,pageSize:6,disabled:true,onPageChange:n=>selected.push(n)});
 for(const button of host.children){assert.equal(button.disabled,true);button.click();}
 assert.deepEqual(selected,[]);
 renderPagination(host,{page:2,total:18,pageSize:6,onPageChange:n=>selected.push(n)});
 assert.equal(host.children.length,5);host.children[1].click();assert.deepEqual(selected,[1]);
});
test('server pagination renders navigable numbered links with disabled boundary buttons',()=>{
 const {host}=fixture();renderPagination(host,{page:1,total:18,pageSize:6,href:n=>'/me/orders?status=PAID&size=6&page='+n});
 assert.equal(host.children[0].tagName,'BUTTON');assert.equal(host.children[0].disabled,true);
 assert.equal(host.children[2].tagName,'A');assert.equal(host.children[2].href,'/me/orders?status=PAID&size=6&page=2');
 assert.equal(host.children[1].getAttribute('aria-current'),'page');
});
