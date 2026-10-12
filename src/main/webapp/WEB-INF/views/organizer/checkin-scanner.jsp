<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Soát vé — TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <style>
        body { min-height: 100vh; background: #101114; color: #fff; }
        .camera { width: 100%; max-width: 480px; aspect-ratio: 1; background: #050505; object-fit: cover; }
        .scan-card { max-width: 480px; }
    </style>
</head>
<body data-context="<c:out value='${pageContext.request.contextPath}'/>"
      data-event="<c:out value='${param.eventId}'/>"
      data-csrf="<c:out value='${sessionScope.TC_CSRF_TOKEN}'/>">
<header class="container py-4 d-flex align-items-center justify-content-between">
    <a class="text-white" href="${pageContext.request.contextPath}/organizer/checkin/select">Đổi sự kiện</a>
    <strong>Soát vé</strong>
</header>
<main class="container pb-5"><div class="scan-card mx-auto">
    <p id="cameraStatus" class="text-white-50" role="status">Đang khởi động camera…</p>
    <video id="camera" class="camera border border-secondary" autoplay playsinline muted aria-label="Camera quét mã QR"></video>
    <div class="d-flex gap-2 my-3">
        <button id="startCamera" type="button" class="btn btn-outline-light">Bật camera</button>
        <button id="switchCamera" type="button" class="btn btn-outline-light">Đổi camera</button>
    </div>
    <form id="manualForm" class="border border-secondary p-3">
        <label for="manualCode" class="form-label">Nhập mã vé khi không thể quét QR</label>
        <div class="input-group">
            <input id="manualCode" class="form-control" autocomplete="off" maxlength="100" required placeholder="TC-…">
            <button class="btn btn-danger" type="submit">Soát vé</button>
        </div>
    </form>
    <div id="result" class="alert mt-3 d-none" role="alert" aria-live="assertive"></div>
</div></main>
<script>
(() => {
    const settings = document.body.dataset;
    const video = document.getElementById('camera');
    const status = document.getElementById('cameraStatus');
    const result = document.getElementById('result');
    const eventId = settings.event;
    const eventValid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(eventId);
    let stream = null, detector = null, timer = null, busy = false, detecting = false, facing = 'environment';
    function show(message, success) {
        result.textContent = message;
        result.className = 'alert mt-3 ' + (success ? 'alert-success' : 'alert-danger');
    }
    function stopCamera() {
        if (timer) clearInterval(timer);
        timer = null;
        if (stream) stream.getTracks().forEach(track => track.stop());
        stream = null;
        video.srcObject = null;
    }
    async function submit(payload) {
        if (busy) return;
        busy = true;
        try {
            const response = await fetch(settings.context + '/api/check-ins', {
                method: 'POST', credentials: 'same-origin',
                headers: {'Content-Type': 'application/json', 'X-CSRF-Token': settings.csrf},
                body: JSON.stringify({eventId, ...payload})
            });
            const json = await response.json();
            if (!response.ok) { show(json.error?.message || 'Không thể soát vé. Vui lòng thử lại.', false); return; }
            const outcome = json.data.result;
            const messages = {
                SUCCESS: 'Vé hợp lệ — mời khách vào cổng.', ALREADY_USED: 'Vé đã được sử dụng.',
                WRONG_EVENT: 'Vé thuộc sự kiện khác.', NOT_FOUND: 'Không tìm thấy vé.',
                EVENT_CANCELLED: 'Sự kiện đã bị hủy.', TOO_EARLY: 'Chưa đến giờ mở cổng.',
                TOO_LATE: 'Cổng soát vé đã đóng.', NOT_ACTIVE: 'Vé không còn hiệu lực.'
            };
            show(messages[outcome] || 'Không thể xác nhận vé.', outcome === 'SUCCESS');
        } catch (_) { show('Mất kết nối đến máy chủ. Kiểm tra mạng rồi thử lại.', false); }
        finally { busy = false; }
    }
    async function startCamera() {
        stopCamera();
        if (!eventValid) { status.textContent = 'Hãy chọn một sự kiện trước khi soát vé.'; return; }
        if (!('BarcodeDetector' in window) || !navigator.mediaDevices?.getUserMedia) {
            status.textContent = 'Trình duyệt không hỗ trợ quét QR. Hãy nhập mã vé bên dưới.'; return;
        }
        try {
            detector = new BarcodeDetector({formats: ['qr_code']});
            stream = await navigator.mediaDevices.getUserMedia({video: {facingMode: facing}, audio: false});
            video.srcObject = stream;
            await video.play();
            status.textContent = 'Đưa mã QR vào khung camera.';
            timer = setInterval(async () => {
                if (busy || detecting || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return;
                detecting = true;
                const activeStream = stream;
                try {
                    const codes = await detector.detect(video);
                    if (stream === activeStream && codes.length && codes[0].rawValue) {
                        stopCamera();
                        status.textContent = 'Đã đọc QR. Bấm Bật camera để quét vé tiếp theo.';
                        await submit({qrEnvelope: codes[0].rawValue});
                    }
                } catch (_) { status.textContent = 'Không đọc được camera. Hãy nhập mã vé bên dưới.'; stopCamera(); }
                finally { detecting = false; }
            }, 600);
        } catch (_) { status.textContent = 'Không thể mở camera. Hãy cấp quyền hoặc nhập mã vé bên dưới.'; stopCamera(); }
    }
    document.getElementById('startCamera').addEventListener('click', startCamera);
    document.getElementById('switchCamera').addEventListener('click', () => {
        facing = facing === 'environment' ? 'user' : 'environment'; startCamera();
    });
    document.getElementById('manualForm').addEventListener('submit', event => {
        event.preventDefault();
        if (!eventValid) { show('Hãy chọn một sự kiện trước khi soát vé.', false); return; }
        submit({ticketCode: document.getElementById('manualCode').value.trim()});
    });
    window.addEventListener('pagehide', stopCamera);
    startCamera();
})();
</script>
</body>
</html>
