(function(root){
 'use strict';
 function chartData(days){
  const rows=days.map(day=>({date:day.date,revenue:BigInt(day.cohortGrossRevenue||0),cash:BigInt(day.netCashFlow||0)}));
  const max=rows.reduce((m,r)=>[m,r.revenue,r.cash<0n?-r.cash:r.cash].reduce((a,b)=>a>b?a:b),0n);
  return rows.map(r=>({...r,revenueHeight:max?Number(r.revenue*100n/max):0,cashHeight:max?Number((r.cash<0n?-r.cash:r.cash)*100n/max):0}));
 }
 function renderChart(host,days){
  const doc=host.ownerDocument,rows=chartData(days),money=v=>v.toLocaleString('vi-VN')+' ₫';host.replaceChildren();
  const legend=doc.createElement('p');legend.className='tc-chart-legend';legend.textContent='● Doanh thu vé     ■ Dòng tiền ròng';host.append(legend);
  const grid=doc.createElement('div');grid.className='tc-chart-grid';
  for(const r of rows){const group=doc.createElement('button');group.type='button';group.className='tc-chart-day';
   const detail=r.date+': doanh thu '+money(r.revenue)+', dòng tiền '+money(r.cash);group.setAttribute('aria-label',detail);group.title=detail;
   const bars=doc.createElement('span');bars.className='tc-chart-bars';
   for(const [height,kind,negative]of [[r.revenueHeight,'revenue',false],[r.cashHeight,'cash',r.cash<0n]]){const bar=doc.createElement('span');bar.className='tc-chart-bar '+kind+(negative?' negative':'');bar.style.height=height+'%';bars.append(bar);}
   const label=doc.createElement('span');label.className='tc-chart-date';label.textContent=r.date.slice(5).split('-').reverse().join('/');group.append(bars,label);
   group.addEventListener('click',()=>{info.textContent=detail;});grid.append(group);
  }
  const info=doc.createElement('p');info.className='tc-chart-info';info.setAttribute('aria-live','polite');info.textContent=rows.some(r=>r.revenue||r.cash)?'Chọn một ngày để xem số tiền.':'Chưa có giao dịch trong 7 ngày gần nhất.';
  host.append(grid,info);
 }
 function paginationModel(options){
  const size=Math.max(1,Math.floor(Number(options.pageSize)||20));
  const totalPages=Math.max(1,Math.ceil(Number(options.totalPages)||Number(options.total||0)/size));
  const page=Math.min(totalPages,Math.max(1,Math.floor(Number(options.page)||1)));
  const selected=new Set([1,totalPages]);
  let start=Math.max(1,page-1),end=Math.min(totalPages,page+1);
  if(page<=3)end=Math.min(totalPages,5);
  if(page>=totalPages-2)start=Math.max(1,totalPages-4);
  for(let n=start;n<=end;n++)selected.add(n);
  const pages=[];let last=0;
  for(const n of [...selected].sort((a,b)=>a-b)){if(n-last===2)pages.push(last+1);else if(n-last>2)pages.push(null);pages.push(n);last=n;}
  return {page,totalPages,pages};
 }
 function renderPagination(host,options){
  if(!host)return;
  const {page,totalPages,pages}=paginationModel(options),doc=host.ownerDocument;
  const focused=host.contains(doc.activeElement),focusPage=doc.activeElement?.dataset?.page;
  host.replaceChildren();host.classList.add('tc-pagination');host.setAttribute('role','navigation');host.setAttribute('aria-label',options.label||'Phân trang');
  for(const old of [options.previous,options.next])if(old){old.hidden=true;old.classList.add('tc-pagination-legacy');}
  const controls=[];
  function control(target,text,label,boundary){
   const disabled=Boolean(options.disabled||boundary),element=doc.createElement(options.href&&!disabled?'a':'button');
   element.className='tc-page-button'+(text==='‹'||text==='›'?' tc-page-chevron':'');element.textContent=text;element.dataset.page=String(target);element.setAttribute('aria-label',label);
   if(element.tagName==='A')element.href=options.href(target);else{element.type='button';element.disabled=disabled;}
   if(target===page&&text===String(page))element.setAttribute('aria-current','page');
   if(disabled)element.setAttribute('aria-disabled','true');
   if(!options.href)element.addEventListener('click',()=>{if(!element.disabled&&target!==page)options.onPageChange?.(target);});
   host.append(element);controls.push(element);return element;
  }
  control(Math.max(1,page-1),'‹','Trang trước',page===1);
  for(const n of pages){if(n===null){const gap=doc.createElement('span');gap.className='tc-page-ellipsis';gap.textContent='…';gap.setAttribute('aria-hidden','true');host.append(gap);}else control(n,String(n),'Trang '+n,false);}
  control(Math.min(totalPages,page+1),'›','Trang sau',page===totalPages);
  if(options.countLabel){const count=doc.createElement('span');count.className='tc-pagination-count';count.textContent=options.countLabel;host.append(count);}
  host.onkeydown=event=>{if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;const enabled=controls.filter(e=>!e.disabled);const index=enabled.indexOf(doc.activeElement);if(index<0)return;event.preventDefault();const next=event.key==='Home'?0:event.key==='End'?enabled.length-1:Math.min(enabled.length-1,Math.max(0,index+(event.key==='ArrowRight'?1:-1)));enabled[next].focus();};
  if(focused)(controls.find(e=>e.dataset.page===focusPage&&!e.disabled)||controls.find(e=>e.getAttribute('aria-current')==='page'))?.focus();
 }
 function paginateItems(container,options={}){
  if(!container)return;
  const items=[...container.children],size=options.pageSize||10;
  let host=container._tcItemPager;
  if(!host){host=container.ownerDocument.createElement('nav');const anchor=container.closest('table')?.parentElement||container;anchor.after(host);container._tcItemPager=host;}
  host.hidden=items.length===0;
  function show(target){const model=paginationModel({page:target,total:items.length,pageSize:size});items.forEach((item,index)=>{item.hidden=index<(model.page-1)*size||index>=model.page*size;});renderPagination(host,{page:model.page,total:items.length,pageSize:size,label:options.label,onPageChange:show});}
  show(options.page||1);
  return host;
 }
 if(typeof module!=='undefined'&&module.exports){module.exports={chartData,paginationModel,renderPagination,paginateItems};return;}
 root.TicketUI={renderChart,renderPagination,paginationModel,paginateItems};
 const doc=root.document;
 for(const host of doc.querySelectorAll('[data-tc-pagination]'))renderPagination(host,{page:host.dataset.page,total:host.dataset.total,pageSize:host.dataset.pageSize,href:page=>{const url=new URL(root.location.href);url.searchParams.set('page',String(page));return url.href;}});
 for(const container of doc.querySelectorAll('[data-tc-paginate-items]'))paginateItems(container,{pageSize:Number(container.dataset.pageSize)||6});
})(typeof window!=='undefined'?window:globalThis);
