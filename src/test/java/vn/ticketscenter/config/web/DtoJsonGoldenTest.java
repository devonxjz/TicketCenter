package vn.ticketscenter.config.web;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import vn.ticketscenter.event.dto.EventDtos;
import vn.ticketscenter.event.dto.EventFilter;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInEventSummaryDto;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInWindowStatusDto;
import vn.ticketscenter.fulfillment.checkin.dto.RecentCheckInLogDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.dto.OrganizationDecisionResultDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestPageDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.dto.OtpVerifyResponseDto;
import vn.ticketscenter.identity.dto.ProfileDto;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.dao.OrderRepository.OrderHistoryPage;
import vn.ticketscenter.order.dto.OrderHistoryItemDto;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class DtoJsonGoldenTest {

    private static final boolean UPDATE = Boolean.getBoolean("golden.update");
    private static final Path GOLDEN_ROOT = Path.of("src", "test", "resources", "golden");

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ID3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant AT = Instant.parse("2026-10-19T01:02:03Z");
    private static final Instant AT2 = Instant.parse("2026-11-20T04:05:06.789Z");
    private static final String TEXT = "Đêm \"nhạc\" \\ mới\nxuống\r\ndòng";

    @TestFactory
    List<DynamicTest> dtoWireFormatMatchesGolden() {
        Map<String, Supplier<String>> cases = new LinkedHashMap<>();

        cases.put("order/order-history-item-full", () -> orderHistoryItem(true).toJson());
        cases.put("order/order-history-item-sparse", () -> orderHistoryItem(false).toJson());
        cases.put("order/order-history-page-full", () ->
                new OrderHistoryPage(2, 10, 12, List.of(orderHistoryItem(true), orderHistoryItem(false))).toJson());
        cases.put("order/order-history-page-empty", () -> new OrderHistoryPage(1, 10, 0, List.of()).toJson());
        cases.put("order/coupon-admin-full", () -> couponAdmin(true).toJson());
        cases.put("order/coupon-admin-sparse", () -> couponAdmin(false).toJson());

        cases.put("identity/otp-verify-reset", () -> OtpVerifyResponseDto.forPasswordReset(TEXT, "tok\"en").toJson());
        cases.put("identity/otp-verify-email", () -> OtpVerifyResponseDto.forEmailVerification(null).toJson());
        cases.put("identity/profile-full", () -> new ProfileDto(ID, "a@b.vn", "user\"n", TEXT, "0900\\1").toJson());
        cases.put("identity/profile-sparse", () -> new ProfileDto(null, null, null, null, null).toJson());
        cases.put("identity/current-user-full", () ->
                new CurrentUserDto(ID, "a@b.vn", "user\"n", TEXT, PlatformRole.ADMIN, true).toJson());
        cases.put("identity/current-user-sparse", () -> new CurrentUserDto(null, null, null, null, null, false).toJson());
        cases.put("identity/organization-member-full", () ->
                new OrganizationMemberDto(ID, ID2, "u\"n", TEXT, "m@b.vn", OrganizationRole.MANAGER, true, AT).toJson());
        cases.put("identity/organization-member-sparse", () ->
                new OrganizationMemberDto(null, null, null, null, null, null, false, null).toJson());
        cases.put("identity/organization-request-summary-full", () -> requestSummary(true).toJson());
        cases.put("identity/organization-request-summary-sparse", () -> requestSummary(false).toJson());
        cases.put("identity/organization-request-detail-full", () -> requestDetail(true).toJson());
        cases.put("identity/organization-request-detail-sparse", () -> requestDetail(false).toJson());
        cases.put("identity/organization-request-page-full", () ->
                new OrganizationRequestPageDto(List.of(requestSummary(true), requestSummary(false)), 7, 2, 5).toJson());
        cases.put("identity/organization-request-page-empty", () ->
                new OrganizationRequestPageDto(List.of(), 0, 1, 20).toJson());
        cases.put("identity/organization-decision-approved-full", () ->
                OrganizationDecisionResultDto.approved(ID, requestDetail(true)).toJson());
        cases.put("identity/organization-decision-approved-sparse", () ->
                OrganizationDecisionResultDto.approved(ID, requestDetail(false)).toJson());
        cases.put("identity/organization-decision-rejected-full", () ->
                OrganizationDecisionResultDto.rejected(ID, TEXT, requestDetail(true)).toJson());
        cases.put("identity/organization-decision-rejected-sparse", () ->
                OrganizationDecisionResultDto.rejected(ID, null, requestDetail(false)).toJson());

        cases.put("ticket/ticket-summary-full", () -> ticketSummary(true).toJson());
        cases.put("ticket/ticket-summary-sparse", () -> ticketSummary(false).toJson());
        cases.put("ticket/ticket-detail-full", () -> ticketDetail(true).toJson());
        cases.put("ticket/ticket-detail-sparse", () -> ticketDetail(false).toJson());

        cases.put("checkin/window-status-full", () ->
                new CheckInWindowStatusDto(ID, true, "OPEN", AT, AT2, AT, TEXT).toJson());
        cases.put("checkin/window-status-sparse", () ->
                new CheckInWindowStatusDto(null, false, null, null, null, null, null).toJson());
        cases.put("checkin/scan-response-success", () ->
                new CheckInScanResponseDto("CHECKED_IN_SUCCESS", ID, "TK\"1", TEXT, "Zone \\A", "A-1", AT, AT2, ID2).toJson());
        cases.put("checkin/scan-response-sparse", () ->
                new CheckInScanResponseDto("TICKET_NOT_FOUND", null, null, null, null, null, null, AT2, null).toJson());
        cases.put("checkin/recent-log-full", () ->
                new RecentCheckInLogDto(ID, ID2, "CHECKED_IN_SUCCESS", AT, TEXT, "Zone \"A\"", "A-1").toJson());
        cases.put("checkin/recent-log-sparse", () ->
                new RecentCheckInLogDto(ID, null, "TICKET_NOT_FOUND", AT, null, null, null).toJson());
        cases.put("checkin/event-summary-full", () ->
                new CheckInEventSummaryDto(ID, TEXT, "Sân \"A\"", AT, AT2, AT, true, 100, 40, "PUBLISHED").toJson());
        cases.put("checkin/event-summary-sparse", () ->
                new CheckInEventSummaryDto(null, null, null, null, null, null, false, 0, 0, null).toJson());

        cases.put("event/event-full", () -> event(true).toJson());
        cases.put("event/event-sparse", () -> event(false).toJson());
        cases.put("event/zone-full", () ->
                new EventDtos.Zone(ID, ID2, "VIP \"A\"", "SEATED", new BigDecimal("150000"), 100, 10, 20, 70).toJson());
        cases.put("event/zone-sparse", () -> new EventDtos.Zone(null, null, null, null, null, 0, 0, 0, 0).toJson());
        cases.put("event/seat-full", () -> new EventDtos.Seat(ID, ID2, "R\"1", 7, "A-7", "AVAILABLE").toJson());
        cases.put("event/seat-sparse", () -> new EventDtos.Seat(null, null, null, 0, null, null).toJson());
        cases.put("event/category-full", () -> new EventDtos.Category(ID, TEXT, "am-nhac").toJson());
        cases.put("event/category-sparse", () -> new EventDtos.Category(null, null, null).toJson());
        cases.put("event/page-full", () ->
                new EventDtos.Page(List.of(event(true), event(false)), 41).toJson(eventFilter()));
        cases.put("event/page-empty", () -> new EventDtos.Page(List.of(), 0).toJson(eventFilter()));
        cases.put("event/data-array", () ->
                EventDtos.data(EventDtos.array(List.of(new EventDtos.Category(ID, "A", "a")), EventDtos.Category::toJson)));

        return cases.entrySet().stream()
                .map(entry -> dynamicTest(entry.getKey(), () -> assertGolden(entry.getKey(), entry.getValue().get())))
                .toList();
    }

    private static void assertGolden(String name, String actual) throws IOException {
        String resource = "/golden/" + name + ".json";
        if (UPDATE) {
            Path target = GOLDEN_ROOT.resolve(name + ".json");
            Files.createDirectories(target.getParent());
            Files.writeString(target, actual, StandardCharsets.UTF_8);
            return;
        }
        try (InputStream in = DtoJsonGoldenTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing golden " + resource);
            assertEquals(new String(in.readAllBytes(), StandardCharsets.UTF_8), actual);
        }
    }

    private static OrderHistoryItemDto orderHistoryItem(boolean full) {
        if (full) {
            return new OrderHistoryItemDto(ID, "ORD-\"1\"\\x", ID2, TEXT, "Sân\r\nA", AT,
                    new BigDecimal("200000"), new BigDecimal("20000"), new BigDecimal("180000"),
                    "PAID", AT, AT2, "SALE\"10", 3);
        }
        return new OrderHistoryItemDto(ID, "ORD-2", null, null, null, null, null, null, null,
                "PENDING", null, null, null, 0);
    }

    private static CouponAdminDto couponAdmin(boolean full) {
        if (full) {
            return new CouponAdminDto(ID, ID2, TEXT, "SALE\"10", "PERCENTAGE", new BigDecimal("10"),
                    new BigDecimal("5000"), new BigDecimal("50000"), 100, 5, 20, 75, AT, AT2, true, 4);
        }
        return new CouponAdminDto(ID, ID2, null, "FIXED5", "FIXED", null, null, null, 0, 0, 0, 0,
                null, null, false, 0);
    }

    private static OrganizationRequestSummaryDto requestSummary(boolean full) {
        if (full) {
            return new OrganizationRequestSummaryDto(ID, TEXT, "c@b.vn", OrganizationStatus.PENDING_APPROVAL, AT,
                    "Lý do \"x\"", "r@b.vn", "0900", ID2, "Người \"gửi\"",
                    "Mô tả \"x\"\n[Event: Đêm nhạc | Date: 2026-11-01 | Venue: Sân \"A\"]");
        }
        return new OrganizationRequestSummaryDto(ID, null, null, null, null, null, null, null, null, null, null);
    }

    private static OrganizationRequestDetailDto requestDetail(boolean full) {
        if (full) {
            return new OrganizationRequestDetailDto(ID, TEXT, "c@b.vn", "0900", 
                    "Mô tả \"x\"\n[Event: Đêm nhạc | Date: 2026-11-01 | Start: 19:00 | End: 22:00 | Venue: Sân \"A\"]",
                    OrganizationStatus.APPROVED, ID2, AT, "Lý do \"x\"", ID3);
        }
        return new OrganizationRequestDetailDto(ID, null, null, null, null, null, null, null, null, null);
    }

    private static TicketSummaryDto ticketSummary(boolean full) {
        if (full) {
            return new TicketSummaryDto(ID, ID2, "TK-\"1\"", TEXT, "Sân\r\nA", AT, "Zone \\A", "A-1", 100000, "ISSUED", AT2, 3);
        }
        return new TicketSummaryDto(ID, ID2, "TK-2", "Show", null, null, null, null, 0, "ISSUED", null, 0);
    }

    private static TicketDetailDto ticketDetail(boolean full) {
        if (full) {
            return new TicketDetailDto(ID, ID2, "ORD-\"1\"", "TK-\"1\"", ID3, TEXT, "Sân\r\nA", "Q1\nHCM", AT, AT2,
                    "Zone \\A", "A-1", 100000, "ISSUED", AT2, 3);
        }
        return new TicketDetailDto(ID, ID2, null, "TK-2", null, "Show", null, null, null, null, null, null, 0,
                "ISSUED", null, 0);
    }

    private static EventDtos.Event event(boolean full) {
        if (full) {
            return new EventDtos.Event(ID, ID2, TEXT, "Mô tả\r\n\"x\"", "https://img/x.png", "Sân \"A\"", "Q1\\HCM",
                    AT, AT2, AT, AT2, new BigDecimal("100000"), "Âm nhạc", "Org \"X\"", new BigDecimal("500000"), true);
        }
        return new EventDtos.Event(ID, null, "Show", null, null, null, null, null, null, null, null, null, null,
                null, null, false);
    }

    private static EventFilter eventFilter() {
        return new EventFilter("", null, null, null, "startTime_asc", 2, 20);
    }
}
