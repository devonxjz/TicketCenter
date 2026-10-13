package vn.ticketscenter.fulfillment.ticket.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketPageServletTest {
    @Test void anonymousTicketPageReturnsToItsContextAndQueryAfterLogin() throws Exception {
        String[] redirect = new String[1];
        var request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequestURI" -> "/ticketscenter/me/tickets";
                    case "getContextPath" -> "/ticketscenter";
                    case "getQueryString" -> "page=2&status=ACTIVE";
                    default -> null;
                });
        var response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletResponse.class},
                (proxy, method, args) -> { if (method.getName().equals("sendRedirect")) redirect[0] = (String) args[0]; return null; });
        new TicketPageServlet().doGet(request,response);
        assertEquals("/ticketscenter/auth/login?redirect=%2Fticketscenter%2Fme%2Ftickets%3Fpage%3D2%26status%3DACTIVE",redirect[0]);
    }
}
