package vn.ticketscenter.fulfillment.checkin.dto;
import java.time.Instant;
import java.util.UUID;
import static vn.ticketscenter.config.web.HttpResponses.jsonString;
public record RecentCheckInLogDto(UUID id,UUID ticketId,String result,Instant scannedAt,String staffName,String zoneName,String seatLabel) {
    public String toJson(){return "{\"id\":"+jsonString(id.toString())+",\"ticketId\":"+jsonString(ticketId==null?null:ticketId.toString())
        +",\"result\":"+jsonString(result)+",\"scannedAt\":"+jsonString(scannedAt.toString())+",\"staffName\":"+jsonString(staffName)
        +",\"zoneName\":"+jsonString(zoneName)+",\"seatLabel\":"+jsonString(seatLabel)+"}";}
}
