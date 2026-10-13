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
 if(typeof module!=='undefined'&&module.exports){module.exports={chartData};return;}
 root.TicketUI={renderChart};
 const doc=root.document;
 function pagers(){
  for(const button of doc.querySelectorAll('button,a')){
   if(button.dataset.tcPageButton)continue;
   const text=button.textContent.trim();let direction;
   if(/^(←|&larr;)?\s*Trang trước\s*(←)?$/.test(text))direction='previous';
   if(/^\s*Trang sau\s*(→)?$/.test(text))direction='next';
   if(!direction)continue;button.dataset.tcPageButton=direction;button.classList.add('tc-page-button');button.setAttribute('aria-label',direction==='previous'?'Trang trước':'Trang sau');button.title=button.getAttribute('aria-label');button.textContent=direction==='previous'?'‹':'›';
   const nav=button.closest('nav,.pager,.finance-pagination');if(nav){nav.classList.add('tc-pagination');nav.setAttribute('aria-label','Chuyển trang');}
  }
 }
 pagers();new MutationObserver(pagers).observe(doc.body,{childList:true,subtree:true});
})(typeof window!=='undefined'?window:globalThis);
