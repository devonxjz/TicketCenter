package vn.ticketscenter.event.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;

/** The shared organization wildcard resolves to Report; only Event actions consume multipart. */
@WebListener
public final class EventMultipartRouteInitializer implements ServletContextListener {
    @Override public void contextInitialized(ServletContextEvent event) {
        var registration=event.getServletContext().getServletRegistration("FinanceReportServlet");
        if(registration instanceof ServletRegistration.Dynamic dynamic)
            dynamic.setMultipartConfig(new MultipartConfigElement("",5_242_880,5_400_000,0));
    }
}
