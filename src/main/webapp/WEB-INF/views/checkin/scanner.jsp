<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Soát vé sự kiện · TicketCenter</title>
<style>
*{box-sizing:border-box}body{margin:0;font:16px system-ui,sans-serif;background:#f4f6fb;color:#14213d}main{max-width:980px;margin:auto;padding:24px}header{display:flex;align-items:center;justify-content:space-between;gap:12px}a{color:#164cca}h1{font-size:28px;margin:12px 0}h2{font-size:20px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}.panel{background:white;padding:22px;border-radius:14px;border:1px solid #dde3ef;margin-top:18px}button{padding:12px 18px;border:0;border-radius:8px;background:#164cca;color:white;font:inherit;cursor:pointer;margin:5px 5px 5px 0}button:disabled{opacity:.5;cursor:default}.secondary{background:#52647d}textarea{width:100%;min-height:100px;font:inherit;padding:12px;border:1px solid #b8c5db;border-radius:8px}label{display:block;margin:14px 0 8px}.muted{color:#586880;font-size:14px}#reader{min-height:90px}#result{font-size:20px;min-height:180px;background:#eef3fb}#result.success{background:#e4f8ed;border-color:#15814a;color:#105b35}#result.failure{background:#fff0ee;border-color:#b83535;color:#8d2020}#result.uncertain{background:#fff6db;border-color:#ad7910;color:#754d00}table{width:100%;border-collapse:collapse;font-size:14px}th,td{padding:10px 6px;border-bottom:1px solid #e0e6f0;text-align:left}#history{overflow:auto}input[type=file]{width:100%}#notice{color:#9b2020} @media(max-width:650px){main{padding:16px}.grid{grid-template-columns:1fr}header{align-items:flex-start}h1{font-size:24px}.panel{padding:16px}}
</style></head><body><main>
<header><a id="back" href="#">← Chọn sự kiện</a><span class="muted">UI-16 · Soát vé</span></header>
<h1 id="event-title">Đang tải sự kiện…</h1><p id="window" class="muted"></p><p id="notice" role="alert"></p>
<div class="grid"><section class="panel"><h2>Quét mã QR</h2><div id="reader"></div>
<button id="camera" type="button" disabled>Bật camera</button><button id="stop" type="button" class="secondary" disabled>Tắt camera</button>
<label for="image">Hoặc chọn ảnh QR trên thiết bị</label><input id="image" type="file" accept="image/*" disabled>
<p class="muted">Camera cần quyền truy cập và kết nối HTTPS. Ảnh QR được đọc trên thiết bị.</p>
<form id="manual"><label for="code">Nhập mã vé hoặc nội dung QR</label><textarea id="code" maxlength="4096" required autocomplete="off" spellcheck="false" disabled></textarea>
<button id="submit" type="submit" disabled>Kiểm tra vé</button></form></section>
<section id="result" class="panel" role="status" aria-live="polite"><h2>Sẵn sàng soát vé</h2><p>Quét QR hoặc nhập mã để kiểm tra.</p></section></div>
<button id="retry" type="button" hidden>Tra cứu lại kết quả</button><button id="next" type="button" class="secondary" hidden>Quét vé tiếp theo</button>
<section class="panel"><h2>Lượt soát vé gần đây</h2><div id="history">Đang tải nhật ký…</div><button id="reload" type="button" class="secondary">Làm mới nhật ký</button></section>
</main><script src="../assets/checkin/vendor/html5-qrcode.min.js?v=2.3.8"></script><script src="../assets/checkin/scanner.js?v=khanh02-1"></script></body></html>
