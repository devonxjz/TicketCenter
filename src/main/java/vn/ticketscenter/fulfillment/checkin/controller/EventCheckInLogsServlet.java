package vn.ticketscenter.fulfillment.checkin.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.service.TicketCheckInService;
import java.io.IOException;
import java.util.stream.Collectors;
@WebServlet(name="EventCheckInLogsServlet",urlPatterns="/api/check-ins/recent-internal")
public class EventCheckInLogsServlet extends HttpServlet {
    public static final String EVENT_ATTRIBUTE=EventCheckInLogsServlet.class.getName()+".event";
    private final TicketCheckInService injected;
    public EventCheckInLogsServlet(){injected=null;}
    public EventCheckInLogsServlet(TicketCheckInService service){injected=service;}
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        try{
            var actor=CheckInWeb.actor(request);var event=CheckInWeb.id((String)request.getAttribute(EVENT_ATTRIBUTE));
            int limit=20;String size=request.getParameter("pageSize");
            if(size!=null){try{limit=Integer.parseInt(size);}catch(NumberFormatException e){throw new CheckInException(400,"INVALID_PAGE_SIZE","Số dòng nhật ký không hợp lệ.");}}
            var service=injected==null?CheckInRuntime.resolveScanner(request.getServletContext()):injected;
            HttpResponses.data(response,service.recent(actor,event,limit).stream().map(v->v.toJson()).collect(Collectors.joining(",","[","]")));
        }catch(RuntimeException e){CheckInWeb.error(response,e);}
    }
}
