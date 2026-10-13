package vn.ticketscenter.event.repository;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.dto.EventFilter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventCatalogRepositoryTest {
    private final UUID id = UUID.randomUUID();
    private final List<String> sql = new ArrayList<>();
    private final List<Map<Integer, Object>> binds = new ArrayList<>();
    private List<Object[]> projection = List.of();
    private int timeType = Types.TIMESTAMP;
    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (!method.getName().equals("prepareStatement")) return null;
            String query = (String) args[0]; sql.add(query);
            Map<Integer, Object> parameters = new HashMap<>(); binds.add(parameters);
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> switch (m.getName()) {
                case "setString", "setObject", "setInt" -> { parameters.put((int) a[0], a[1]); yield null; }
                case "executeQuery" -> rows(query.contains("COUNT_BIG") ? Collections.singletonList(new Object[]{1L}) : projection);
                default -> null;
            });
        });
    }
    private ResultSet rows(List<Object[]> values) {
        int[] index = {-1};
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, (p, m, a) -> switch (m.getName()) {
            case "next" -> ++index[0] < values.size();
            case "getString" -> { Object value = values.get(index[0])[(int) a[0] - 1]; yield value == null ? null : value.toString(); }
            case "getLong" -> ((Number) values.get(index[0])[(int) a[0] - 1]).longValue();
            case "getInt" -> ((Number) values.get(index[0])[(int) a[0] - 1]).intValue();
            case "getBoolean" -> values.get(index[0])[(int) a[0] - 1];
            case "getBigDecimal", "getObject" -> values.get(index[0])[(int) a[0] - 1];
            case "getMetaData" -> Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSetMetaData.class},
                    (mp, mm, ma) -> mm.getName().equals("getColumnType") ? timeType : null);
            default -> null;
        });
    }
    private Object[] eventRow(Object time) {
        return new Object[]{id, id, "Nhạc", "Mô tả", null, "Nhà hát", "HCM", time, time, time, time,
                new BigDecimal("300000"), "Âm nhạc", "Ban tổ chức", new BigDecimal("500000"), true};
    }
    @Test void filtersAreBoundAndPaginationSortAreStable() {
        projection = Collections.singletonList(eventRow(LocalDateTime.of(2026, 10, 10, 10, 0)));
        var filter = EventFilter.parse(Map.of("keyword", "x' OR 1=1 --", "categoryId", id.toString(),
                "from", "2026-10-10T01:00:00Z", "to", "2026-10-11T01:00:00Z", "page", "2", "pageSize", "10", "sort", "price_desc"));
        var page = EventRepositoryFixture.repository(connection()).events(filter);
        assertEquals(1, page.total()); assertEquals(1, page.events().size());
        assertTrue(sql.stream().allMatch(q -> (q.contains("dbo.vw_PublicEvents") || q.contains("dbo.vw_PublicEventCatalog")) && !q.contains("x' OR")));
        assertTrue(sql.get(1).contains("ORDER BY minimum_price DESC, event_id ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertEquals("%x' OR 1=1 --%", binds.get(1).get(1)); assertEquals(id.toString(), binds.get(1).get(4));
        assertEquals(binds.get(1).get(1), binds.get(1).get(2)); assertEquals(binds.get(1).get(1), binds.get(1).get(3));
        assertTrue(sql.get(1).contains("OR e.venue_name LIKE ?"));
        assertEquals(LocalDateTime.of(2026, 10, 10, 1, 0), binds.get(1).get(5));
        assertEquals(10, binds.get(1).get(7)); assertEquals(10, binds.get(1).get(8));
        assertEquals(Instant.parse("2026-10-10T10:00:00Z"), page.events().getFirst().startTime());
    }
    @Test void mapsDatetimeOffsetWithoutLosingItsOffset() {
        timeType = -155;
        projection = Collections.singletonList(eventRow(OffsetDateTime.parse("2026-10-10T10:00:00+07:00")));
        assertEquals(Instant.parse("2026-10-10T03:00:00Z"), EventRepositoryFixture.repository(connection()).event(id).orElseThrow().startTime());
    }
    @Test void allChildReadsGuardVisibilityWithPublicViewAndBindIds() {
        var repository = EventRepositoryFixture.repository(connection());
        assertTrue(repository.event(id).isEmpty()); assertTrue(repository.zones(id).isEmpty()); assertTrue(repository.seats(id).isEmpty());
        assertEquals(3, sql.size());
        assertTrue(sql.stream().allMatch(q -> (q.contains("dbo.vw_PublicEvents") || q.contains("dbo.vw_PublicEventCatalog")) && !q.contains(id.toString())));
        assertTrue(binds.stream().allMatch(p -> p.get(1).equals(id.toString())));
    }
    @Test void leftJoinEmptyPublicParentMapsToEmptyCollection() {
        projection = Collections.singletonList(new Object[9]);
        var repository = EventRepositoryFixture.repository(connection());
        assertTrue(repository.zones(id).orElseThrow().isEmpty()); assertTrue(repository.seats(id).orElseThrow().isEmpty());
    }
}
