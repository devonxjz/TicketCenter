package vn.ticketscenter.order.dto;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.order.dto.OrderDetailDto.OrderItemDetail;
import vn.ticketscenter.order.dto.OrderDetailDto.OrderTicketDetail;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderDetailDtoJsonTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant AT = Instant.parse("2026-10-19T01:02:03Z");

    @Test
    void fullOrderKeepsWireFormat() throws IOException {
        OrderDetailDto order = new OrderDetailDto(
                ID, ID, "ORD-\"1\"\\x", ID, "Đêm \"nhạc\"\nmới", "Sân", "Q1\r\nHCM", AT, AT,
                new BigDecimal("200000"), new BigDecimal("0"), new BigDecimal("200000"),
                "PAID", AT, AT, "SALE10", "SUCCESS", "TXN1", "PAY1", AT,
                List.of(
                        new OrderItemDetail(ID, "VIP \"A\"", "A-1", 2, new BigDecimal("100000"), new BigDecimal("200000")),
                        new OrderItemDetail(ID, "GA", null, 1, null, null)),
                List.of(new OrderTicketDetail(ID, "TK-1", "ISSUED", 100000L)));

        assertEquals(golden("order-detail-full.json"), order.toJson());
    }

    @Test
    void missingValuesKeepEmptyStringsAndZeroes() throws IOException {
        OrderDetailDto order = new OrderDetailDto(
                ID, null, "ORD-2", null, null, null, null, null, null, null, null, null,
                "PENDING", null, null, null, null, null, null, null, null, null);

        assertEquals(golden("order-detail-sparse.json"), order.toJson());
    }

    private static String golden(String name) throws IOException {
        try (InputStream in = OrderDetailDtoJsonTest.class.getResourceAsStream("/golden/order/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
