(function(root){
 'use strict';
 async function signOut(fetch,base){
  const before=await fetch(base+'/api/auth/csrf',{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}}),csrf=await before.json();
  if(!before.ok||!csrf.data?.token)throw new Error(csrf.error?.message||'Không thể xác nhận phiên đăng nhập.');
  const response=await fetch(base+'/api/auth/logout',{method:'POST',credentials:'same-origin',headers:{Accept:'application/json','Content-Type':'application/json','X-CSRF-Token':csrf.data.token},body:'{}'}),result=await response.json();
  if(!response.ok||!result.data?.loggedOut)throw new Error(result.error?.message||'Chưa đăng xuất được. Vui lòng thử lại.');
 }
 if(typeof module!=='undefined'&&module.exports){module.exports={signOut};return;}
 const doc=root.document,nav=doc.querySelector('[data-site-header]'),main=doc.querySelector('main');if(main&&!main.id)main.id='main-content';if(!nav)return;
 const base=nav.dataset.context||'',category=doc.getElementById('header-category'),notice=doc.getElementById('site-notice'),query=new URLSearchParams(root.location.search),search=nav.querySelector('input[name=keyword]');if(search)search.value=query.get('keyword')||'';
 if(category)root.fetch(base+'/api/event-categories',{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}}).then(async response=>{
  const payload=await response.json();if(!response.ok)throw new Error('Danh mục chưa khả dụng');
  for(const value of payload.data){const option=doc.createElement('option');option.value=value.categoryId;option.textContent=value.name;category.append(option);}category.value=doc.getElementById('category')?.value||new URLSearchParams(root.location.search).get('categoryId')||'';
 }).catch(()=>{category.disabled=true;category.setAttribute('title','Chưa tải được danh mục. Bạn vẫn có thể tìm theo từ khóa.');});
 const logout=doc.getElementById('site-logout');if(logout)logout.addEventListener('click',async()=>{logout.disabled=true;try{await signOut(root.fetch.bind(root),base);root.location.assign(base+'/');}catch(_){root.location.assign(base+'/auth/logout');}});
 const searchForm=nav.querySelector('form.tc-header-search');if(searchForm){searchForm.addEventListener('submit',event=>{if(doc.body.dataset.publicPage==='catalog'){const filters=doc.getElementById('filters');if(filters){event.preventDefault();const kw=(searchForm.querySelector('input[name=keyword]')?.value||'').trim(),cat=searchForm.querySelector('select[name=categoryId]')?.value||'';const kwInput=filters.elements.namedItem('keyword');if(kwInput)kwInput.value=kw;const catSelect=filters.elements.namedItem('categoryId');if(catSelect)catSelect.value=cat;filters.dispatchEvent(new Event('submit',{cancelable:true,bubbles:true}));}}});}
 const mainKeyword=doc.getElementById('keyword');if(mainKeyword&&search){mainKeyword.addEventListener('input',()=>{search.value=mainKeyword.value;});}
})(typeof window!=='undefined'?window:globalThis);

// Hiển thị <time class="tc-local-time" datetime="ISO-8601"> theo giờ địa phương của người xem.
(function () {
    if (typeof document === "undefined") return;
    const format = () => {
        for (const node of document.querySelectorAll("time.tc-local-time[datetime]")) {
            const value = new Date(node.getAttribute("datetime"));
            if (!Number.isNaN(value.getTime())) node.textContent = value.toLocaleString("vi-VN", { dateStyle: "medium", timeStyle: "short" });
        }
    };
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", format); else format();
})();
