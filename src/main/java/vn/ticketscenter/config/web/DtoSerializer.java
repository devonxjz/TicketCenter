package vn.ticketscenter.config.web;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Universal JSON serializer for DTOs using reflection.
 * Eliminates need for manual toJson() methods.
 * Handles: primitives, String, UUID, Instant, BigDecimal, Collections, and nested records.
 */
public final class DtoSerializer {

    private DtoSerializer() {}

    /**
     * Serialize any object to JSON string.
     */
    public static String toJson(Object dto) {
        if (dto == null) return "null";
        if (dto instanceof String s) return escape(s);
        if (dto instanceof Number n) return n.toString();
        if (dto instanceof Boolean b) return b.toString();
        if (dto instanceof UUID u) return escape(u.toString());
        if (dto instanceof Instant i) return escape(i.toString());
        if (dto instanceof Collection<?> c) return collectionToJson(c);
        if (dto.getClass().isRecord()) return recordToJson(dto);
        return "null";
    }

    /**
     * Serialize a record to JSON object.
     */
    private static String recordToJson(Object dto) {
        StringBuilder json = new StringBuilder("{");
        RecordComponent[] components = dto.getClass().getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            try {
                Object value = component.getAccessor().invoke(dto);
                if (i > 0) json.append(",");
                json.append(escape(component.getName())).append(":").append(toJson(value));
            } catch (Exception e) {
                // Skip on error
            }
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Serialize a collection to JSON array.
     */
    private static String collectionToJson(Collection<?> collection) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Object item : collection) {
            if (!first) json.append(",");
            first = false;
            json.append(toJson(item));
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Escape string for JSON (quote and escape special chars).
     */
    private static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append("\"").toString();
    }
}
