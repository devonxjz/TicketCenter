package vn.ticketscenter.event.service;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.dto.EventFilter;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EventFilterTest {
    @Test void normalizesEquivalentFiltersAndIncludesPagination() {
        var a = EventFilter.parse(Map.of("keyword", "  Nhạc  ", "page", "01"));
        var b = EventFilter.parse(Map.of("keyword", "Nhạc"));
        assertEquals(a.cacheKey(), b.cacheKey());
        assertNotEquals(a.cacheKey(), EventFilter.parse(Map.of("keyword", "Nhạc", "page", "2")).cacheKey());
        assertTrue(a.cacheKey().matches("tc:event:list:v1:[a-f0-9]{64}"));
        assertFalse(a.cacheKey().contains("Nhạc"));
    }
    @Test void rejectsInvalidSortPaginationIdsAndDates() {
        for (var input : java.util.List.of(Map.of("sort", "start_time; DROP TABLE x"),
                Map.of("page", "0"), Map.of("pageSize", "51"), Map.of("keyword", "x".repeat(101)), Map.of("page", "2147483647"),
                Map.of("categoryId", "1-1-1-1-1"), Map.of("from", "2026-10-10"), Map.of("from", "+10000-01-01T00:00:00Z"),
                Map.of("from", "2026-10-12T00:00:00Z", "to", "2026-10-11T00:00:00Z"))) {
            assertThrows(IllegalArgumentException.class, () -> EventFilter.parse(input));
        }
    }
    @Test void issueDefaultsAndAllSortValuesAreSupported() {
        var defaults = EventFilter.parse(Map.of());
        assertEquals(12, defaults.pageSize()); assertEquals("startTime_asc", defaults.sort());
        for (String sort : java.util.List.of("startTime_asc", "startTime_desc", "price_asc", "price_desc")) {
            assertTrue(EventFilter.parse(Map.of("sort", sort)).orderBy().endsWith("event_id ASC"));
        }
        assertEquals(50, EventFilter.parse(Map.of("pageSize", "50")).pageSize());
    }
    @Test void keepsLiteralSearchCharactersAndUsesStableAllowlistedOrder() {
        var filter = EventFilter.parse(Map.of("keyword", "%_[x]\\", "sort", "price_desc"));
        assertEquals("%\\%\\_\\[x]\\\\%", filter.keywordPattern());
        assertEquals("minimum_price DESC, event_id ASC", filter.orderBy());
    }
}
