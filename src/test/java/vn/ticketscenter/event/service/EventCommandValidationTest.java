package vn.ticketscenter.event.service;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.dto.*;
import java.io.StringReader;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventCommandValidationTest {
    private final UUID id=UUID.randomUUID();
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"),ZoneOffset.UTC);
    private Map<String,String> draft() { return new HashMap<>(Map.of("title","Concert test","description","Description","categoryId",id.toString(),
        "venueName","Venue","venueAddress","Address","saleStartTime","2026-11-01T00:00:00Z","saleEndTime","2026-11-02T00:00:00Z","startTime","2026-11-03T00:00:00Z","endTime","2026-11-04T00:00:00Z")); }
    @Test void draftChronologyAndOwnerFieldsAreValidated() {
        assertEquals("CREATE",EventCommand.input("CREATE",id,null,null,draft(),clock).operation());
        var fields=draft();fields.put("endTime","2026-11-02T00:00:00Z");assertThrows(EventProblem.class,()->EventCommand.input("CREATE",id,null,null,fields,clock));
        var invalid=draft();invalid.put("organizationId",id.toString());
        assertThrows(EventProblem.class,()->EventCommand.input("CREATE",id,null,null,invalid,clock));
    }
    @Test void futureOpeningAndScheduleCompletenessAreRequired() {
        var fields=draft();fields.put("saleStartTime","2026-10-01T00:00:00Z");
        assertThrows(EventProblem.class,()->EventCommand.input("CREATE",id,null,null,fields,clock));
        assertThrows(EventProblem.class,()->EventCommand.input("EDIT",null,id,null,Map.of("startTime","2026-11-01T00:00:00Z"),clock));
    }
    @Test void canonicalHashIgnoresPropertyOrderAndChangesWithScopePayloadOrResource() {
        var a=EventCommand.input("CREATE",id,null,null,draft(),clock);var ordered=new LinkedHashMap<String,String>();
        draft().entrySet().stream().sorted(Map.Entry.<String,String>comparingByKey().reversed()).forEach(e->ordered.put(e.getKey(),e.getValue()));
        assertEquals(a.hash(),EventCommand.input("CREATE",id,null,null,ordered,clock).hash());
        ordered.put("title","Different title");assertNotEquals(a.hash(),EventCommand.input("CREATE",id,null,null,ordered,clock).hash());
        assertNotEquals(a.hash(),EventCommand.input("CREATE",UUID.randomUUID(),null,null,draft(),clock).hash());
    }
    @Test void zoneMatrixPriceAndFieldMismatchAreRejected() {
        var valid=Map.of("name","VIP","zoneType","SEATED","basePrice","0","rows","27","seatsPerRow","2");
        assertEquals("AA",EventCommand.rowLabel(27));assertEquals("Z",EventCommand.rowLabel(26));assertEquals("ALL",EventCommand.rowLabel(1000));
        assertNotNull(EventCommand.input("ZONE_CREATE",null,id,null,valid,clock));
        for(var fields:List.of(Map.of("name","VIP","zoneType","STANDING","basePrice","10","rows","1","seatsPerRow","2"),
            Map.of("name","VIP","zoneType","SEATED","basePrice","1.5","rows","1","seatsPerRow","2"),
            Map.of("name","VIP","zoneType","STANDING","basePrice","10","capacity","-1"),
            Map.of("name","VIP","zoneType","SEATED","basePrice","10","rows","1000","seatsPerRow","1000"))) {
            assertThrows(EventProblem.class,()->EventCommand.input("ZONE_CREATE",null,id,null,fields,clock));
        }
    }
    @Test void flatParserSupportsNumbersEscapesAndRejectsDuplicateNestedAndLargeBodies() throws Exception {
        var parsed=EventRequestParser.parse(new StringReader("{\"rows\":2,\"name\":\"A\\nB\\u0041\"}"));
        assertEquals("2",parsed.get("rows"));assertEquals("A\nBA",parsed.get("name"));
        for(String body:List.of("{\"rows\":1,\"rows\":2}","{\"nested\":{}}","{\"rows\":01}","{\"rows\":true}"))
            assertThrows(EventProblem.class,()->EventRequestParser.parse(new StringReader(body)));
        assertEquals(413,assertThrows(EventProblem.class,()->EventRequestParser.parse(new StringReader("x".repeat(65537)))).status());
        assertTrue(EventRequestParser.parse(new StringReader("")).isEmpty());
    }
    @Test void rejectionAndIdempotencyHeaderLimitsAreValidated() {
        assertThrows(EventProblem.class,()->EventCommand.input("REJECT",null,id,null,Map.of("reason","short"),clock));
        assertDoesNotThrow(()->EventCommand.input("REJECT",null,id,null,Map.of("reason","Please provide the event permit"),clock));
        assertThrows(EventProblem.class,()->EventManagementService.validateKey(null));assertThrows(EventProblem.class,()->EventManagementService.validateKey("bad*key"));
    }
}
