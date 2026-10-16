package vn.ticketscenter.event.dto;

import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.service.EventProblem;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public record EventCommand(
        String operation,
        UUID organizationId,
        UUID eventId,
        UUID zoneId,
        Map<String, String> fields) {
    private static final Set<String> DRAFT =
            Set.of(
                    "title",
                    "description",
                    "categoryId",
                    "venueName",
                    "venueAddress",
                    "saleStartTime",
                    "saleEndTime",
                    "startTime",
                    "endTime");
    private static final Set<String> ZONE =
            Set.of(
                    "name",
                    "zoneType",
                    "basePrice",
                    "capacity",
                    "rows",
                    "seatsPerRow",
                    "expectedVersion");
    private static final Set<String> SCHEDULE =
            Set.of("saleStartTime", "saleEndTime", "startTime", "endTime");

    public EventCommand {
        fields = Collections.unmodifiableMap(new TreeMap<>(fields));
    }

    public String scope() {
        return operation.equals("PUBLISH") || operation.equals("REJECT") ? "EVENT_REVIEW" : "EVENT";
    }

    public String json() {
        return fields.entrySet().stream()
                .map(
                        e ->
                                HttpResponses.jsonString(e.getKey())
                                        + ":"
                                        + HttpResponses.jsonString(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    public String hash() {
        return sha256(
                scope()
                        + "|"
                        + operation
                        + "|"
                        + organizationId
                        + "|"
                        + eventId
                        + "|"
                        + zoneId
                        + "|"
                        + json());
    }

    public static String sha256(String data) {
        return sha256(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static EventCommand input(
            String operation,
            UUID org,
            UUID event,
            UUID zone,
            Map<String, String> raw,
            Clock clock) {
        Map<String, String> f = new TreeMap<>(raw);
        Set<String> allowed =
                switch (operation) {
                    case "CREATE" -> DRAFT;
                    case "EDIT" -> {
                        var copy = new HashSet<>(DRAFT);
                        copy.add("expectedVersion");
                        yield copy;
                    }
                    case "ZONE_CREATE", "ZONE_EDIT" -> ZONE;
                    case "PUBLISH" -> Set.of("commissionRuleId", "adminNote", "expectedVersion");
                    case "REJECT" -> Set.of("reason", "expectedVersion");
                    case "SUBMIT", "DELETE", "ZONE_DELETE" -> Set.of("expectedVersion");
                    default -> throw EventProblem.invalid("Thao tác không hợp lệ.");
                };
        if (!allowed.containsAll(f.keySet()))
            throw EventProblem.invalid("Có trường không được phép thay đổi.");
        if (f.containsKey("expectedVersion")) {
            if (!f.get("expectedVersion").matches("[0-9]{1,18}"))
                throw EventProblem.invalid("Phiên bản sự kiện không hợp lệ.");
        }
        if (operation.equals("CREATE")) {
            if (!f.keySet().containsAll(DRAFT))
                throw EventProblem.invalid("Thiếu thông tin sự kiện.");
        }
        if (operation.equals("EDIT")
                && (f.isEmpty() || f.keySet().equals(Set.of("expectedVersion"))))
            throw EventProblem.invalid("Chưa có thông tin chỉnh sửa.");
        text(f, "title", 5, 200);
        text(f, "description", 1, 5000);
        text(f, "venueName", 1, 250);
        text(f, "venueAddress", 1, 500);
        if (f.containsKey("categoryId")) f.put("categoryId", uuid(f.get("categoryId")).toString());
        if (f.keySet().stream().anyMatch(SCHEDULE::contains)) {
            if (!f.keySet().containsAll(SCHEDULE))
                throw EventProblem.invalid("Cần gửi đầy đủ bốn thời điểm của lịch.");
            Instant saleStart = instant(f, "saleStartTime"),
                    saleEnd = instant(f, "saleEndTime"),
                    start = instant(f, "startTime"),
                    end = instant(f, "endTime");
            if (!saleStart.isBefore(saleEnd) || saleEnd.isAfter(start) || !start.isBefore(end))
                throw EventProblem.invalid(
                        "Lịch phải thỏa mở bán < kết thúc bán ≤ bắt đầu < kết thúc sự kiện.");
            if (operation.equals("CREATE") && !saleStart.isAfter(clock.instant()))
                throw EventProblem.invalid("Thời gian mở bán phải ở tương lai.");
        }
        if (operation.startsWith("ZONE_") && !operation.equals("ZONE_DELETE")) {
            money(f, "basePrice");
            text(f, "name", 1, 120);
            if (operation.equals("ZONE_CREATE")
                    && (!f.containsKey("name") || !f.containsKey("zoneType")))
                throw EventProblem.invalid("Thiếu tên hoặc loại khu vực.");
            String type = f.get("zoneType");
            if (type != null && !Set.of("SEATED", "STANDING").contains(type))
                throw EventProblem.invalid("Loại khu vực không hợp lệ.");
            if ("STANDING".equals(type)) {
                if (f.containsKey("rows") || f.containsKey("seatsPerRow"))
                    throw EventProblem.invalid("Khu đứng không có ghế.");
                if (operation.equals("ZONE_CREATE") && !f.containsKey("capacity"))
                    throw EventProblem.invalid("Cần sức chứa khu đứng.");
            }
            if ("SEATED".equals(type) && f.containsKey("capacity"))
                throw EventProblem.invalid("Khu ngồi dùng ma trận ghế.");
            if (operation.equals("ZONE_CREATE")
                    && "SEATED".equals(type)
                    && (!f.containsKey("rows") || !f.containsKey("seatsPerRow")))
                throw EventProblem.invalid("Cần số hàng và số ghế mỗi hàng.");
            if (f.containsKey("rows") || f.containsKey("seatsPerRow")) {
                int rows = positive(f, "rows", 1000), per = positive(f, "seatsPerRow", 1000);
                if ((long) rows * per > 10000)
                    throw EventProblem.invalid("Mỗi khu tối đa 10.000 ghế.");
            }
            if (f.containsKey("capacity")) positive(f, "capacity", 10000000);
        }
        if (operation.equals("PUBLISH")) {
            if (!f.containsKey("commissionRuleId"))
                throw EventProblem.invalid("Cần chính sách hoa hồng.");
            f.put("commissionRuleId", uuid(f.get("commissionRuleId")).toString());
            text(f, "adminNote", 0, 1000);
        }
        if (operation.equals("REJECT")) {
            if (!f.containsKey("reason")) throw EventProblem.invalid("Cần lý do từ chối.");
            text(f, "reason", 10, 500);
        }
        return new EventCommand(operation, org, event, zone, f);
    }

    public static UUID uuid(String raw) {
        try {
            return EventFilter.id(raw);
        } catch (IllegalArgumentException e) {
            throw EventProblem.invalid("ID phải là UUID đầy đủ.");
        }
    }

    public static String rowLabel(int row) {
        if (row < 1 || row > 1000) throw EventProblem.invalid("Số hàng không hợp lệ.");
        StringBuilder result = new StringBuilder();
        for (int n = row; n > 0; n = (n - 1) / 26) result.append((char) ('A' + (n - 1) % 26));
        return result.reverse().toString();
    }

    private static void text(Map<String, String> f, String key, int min, int max) {
        if (!f.containsKey(key)) return;
        String value = f.get(key).strip();
        if (value.length() < min || value.length() > max || value.indexOf('\0') >= 0)
            throw EventProblem.invalid("Độ dài trường " + key + " không hợp lệ.");
        f.put(key, value);
    }

    private static int positive(Map<String, String> f, String key, int max) {
        String raw = f.get(key);
        if (raw == null || !raw.matches("[0-9]{1,10}"))
            throw EventProblem.invalid("Số lượng không hợp lệ.");
        try {
            int value = Integer.parseInt(raw);
            if (value < 1 || value > max) throw EventProblem.invalid("Số lượng ngoài giới hạn.");
            f.put(key, Integer.toString(value));
            return value;
        } catch (NumberFormatException e) {
            throw EventProblem.invalid("Số lượng ngoài giới hạn.");
        }
    }

    private static void money(Map<String, String> f, String key) {
        String raw = f.get(key);
        if (raw == null || !raw.matches("[0-9]{1,19}"))
            throw EventProblem.invalid("Giá VND phải là số nguyên không âm.");
        f.put(key, new BigDecimal(raw).toBigIntegerExact().toString());
    }

    private static Instant instant(Map<String, String> f, String key) {
        try {
            Instant value = Instant.parse(f.get(key));
            int year = value.atOffset(ZoneOffset.UTC).getYear();
            if (year < 1 || year > 9999) throw new DateTimeException("range");
            f.put(key, value.toString());
            return value;
        } catch (DateTimeException e) {
            throw EventProblem.invalid("Thời gian phải là ISO-8601 UTC hợp lệ.");
        }
    }
}
