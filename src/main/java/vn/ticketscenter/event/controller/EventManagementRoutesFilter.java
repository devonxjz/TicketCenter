package vn.ticketscenter.event.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;

/** Dispatch only Event-owned actions; preserve the existing catalog and other domains. */
@WebFilter(urlPatterns={"/api/events/*","/api/zones/*","/api/organizations/*","/api/admin/events/*"})
public final class EventManagementRoutesFilter implements Filter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain) throws IOException,ServletException {
        var req=(HttpServletRequest)request;String path=req.getRequestURI().substring(req.getContextPath().length());String[] p=path.split("/",-1);
        boolean event=p.length==5 && p[2].equals("events") &&
                ((req.getMethod().equals("POST") && Set.of("edit","delete","submit","zones","cover").contains(p[4]))
                || (req.getMethod().equals("GET") && Set.of("layout","image").contains(p[4])));
        boolean zone=p.length==5 && p[2].equals("zones") && req.getMethod().equals("POST") && Set.of("edit","delete").contains(p[4]);
        boolean organization=(p.length==5 || p.length==6) && p[2].equals("organizations") && p[4].equals("events") && Set.of("GET","POST").contains(req.getMethod());
        boolean admin=p.length>=5 && p[2].equals("admin") && p[3].equals("events") &&
            ((p.length==5 && req.getMethod().equals("GET")) || (p.length==6 &&
            ((req.getMethod().equals("GET") && Set.of("layout","commission-rules").contains(p[5])) ||
            (req.getMethod().equals("POST") && Set.of("publish","reject").contains(p[5])))));
        if(event || zone || organization || admin) EventManagementWeb.handle(req,(HttpServletResponse)response);else chain.doFilter(request,response);
    }
}
