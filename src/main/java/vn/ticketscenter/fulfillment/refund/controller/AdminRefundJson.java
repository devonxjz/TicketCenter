package vn.ticketscenter.fulfillment.refund.controller;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundDecisionRequestDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

final class AdminRefundJson {
    private static final Set<String> DECISION_FIELDS = Set.of("decision", "note", "rejectReason", "rejectionReason");

    private AdminRefundJson() { }

    static AdminRefundDecisionRequestDto decision(String source) {
        var fields = object(source);
        if (!fields.containsKey("decision") || !DECISION_FIELDS.containsAll(fields.keySet())) {
            throw RefundException.invalid();
        }
        if (fields.containsKey("rejectReason") && fields.containsKey("rejectionReason")) {
            throw RefundException.invalid();
        }
        return new AdminRefundDecisionRequestDto(fields.get("decision"), fields.get("note"),
                fields.getOrDefault("rejectReason", fields.get("rejectionReason")));
    }

    static void retry(String source) {
        if (!object(source).isEmpty()) throw RefundException.invalid();
    }

    private static Map<String, String> object(String source) {
        var reader = new RefundJsonReader(source, 1000);
        var fields = new HashMap<String, String>();
        reader.expect('{');
        if (!reader.take('}')) {
            do {
                String key = reader.string();
                if (fields.containsKey(key)) throw RefundException.invalid();
                reader.expect(':');
                fields.put(key, reader.takeNull() ? null : reader.string());
            } while (reader.take(','));
            reader.expect('}');
        }
        reader.finish();
        return fields;
    }
}
