package vn.ticketscenter.swagger;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.dto.*;
import static org.junit.jupiter.api.Assertions.*;

class EventCatalogOpenApiContractTest {
    private JSONObject spec()throws Exception {
        try(var source=getClass().getResourceAsStream("/openapi.json")) {
            assertNotNull(source);return new JSONObject(new String(source.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    @Test void publicOperationsDescribeTheDeliveredTypedPagingAndErrorContracts()throws Exception {
        var paths=spec().getJSONObject("paths");
        for(String path:List.of("/api/events","/api/events/{eventId}","/api/events/{eventId}/zones","/api/zones/{zoneId}/seats","/api/event-categories")) {
            var operation=paths.getJSONObject(path).getJSONObject("get");
            assertEquals("implemented",operation.getString("x-implementation-status"));
            assertEquals("dong",operation.getString("x-owner"));assertTrue(operation.getJSONArray("security").isEmpty());
            var responses=operation.getJSONObject("responses");assertTrue(responses.has("400")&&responses.has("503"));
            assertTrue(responses.getJSONObject("200").getJSONObject("content").getJSONObject("application/json").getJSONObject("schema").has("$ref"));
        }
        Set<String> queryNames=new HashSet<>();
        for(Object parameter:paths.getJSONObject("/api/events").getJSONObject("get").getJSONArray("parameters")) {
            var item=(JSONObject)parameter;if(item.getString("in").equals("query"))queryNames.add(item.getString("name"));
        }
        assertEquals(Set.of("keyword","categoryId","from","to","sort","page","pageSize"),queryNames);
    }
    @Test void actualPublicEventAndPageMatchSchemaIncludingAliasesExactMoneyAndUtc()throws Exception {
        UUID id=UUID.randomUUID();Instant now=Instant.parse("2026-10-12T00:00:00Z");
        var event=new EventDtos.Event(id,id,"Sự kiện","Description",null,"Venue","Address",now,now.plusSeconds(60),now.plusSeconds(120),now.plusSeconds(180),new BigDecimal("9007199254740993"),"Category","Organization",new BigDecimal("9007199254740994"),true);
        var json=new JSONObject(event.toJson());var schemas=spec().getJSONObject("components").getJSONObject("schemas");
        assertEquals(schemas.getJSONObject("PublicEvent").getJSONObject("properties").keySet(),json.keySet());
        assertEquals("9007199254740993",json.get("minPrice"));assertEquals(now.toString(),json.getString("saleStartTime"));
        assertEquals("string",schemas.getJSONObject("PublicEvent").getJSONObject("properties").getJSONObject("minPrice").getString("type"));
        var page=new JSONObject(new EventDtos.Page(List.of(event),1).toJson(EventFilter.parse(Map.of())));
        assertEquals(schemas.getJSONObject("PublicEventPage").getJSONObject("properties").keySet(),page.keySet());
    }
    @Test void actualZoneSeatAndCategoryMatchThePublicSchemas()throws Exception {
        UUID id=UUID.randomUUID();var schemas=spec().getJSONObject("components").getJSONObject("schemas");
        var values=Map.of("PublicEventZone",new EventDtos.Zone(id,id,"Zone","SEATED",new BigDecimal("1000"),10,1,2,7).toJson(),
                "PublicEventSeat",new EventDtos.Seat(id,id,"A",1,"A1","AVAILABLE").toJson(),
                "PublicEventCategory",new EventDtos.Category(id,"Category","category").toJson());
        for(var entry:values.entrySet())assertEquals(schemas.getJSONObject(entry.getKey()).getJSONObject("properties").keySet(),new JSONObject(entry.getValue()).keySet());
    }
}
