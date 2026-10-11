package vn.ticketscenter.fulfillment.checkin.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.*;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanRequestDto;
import vn.ticketscenter.fulfillment.checkin.service.TicketCheckInService;
import vn.ticketscenter.identity.security.SecurityContext;
import java.io.*;
import java.util.*;
@WebServlet(name="TicketCheckInServlet",urlPatterns="/api/check-ins")
public class TicketCheckInServlet extends HttpServlet {
    private final TicketCheckInService injected;
    public TicketCheckInServlet(){injected=null;}
    public TicketCheckInServlet(TicketCheckInService service){injected=service;}
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws IOException {
        try{
            UUID actor=CheckInWeb.actor(request);
            if(!SecurityContext.validateCsrfToken(request))throw new CheckInException(403,"CSRF_TOKEN_INVALID","Mã CSRF không hợp lệ.");
            String type=request.getContentType();if(type==null || !type.split(";")[0].trim().equalsIgnoreCase("application/json"))
                throw new CheckInException(415,"JSON_REQUIRED","Yêu cầu phải dùng application/json.");
            UUID key=CheckInWeb.id(request.getHeader("Idempotency-Key"));
            if(request.getContentLengthLong()>8192)throw new CheckInException(413,"REQUEST_TOO_LARGE","Nội dung soát vé quá dài.");
            char[] chars=new char[8193];int count=0,n;Reader reader=request.getReader();
            while(count<chars.length && (n=reader.read(chars,count,chars.length-count))!=-1)count+=n;
            if(count>8192)throw new CheckInException(413,"REQUEST_TOO_LARGE","Nội dung soát vé quá dài.");
            Map<String,String> body;
            try{body=JsonObjectParser.parse(new StringReader(new String(chars,0,count)));}
            catch(IllegalArgumentException e){throw new CheckInException(400,"INVALID_JSON","Nội dung JSON không hợp lệ.");}
            if(!body.keySet().equals(Set.of("eventId","qrContent")) && !body.keySet().equals(Set.of("eventId","ticketCode")))
                throw new CheckInException(400,"INVALID_SCAN_REQUEST","Nội dung soát vé không đúng định dạng.");
            var dto=new CheckInScanRequestDto(CheckInWeb.id(body.get("eventId")),body.get("qrContent"),body.get("ticketCode"));
            var service=injected==null?CheckInRuntime.resolveScanner(request.getServletContext()):injected;
            var result=service.scan(actor,key,dto);
            response.setStatus(result.httpStatus());response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control","no-store");response.getWriter().write("{\"data\":"+result.toJson()+"}");
        }catch(RuntimeException e){CheckInWeb.error(response,e);}
    }
}
