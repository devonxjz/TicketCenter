package vn.ticketscenter.fulfillment.refund.controller;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.CreateRefundRequestDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

final class RefundRequestJson {
    private RefundRequestJson() {}

    static CreateRefundRequestDto parse(String source) {
        var reader = new RefundJsonReader(source, 500);
        Set<String> fields = new HashSet<>();
        UUID order = null;
        String reason = null;
        List<UUID> tickets = null;

        reader.expect('{');
        do {
            String key = reader.string();
            if (!fields.add(key)) {
                throw RefundException.invalid();
            }
            reader.expect(':');
            switch (key) {
                case "orderId" -> order = RefundWeb.id(reader.string());
                case "reason" -> reason = reader.string();
                case "ticketIds" -> tickets = tickets(reader);
                default -> throw RefundException.invalid();
            }
        } while (reader.take(','));
        reader.expect('}');
        reader.finish();
        if (fields.size() != 3) {
            throw RefundException.invalid();
        }
        return new CreateRefundRequestDto(order, tickets, reason);
    }

    private static List<UUID> tickets(RefundJsonReader reader) {
        var tickets = new ArrayList<UUID>();
        reader.expect('[');
        do {
            if (tickets.size() == 8) {
                throw RefundException.invalid();
            }
            tickets.add(RefundWeb.id(reader.string()));
        } while (reader.take(','));
        reader.expect(']');
        return tickets;
    }
}
