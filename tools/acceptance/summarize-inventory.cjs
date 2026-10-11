'use strict';
const fs=require('node:fs'),path=require('node:path');
const catalog=JSON.parse(fs.readFileSync(process.argv[2],'utf8').replace(/^\uFEFF/,''));
const spec=fs.readFileSync(path.join(__dirname,'../../docs/references/SPEC.md'),'utf8');
const names=new Set(catalog.objects.map(o=>o.name));
const counts={};for(const o of catalog.objects)counts[o.type.trim()]=(counts[o.type.trim()]||0)+1;
const classes={User:'tc_users',Organization:'tc_organizations',Event:'tc_events',Zone:'tc_zones',Seat:'tc_seats',TicketHold:'tc_ticket_holds',TicketHoldItem:'tc_ticket_hold_items',Order:'tc_orders',OrderItem:'tc_order_items',Ticket:'tc_tickets',Payment:'tc_payments',Coupon:'tc_coupons',CommissionRule:'tc_commission_rules',Refund:'tc_refund_obligations',Settlement:'tc_settlements'};
const lines=['# SQL catalog inventory — 2026-10-11','','Source: NEW marked local schema fixture; no shared/Azure database. Existence is not behavior acceptance. The fixture includes adapters and verification-only tables.','','| Kind | Present |','|---|---:|',...Object.entries(counts).map(([k,n])=>`| ${k} | ${n} |`),`| Non-PK indexes (includes unique constraints) | ${catalog.indexes.filter(i=>!i.is_primary_key).length} |`,`| Non-PK, non-unique indexes | ${catalog.indexes.filter(i=>!i.is_primary_key&&!i.is_unique).length} |`,'','## 15-class persistence mapping','','| Class | Physical mapping | Existence only |','|---|---|---|',...Object.entries(classes).map(([c,n])=>`| ${c} | ${n} | ${names.has(n)?'PRESENT':'MISSING'} |`),'','Refund maps to the finance obligation ledger; legacy tc_refunds represents owner attempts and is normalized through adapters. Organization lifecycle, role synchronization, Email/QR/payment workers and connected E2E still require owner behavior evidence. Technical tables do not add domain classes.','','## SPEC SQL names','','PRESENT means the exact named object exists, not that its parameters, grants or behavior satisfy SPEC. MISSING may have a legacy equivalent requiring owner confirmation.','','| SPEC ID | Required name | Exact name |','|---|---|---|'];
let present=0,total=0;
for(const match of spec.matchAll(/^\| ((?:V|SP|F|TR|IX)\d{2}) \| (.+)$/gm)){
 const name=match[2].match(/(?:dbo\.)?((?:vw_|usp_|fn_|trg_|IX_)[A-Za-z0-9_]+)/)?.[1];
 if(!name)throw new Error('SPEC SQL name not parsed: '+match[1]);
 const exists=match[1].startsWith('IX')?catalog.indexes.some(i=>i.name===name):names.has(name);
 total++;if(exists)present++;lines.push(`| ${match[1]} | ${name} | ${exists?'PRESENT':'MISSING'} |`);
}
if(total!==62)throw new Error('Expected 62 SPEC names:10 views+17 SP+10 functions+10 triggers+15 indexes; got '+total);
lines.push('',`Exact named objects: ${present}/${total}. Transactions and constraint groups require separate behavioral evidence; object totals cannot close #33.`,'');
process.stdout.write(lines.join('\n'));
