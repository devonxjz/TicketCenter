package vn.ticketscenter.settlement.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Escapes the small server-rendered finance option lists without a JSTL dependency. */
public final class FinanceHtml {
    private FinanceHtml() {}

    public static String escape(Object value) {
        if (value == null) return "";
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String options(Object value, String labelKey) {
        if (!(value instanceof List<?> rows))
            throw new IllegalArgumentException("Expected option list");
        StringBuilder html = new StringBuilder();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> option))
                throw new IllegalArgumentException("Expected option row");
            Object rawId = option.get("id");
            String id = rawId == null ? "" : rawId.toString();
            if (id.length() != 36 || !UUID.fromString(id).toString().equalsIgnoreCase(id))
                throw new IllegalArgumentException("Expected UUID option");
            html.append("<option value=\"")
                    .append(id)
                    .append("\">")
                    .append(escape(option.get(labelKey)))
                    .append("</option>");
        }
        return html.toString();
    }

    public static String readySettlementRows(Object value) {
        if (!(value instanceof List<?> rows))
            throw new IllegalArgumentException("Expected event list");
        if (rows.isEmpty())
            return "<tr><td colspan=\"3\">Chưa có sự kiện đã kết thúc đủ điều kiện quyết toán.</td></tr>";
        var format =
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        var html = new StringBuilder();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> event))
                throw new IllegalArgumentException("Expected event row");
            String id = UUID.fromString(event.get("id").toString()).toString();
            String end = event.get("endTime") instanceof Instant at ? format.format(at) : "—";
            html.append("<tr><td>")
                    .append(escape(event.get("title")))
                    .append("</td><td>")
                    .append(end)
                    .append(
                            "</td><td><button type=\"button\" class=\"finance-button-secondary\" data-ready-event=\"")
                    .append(id)
                    .append("\" data-ready-title=\"")
                    .append(escape(event.get("title")))
                    .append("\">Xem bảng kê</button></td></tr>");
        }
        return html.toString();
    }
}
