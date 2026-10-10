package vn.ticketscenter.audit.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.audit.service.AuditService;
import vn.ticketscenter.report.controller.ReadsideHttp;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.settlement.infrastructure.*;
@WebServlet(name="FinanceAuditServlet",urlPatterns={"/api/admin/audit-logs","/api/admin/audit-logs/*"})
public final class AuditServlet extends HttpServlet{
    private AuditService service;@Override public void init(){service=new AuditService(FinanceRuntime.database(getServletContext()));}
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException{try{var actor=ReadsideHttp.actor(request);String path=request.getPathInfo();if(path==null || path.equals("/")){ReadsideHttp.json(response,service.list(actor,AuditFilter.parse(ReadsideHttp.parameters(request))));}else{if(!path.matches("/[0-9a-fA-F-]{36}"))throw new FinanceException("NOT_FOUND",404);ReadsideHttp.json(response,service.detail(actor,ReportFilter.uuid(path.substring(1))));}}catch(FinanceException e){ReadsideHttp.error(response,e);}catch(SQLException e){ReadsideHttp.error(response,new FinanceException("AUDIT_UNAVAILABLE",503));}}
}
