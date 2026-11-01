/**
 * TicketsCenter Interactive Prototype Controller
 * Tương tác mô phỏng trên trình duyệt; không có backend, xác thực, email hoặc chuyển tiền thật.
 */

document.addEventListener("DOMContentLoaded", () => {
  initNavigation();
  initHoldTimer();
  initCategoryFilters();
  initSeatMapInteractivity();
  initCouponLogic();
  initCheckinScanner();
  initSettlementCalculator();
});

// Format VND currency
function formatVND(amount) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

// Global active frame switcher
function switchFrame(frameId) {
  const allFrames = document.querySelectorAll(".page-frame");
  allFrames.forEach(f => f.classList.add("hidden"));

  const target = document.getElementById(frameId);
  if (target) {
    target.classList.remove("hidden");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  // Update navigation pills
  const navBtns = document.querySelectorAll("[data-target-frame]");
  navBtns.forEach(btn => {
    if (btn.getAttribute("data-target-frame") === frameId) {
      btn.classList.add("bg-slate-900", "text-white");
      btn.classList.remove("bg-white", "text-slate-600");
    } else {
      btn.classList.remove("bg-slate-900", "text-white");
      btn.classList.add("bg-white", "text-slate-600");
    }
  });

  // Update quick jump selector in toolbar
  const frameSelector = document.getElementById("frameQuickJump");
  if (frameSelector) {
    frameSelector.value = frameId;
  }
}

// Navigation & Persona Switcher
function initNavigation() {
  const jumpSelector = document.getElementById("frameQuickJump");
  if (jumpSelector) {
    jumpSelector.addEventListener("change", (e) => {
      switchFrame(e.target.value);
    });
  }

  // Handle data-target-frame buttons
  document.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-target-frame]");
    if (btn) {
      const target = btn.getAttribute("data-target-frame");
      switchFrame(target);
    }
  });
}

// Category filter tabs on UI-01 (Like CTicket reference)
function initCategoryFilters() {
  const categoryPills = document.querySelectorAll(".category-pill");
  categoryPills.forEach(pill => {
    pill.addEventListener("click", () => {
      categoryPills.forEach(p => {
        p.classList.remove("bg-blue-600", "text-white", "shadow-sm");
        p.classList.add("bg-white", "text-slate-600", "border-slate-200");
      });
      pill.classList.remove("bg-white", "text-slate-600", "border-slate-200");
      pill.classList.add("bg-blue-600", "text-white", "shadow-sm");
    });
  });
}

// UI-04 Hold Timer: 10 minutes countdown (SPEC.md 6.4)
let holdSecondsLeft = 8 * 60 + 45; // 08:45
let holdInterval = null;

function initHoldTimer() {
  const timerElements = document.querySelectorAll(".hold-timer-display");
  if (timerElements.length === 0) return;

  function updateDisplay() {
    if (holdSecondsLeft <= 0) {
      clearInterval(holdInterval);
      timerElements.forEach(el => el.textContent = "00:00 (Đã hết hạn)");
      return;
    }
    const mins = Math.floor(holdSecondsLeft / 60);
    const secs = holdSecondsLeft % 60;
    const formatted = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    timerElements.forEach(el => el.textContent = formatted);
    holdSecondsLeft--;
  }

  updateDisplay();
  holdInterval = setInterval(updateDisplay, 1000);
}

// UI-02 Seat Map selection (Zone Seated & Standing, max 8 tickets)
let selectedSeats = [];
let standingCount = 0;
const STANDING_UNIT_PRICE = 40000;
const SEAT_VIP_PRICE = 1200000;

function initSeatMapInteractivity() {
  const seatButtons = document.querySelectorAll(".seat-interactive");
  seatButtons.forEach(seat => {
    seat.addEventListener("click", () => {
      const status = seat.getAttribute("data-seat-status");
      const seatLabel = seat.getAttribute("data-seat-label");
      if (status !== "AVAILABLE") return;

      const idx = selectedSeats.indexOf(seatLabel);
      if (idx > -1) {
        selectedSeats.splice(idx, 1);
        seat.classList.remove("bg-blue-600", "text-white", "ring-2", "ring-blue-400");
        seat.classList.add("bg-emerald-50", "text-emerald-700", "border-emerald-200");
      } else {
        if (selectedSeats.length + standingCount >= 8) {
          alert("Quy tắc nghiệp vụ: Mỗi lượt giữ tối đa 8 vé!");
          return;
        }
        selectedSeats.push(seatLabel);
        seat.classList.remove("bg-emerald-50", "text-emerald-700", "border-emerald-200");
        seat.classList.add("bg-blue-600", "text-white", "ring-2", "ring-blue-400");
      }
      updateTicketSummary();
    });
  });

  // Standing counter buttons
  const btnMinusStanding = document.getElementById("btnMinusStanding");
  const btnPlusStanding = document.getElementById("btnPlusStanding");
  const standingQty = document.getElementById("standingQty");

  if (btnMinusStanding && btnPlusStanding && standingQty) {
    btnMinusStanding.addEventListener("click", () => {
      if (standingCount > 0) {
        standingCount--;
        standingQty.textContent = standingCount;
        updateTicketSummary();
      }
    });

    btnPlusStanding.addEventListener("click", () => {
      if (selectedSeats.length + standingCount >= 8) {
        alert("Quy tắc nghiệp vụ: Mỗi lượt giữ tối đa 8 vé!");
        return;
      }
      standingCount++;
      standingQty.textContent = standingCount;
      updateTicketSummary();
    });
  }
}

function updateTicketSummary() {
  const summaryList = document.getElementById("ticketSummaryList");
  const subtotalDisplay = document.getElementById("ticketSubtotalDisplay");
  const ticketCountDisplay = document.getElementById("ticketCountDisplay");
  const btnHoldProceed = document.getElementById("btnHoldProceed");

  const totalTickets = selectedSeats.length + standingCount;
  const subtotal = (selectedSeats.length * SEAT_VIP_PRICE) + (standingCount * STANDING_UNIT_PRICE);

  if (ticketCountDisplay) ticketCountDisplay.textContent = totalTickets;
  if (subtotalDisplay) subtotalDisplay.textContent = formatVND(subtotal);

  if (summaryList) {
    if (totalTickets === 0) {
      summaryList.innerHTML = `<li class="text-sm text-slate-400 italic">Chưa chọn vé nào</li>`;
    } else {
      let html = "";
      selectedSeats.forEach(s => {
        html += `<li class="flex justify-between items-center text-xs py-1 border-b border-slate-100">
          <span class="font-medium text-slate-700">Ghế VIP ${s}</span>
          <span class="font-mono text-slate-900">${formatVND(SEAT_VIP_PRICE)}</span>
        </li>`;
      });
      if (standingCount > 0) {
        html += `<li class="flex justify-between items-center text-xs py-1 border-b border-slate-100">
          <span class="font-medium text-slate-700">${standingCount}x Vé Khu Đứng Tự Do</span>
          <span class="font-mono text-slate-900">${formatVND(standingCount * STANDING_UNIT_PRICE)}</span>
        </li>`;
      }
      summaryList.innerHTML = html;
    }
  }

  if (btnHoldProceed) {
    btnHoldProceed.disabled = totalTickets === 0;
  }
}

// UI-04 Coupon Validation logic (SPEC.md 6.5: max 30% cap)
let appliedDiscount = 0;
const ORDER_SUBTOTAL = 2400000; // 2x VIP seats (1.2m each)

function initCouponLogic() {
  const btnApplyCoupon = document.getElementById("btnApplyCoupon");
  const inputCoupon = document.getElementById("inputCouponCode");
  const couponFeedback = document.getElementById("couponFeedback");
  const orderDiscountDisplay = document.getElementById("orderDiscountDisplay");
  const orderTotalDisplay = document.getElementById("orderTotalDisplay");

  if (btnApplyCoupon && inputCoupon) {
    btnApplyCoupon.addEventListener("click", () => {
      const code = inputCoupon.value.trim().toUpperCase();
      const coupon = window.SEED_DATA.coupons.find(c => c.code === code && c.active);

      if (!coupon) {
        if (couponFeedback) {
          couponFeedback.className = "text-xs text-rose-500 mt-1 block font-medium";
          couponFeedback.textContent = "Mã không hợp lệ hoặc đã hết hạn!";
        }
        return;
      }

      // Check maxUses quota
      if (coupon.reservedCount + coupon.consumedCount >= coupon.maxUses) {
        if (couponFeedback) {
          couponFeedback.className = "text-xs text-rose-500 mt-1 block font-medium";
          couponFeedback.textContent = "Mã đã vượt quá giới hạn lượt sử dụng!";
        }
        return;
      }

      // Calculate discount & enforce 30% cap
      const maxDiscountAllowed = ORDER_SUBTOTAL * 0.30; // 30% subtotal
      let discount = 0;

      if (coupon.discountType === "PERCENTAGE") {
        discount = Math.floor(ORDER_SUBTOTAL * (coupon.discountValue / 100));
      } else {
        discount = Math.min(coupon.discountValue, maxDiscountAllowed);
      }

      discount = Math.min(discount, maxDiscountAllowed);
      appliedDiscount = discount;

      const finalTotal = ORDER_SUBTOTAL - appliedDiscount;

      if (orderDiscountDisplay) orderDiscountDisplay.textContent = `-${formatVND(appliedDiscount)}`;
      if (orderTotalDisplay) orderTotalDisplay.textContent = formatVND(finalTotal);

      if (couponFeedback) {
        couponFeedback.className = "text-xs text-emerald-600 mt-1 block font-medium";
        couponFeedback.textContent = `Áp dụng thành công mã ${code}! Giảm ${formatVND(appliedDiscount)} (Đã kiểm tra trần tối đa 30%).`;
      }
    });
  }
}

// UI-16 Check-in QR Scanner Simulator & Manual verification
function initCheckinScanner() {
  const btnVerifyManual = document.getElementById("btnVerifyManual");
  const inputManualCode = document.getElementById("inputManualCode");
  const scanResultModal = document.getElementById("scanResultModal");
  const scanResultTitle = document.getElementById("scanResultTitle");
  const scanResultDesc = document.getElementById("scanResultDesc");
  const scanResultIcon = document.getElementById("scanResultIcon");

  function showCheckinResult(status, title, desc) {
    if (!scanResultModal) return;
    scanResultModal.classList.remove("hidden");
    if (scanResultTitle) scanResultTitle.textContent = title;
    if (scanResultDesc) scanResultDesc.textContent = desc;

    if (status === "SUCCESS") {
      scanResultIcon.className = "w-16 h-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center text-3xl mx-auto mb-4";
      scanResultIcon.innerHTML = `<i class="ph ph-check-circle"></i>`;
    } else if (status === "USED") {
      scanResultIcon.className = "w-16 h-16 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center text-3xl mx-auto mb-4";
      scanResultIcon.innerHTML = `<i class="ph ph-warning-circle"></i>`;
    } else {
      scanResultIcon.className = "w-16 h-16 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center text-3xl mx-auto mb-4";
      scanResultIcon.innerHTML = `<i class="ph ph-x-circle"></i>`;
    }
  }

  if (btnVerifyManual && inputManualCode) {
    btnVerifyManual.addEventListener("click", () => {
      const code = inputManualCode.value.trim().toUpperCase();
      if (!code) return;

      if (code === "TC-VWM-99214-A") {
        showCheckinResult("SUCCESS", "VÉ HỢP LỆ — MỜI VÀO CỬA", "Vé Tiêu Chuẩn Người Lớn • Bảo tàng Phụ nữ VN • Kiểm tra lúc " + new Date().toLocaleTimeString('vi-VN'));
      } else if (code === "TC-STAGE-44810") {
        showCheckinResult("USED", "VÉ ĐÃ SỬ DỤNG", "Vé này đã được quét thành công lúc 19:45:10 ngày 28/10/2026 tại Cửa A!");
      } else if (code === "TC-STAGE-44811") {
        showCheckinResult("INVALID", "VÉ ĐANG CHỜ HOÀN TIỀN (REFUND_PENDING)", "Vé đang có yêu cầu hoàn tiền đang chờ duyệt, bị chặn check-in theo quy tắc nghiệp vụ!");
      } else {
        showCheckinResult("INVALID", "MÃ VÉ KHÔNG HỢP LỆ", "Không tìm thấy thông tin vé hoặc vé không thuộc sự kiện này!");
      }
    });
  }

  const btnCloseScanModal = document.getElementById("btnCloseScanModal");
  if (btnCloseScanModal && scanResultModal) {
    btnCloseScanModal.addEventListener("click", () => {
      scanResultModal.classList.add("hidden");
      if (inputManualCode) inputManualCode.value = "";
    });
  }

  // Quick simulate scan buttons
  const btnSimulateScanValid = document.getElementById("btnSimulateScanValid");
  const btnSimulateScanUsed = document.getElementById("btnSimulateScanUsed");

  if (btnSimulateScanValid) {
    btnSimulateScanValid.addEventListener("click", () => {
      showCheckinResult("SUCCESS", "VÉ HỢP LỆ — MỜI VÀO CỬA", "Vé Tiêu Chuẩn Người Lớn • Bảo tàng Phụ nữ VN • Quét qua Camera");
    });
  }
  if (btnSimulateScanUsed) {
    btnSimulateScanUsed.addEventListener("click", () => {
      showCheckinResult("USED", "VÉ ĐÃ SỬ DỤNG", "Vé này đã quét trước đó!");
    });
  }
}

// UI-22 Commission & Settlement Math Calculator (SPEC.md 6.11)
function initSettlementCalculator() {
  const rateInput = document.getElementById("settlementRateInput");
  const feeInput = document.getElementById("settlementFixedFeeInput");
  const grossInput = document.getElementById("settlementGrossInput");
  const refundInput = document.getElementById("settlementRefundInput");

  const calcRemaining = document.getElementById("calcRemainingAmount");
  const calcCommission = document.getElementById("calcCommissionAmount");
  const calcNet = document.getElementById("calcNetAmount");

  function recalculate() {
    if (!rateInput || !feeInput || !grossInput || !refundInput) return;
    const gross = parseFloat(grossInput.value) || 0;
    const refund = parseFloat(refundInput.value) || 0;
    const rate = parseFloat(rateInput.value) || 0;
    const fixedFee = parseFloat(feeInput.value) || 0;

    const remaining = Math.max(0, gross - refund);
    let commission = 0;
    if (remaining > 0) {
      commission = Math.min(remaining, Math.round(remaining * (rate / 100) + fixedFee));
    }
    const net = remaining - commission;

    if (calcRemaining) calcRemaining.textContent = formatVND(remaining);
    if (calcCommission) calcCommission.textContent = formatVND(commission);
    if (calcNet) calcNet.textContent = formatVND(net);
  }

  [rateInput, feeInput, grossInput, refundInput].forEach(el => {
    if (el) el.addEventListener("input", recalculate);
  });
}
