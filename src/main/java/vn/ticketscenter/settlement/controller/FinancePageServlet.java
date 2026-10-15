package vn.ticketscenter.settlement.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.settlement.infrastructure.*;
import vn.ticketscenter.settlement.repository.FinanceRepository;

@WebServlet(name="FinancePageServlet",urlPatterns={"/finance/commission","/finance/settlements"})
public final class FinancePageServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws ServletException,IOException {
        var actor=SecurityContext.getCurrentUser(request).orElse(null);
        if(actor==null) { response.sendRedirect(request.getContextPath()+"/auth/login"); return; }
        try {
            var repository=new FinanceRepository(FinanceRuntime.database(getServletContext()));
            var options=repository.options(actor,request.getServletPath().endsWith("settlements"));
            request.setAttribute("organizations",options.get("organizations")); request.setAttribute("events",options.get("events"));
            if(request.getServletPath().endsWith("settlements")) request.setAttribute("readyEvents",repository.readySettlementEvents(actor));
            request.setAttribute("csrf",SecurityContext.getOrCreateCsrfToken(request));
            request.setAttribute("payoutMode",AppConfig.get("payout.adapter","unavailable"));
            response.setHeader("Cache-Control","no-store");
            String view=request.getServletPath().endsWith("commission")?"/WEB-INF/views/admin/commission/rules.jsp":"/WEB-INF/views/settlement/workbench.jsp";
            request.getRequestDispatcher(view).forward(request,response);
        } catch(FinanceException failure) { HttpResponses.error(response,failure.status(),failure.code(),"The finance page could not be loaded"); }
    }
}
