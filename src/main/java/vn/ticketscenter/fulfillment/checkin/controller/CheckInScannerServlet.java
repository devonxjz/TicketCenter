package vn.ticketscenter.fulfillment.checkin.controller;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
@WebServlet(name="CheckInScannerServlet",urlPatterns="/check-ins/scanner")
public class CheckInScannerServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException,ServletException {
        try{
            var actor=CheckInWeb.actor(request);var event=CheckInWeb.id(request.getParameter("eventId"));
            CheckInRuntime.resolve(request.getServletContext()).window(actor,event);
            response.setHeader("Cache-Control","no-store");
            request.getRequestDispatcher("/WEB-INF/views/checkin/scanner.jsp").forward(request,response);
        }catch(RuntimeException e){CheckInWeb.error(response,e);}
    }
}
