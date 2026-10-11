package vn.ticketscenter.fulfillment.checkin.dto;
import java.time.Instant;
import java.util.UUID;
import static vn.ticketscenter.config.web.HttpResponses.jsonString;
public record CheckInScanResponseDto(String status,UUID ticketId,String ticketCode,String customerName,
        String zoneName,String seatLabel,Instant checkedInAt,Instant scannedAt,UUID organizationId) {
    public boolean success(){return status.equals("CHECKED_IN_SUCCESS");}
    public int httpStatus(){return success()?200:status.equals("ALREADY_CHECKED_IN")?409:400;}
    public String message(){return switch(status){
        case "CHECKED_IN_SUCCESS"->"Soát vé thành công. Chào mừng khách hàng!";
        case "ALREADY_CHECKED_IN"->"Vé này đã được điểm danh vào cửa trước đó.";
        case "INVALID_SIGNATURE"->"Mã QR không hợp lệ hoặc đã bị chỉnh sửa.";
        case "WRONG_EVENT"->"Vé không thuộc sự kiện đang soát vé.";
        case "TICKET_REFUND_PENDING"->"Vé đang chờ hoàn tiền, không thể vào cửa.";
        case "TICKET_REFUNDED"->"Vé đã được hoàn tiền, không thể vào cửa.";
        case "EVENT_CANCELLED"->"Sự kiện đã bị hủy.";
        case "OUTSIDE_WINDOW"->"Hiện tại nằm ngoài khung giờ soát vé.";
        case "TICKET_NOT_FOUND"->"Không tìm thấy vé hợp lệ.";
        default->"Vé không còn hiệu lực để vào cửa.";};}
    public String toJson(){return "{\"success\":"+success()+",\"status\":"+jsonString(status)
        +",\"ticketId\":"+jsonString(ticketId==null?null:ticketId.toString())+",\"ticketCode\":"+jsonString(ticketCode)
        +",\"customerName\":"+jsonString(customerName)+",\"zoneName\":"+jsonString(zoneName)
        +",\"seatLabel\":"+jsonString(seatLabel)+",\"checkedInAt\":"+jsonString(checkedInAt==null?null:checkedInAt.toString())
        +",\"scannedAt\":"+jsonString(scannedAt.toString())+",\"message\":"+jsonString(message())+"}";}
    @Override public String toString(){return "CheckInScanResponseDto[status="+status+",ticketId="+ticketId+"]";}
}
