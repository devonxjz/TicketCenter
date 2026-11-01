const fs = require('fs');
const path = require('path');

const rootDir = __dirname;

// 1. Clean organizer-events.html
let orgEvents = fs.readFileSync(path.join(rootDir, 'organizer-events.html'), 'utf8');
orgEvents = orgEvents.replace(/<span class="status-pill status-dark">Đang mở bán<\/span>/g, '<span class="status-pill status-success">Đang mở bán</span>');
orgEvents = orgEvents.replace(/<span class="status-pill status-subtle">Chờ duyệt<\/span>/g, '<span class="status-pill status-amber">Chờ duyệt</span>');
orgEvents = orgEvents.replace(/<span class="status-pill status-red">Cần sửa<\/span>/g, '<span class="status-pill status-red">Cần sửa</span>');
orgEvents = orgEvents.replace(/<a href="event-detail\.html"[^>]*><i class="fa-solid fa-eye"><\/i><\/a>/g, '<a href="event-detail.html" target="_blank" class="btn btn-outline-dark rounded-0 px-2 py-1">Xem</a>');
orgEvents = orgEvents.replace(/<a href="organizer-event-editor\.html"[^>]*><i class="fa-solid fa-pen"><\/i><\/a>/g, '<a href="organizer-event-editor.html" class="btn btn-dark rounded-0 px-2 py-1">Sửa</a>');
orgEvents = orgEvents.replace(/<a href="organizer-reports\.html"[^>]*><i class="fa-solid fa-chart-line"><\/i><\/a>/g, '<a href="organizer-reports.html" class="btn btn-outline-dark rounded-0 px-2 py-1">Báo cáo</a>');
fs.writeFileSync(path.join(rootDir, 'organizer-events.html'), orgEvents, 'utf8');

// 2. Clean organizer-dashboard.html
let orgDash = fs.readFileSync(path.join(rootDir, 'organizer-dashboard.html'), 'utf8');
orgDash = orgDash.replace(/<i class="fa-solid fa-pen-to-square"><\/i>/g, 'Sửa');
orgDash = orgDash.replace(/<i class="fa-solid fa-arrow-right"><\/i>/g, 'Xem');
orgDash = orgDash.replace(/<span class="status-pill status-dark">Đang mở bán<\/span>/g, '<span class="status-pill status-success">Đang mở bán</span>');
orgDash = orgDash.replace(/<span class="status-pill status-subtle">Chờ duyệt<\/span>/g, '<span class="status-pill status-amber">Chờ duyệt</span>');
fs.writeFileSync(path.join(rootDir, 'organizer-dashboard.html'), orgDash, 'utf8');

// 3. Clean organizer-members.html
let orgMembers = fs.readFileSync(path.join(rootDir, 'organizer-members.html'), 'utf8');
orgMembers = orgMembers.replace(/<i class="fa-solid fa-shield-halved text-danger me-1"><\/i>/g, '');
orgMembers = orgMembers.replace(/<i class="fa-solid fa-lock text-muted ms-1"><\/i>/g, '');
fs.writeFileSync(path.join(rootDir, 'organizer-members.html'), orgMembers, 'utf8');

// 4. Clean organizer-reports.html
let orgReports = fs.readFileSync(path.join(rootDir, 'organizer-reports.html'), 'utf8');
orgReports = orgReports.replace(/<i class="fa-solid fa-circle-check text-dark me-2"><\/i>/g, '');
fs.writeFileSync(path.join(rootDir, 'organizer-reports.html'), orgReports, 'utf8');

// 5. Clean refund-request.html
let refundReq = fs.readFileSync(path.join(rootDir, 'refund-request.html'), 'utf8');
refundReq = refundReq.replace(/<i class="fa-solid fa-circle-exclamation text-danger me-2"><\/i>/g, '');
refundReq = refundReq.replace(/<i class="fa-solid fa-paper-plane me-2"><\/i>/g, '');
fs.writeFileSync(path.join(rootDir, 'refund-request.html'), refundReq, 'utf8');

// 6. Clean checkout.html
let checkout = fs.readFileSync(path.join(rootDir, 'checkout.html'), 'utf8');
checkout = checkout.replace(/<i class="fa-solid fa-lock text-danger me-2"><\/i>/g, '');
checkout = checkout.replace(/<i class="fa-regular fa-clock me-1 text-danger"><\/i>/g, '');
checkout = checkout.replace(/<i class="fa-solid fa-check me-2"><\/i>/g, '');
fs.writeFileSync(path.join(rootDir, 'checkout.html'), checkout, 'utf8');

console.log('Successfully polished all tables, buttons, and status pills!');
