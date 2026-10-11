package vn.ticketscenter.report.controller;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.report.service.*;
import vn.ticketscenter.dashboard.service.DashboardService;
import vn.ticketscenter.settlement.infrastructure.*;

@WebServlet(name="FinanceReportServlet",urlPatterns={"/api/organizations/*","/api/admin/reports","/api/reports/export","/api/reports/options"})
public final class ReportServlet extends HttpServlet {
    private ReportService reports;
    private DashboardService overview;
    @Override public void init(){var database=FinanceRuntime.database(getServletContext());reports=new ReportService(database,AppConfig.getBoolean("finance.redis.enabled",true)?ReportCache.redis():ReportCache.none());overview=new DashboardService(database);}
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException{
        try{
            var actor=ReadsideHttp.actor(request);var params=ReadsideHttp.parameters(request);String path=request.getServletPath();
            if(path.equals("/api/reports/options")){ReadsideHttp.json(response,reports.options(actor,ReportFilter.uuid(params.get("organizationId"))));return;}
            UUID org=null;
            if(path.equals("/api/organizations")){
                var route=ReadsideHttp.organizationRoute(request.getPathInfo());org=(UUID)route[0];
                if(route[1].equals("overview")){
                    if(!params.isEmpty())throw new FinanceException("INVALID_FILTER",400);
                    try{ReadsideHttp.json(response,overview.organization(actor,org));}
                    catch(SQLException e){throw new FinanceException("OVERVIEW_UNAVAILABLE",503);}
                    return;
                }
            }
            var filter=ReportFilter.parse(params,org);
            if(path.equals("/api/reports/export")){response.setContentType("text/csv;charset=UTF-8");response.setHeader("Content-Disposition","attachment; filename=\"ticketscenter-"+filter.metric().name().toLowerCase(Locale.ROOT)+".csv\"");response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");reports.export(actor,filter,response.getOutputStream());}
            else ReadsideHttp.json(response,path.equals("/api/admin/reports")?reports.jsonAdmin(actor,filter):reports.json(actor,filter));
        }catch(FinanceException e){ReadsideHttp.error(response,e);}catch(SQLException e){ReadsideHttp.error(response,new FinanceException("REPORT_UNAVAILABLE",503));}
    }
}
