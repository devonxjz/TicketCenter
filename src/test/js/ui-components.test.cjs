const test=require('node:test'),assert=require('node:assert/strict');
const {chartData}=require('../../main/webapp/assets/js/ui-components.js');
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
