package vn.ticketscenter.settlement.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.settlement.infrastructure.*;
import vn.ticketscenter.settlement.payout.*;
import vn.ticketscenter.settlement.repository.FinanceRepository;
import vn.ticketscenter.settlement.service.FinanceMutationService;
import vn.ticketscenter.settlement.commission.service.CommissionRuleCache;
import vn.ticketscenter.report.service.ReportCache;
import vn.ticketscenter.dashboard.service.DashboardCache;
import vn.ticketscenter.settlement.service.SettlementReadCache;

@WebServlet(name="AdminFinanceServlet",urlPatterns={"/api/admin/organizations/*","/api/admin/commission-rules/*","/api/admin/commission-rules","/api/admin/events/*","/api/admin/settlements/*"})
public final class AdminFinanceServlet extends HttpServlet {
    private volatile FinanceMutationService service;
    public AdminFinanceServlet() {}
    public AdminFinanceServlet(FinanceMutationService service) { this.service=service; }
    private FinanceMutationService finance() {
        if(service==null) synchronized(this) {
            if(service==null) {
                var database=FinanceRuntime.database(getServletContext());
                PayoutAdapter adapter=PayoutAdapter.unavailable();
                if("fixture-simulated".equals(AppConfig.get("payout.adapter","unavailable")))
                    adapter=new SimulatedPayoutAdapter(database,AppConfig.get("payout.simulated.outcome","SUCCEEDED"),AppConfig.getBoolean("payout.simulated.timeoutAfterSideEffect",false));
                boolean caching=AppConfig.getBoolean("finance.redis.enabled",true);
                var cache=caching?CommissionRuleCache.redis():CommissionRuleCache.none();
                var settlementCache=caching?SettlementReadCache.redis():SettlementReadCache.none();
                var repository=new FinanceRepository(database,org -> {
                    if(caching) { cache.invalidate(org); ReportCache.invalidateAfterCommit(org); DashboardCache.invalidateAdminAfterCommit(); }
                },settlementCache::invalidate);
                service=new FinanceMutationService(repository,adapter,cache,settlementCache);
            }
        }
        return service;
    }
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws IOException { handle(request,response,false); }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response) throws IOException { handle(request,response,true); }
    private void handle(HttpServletRequest request,HttpServletResponse response,boolean mutation) throws IOException {
        try {
            UserPrincipal actor=SecurityContext.getCurrentUser(request).orElseThrow(()->new FinanceException("UNAUTHENTICATED",401));
            if(mutation && !SecurityContext.validateCsrfToken(request)) throw new FinanceException("CSRF_INVALID",403);
            String path=request.getRequestURI().substring(request.getContextPath().length());
            String[] p=path.substring(1).split("/",-1);
            boolean rules=p.length==5 && p[2].equals("organizations") && p[4].equals("commission-rules");
            boolean edit=p.length==5 && p[2].equals("commission-rules") && p[4].equals("edit");
            boolean event=p.length>=5 && p[2].equals("events") && (p[4].equals("settlement") || p[4].equals("settlement-blockers"));
            boolean settlement=p.length==5 && p[2].equals("settlements") && (p[4].equals("confirm") || p[4].equals("payouts"));
            boolean reconcile=p.length==7 && p[2].equals("settlements") && p[4].equals("payouts") && p[6].equals("reconcile");
            boolean globalRules=path.equals("/api/admin/commission-rules");
            if(!(rules || edit || event || settlement || reconcile || globalRules)) throw new FinanceException("NOT_FOUND",404);
            if(event && !(p.length==5 || (p.length==6 && p[4].equals("settlement") && p[5].equals("recalculate")))) throw new FinanceException("NOT_FOUND",404);
            UUID id=globalRules?null:FinanceRequest.id(p[3]); Object result;
            if(!mutation) {
                String kind=rules || globalRules?"rules":event && p.length==5?(p[4].equals("settlement")?"summary":"blockers"):settlement && p[4].equals("payouts")?"payouts":null;
                if(kind==null) throw new FinanceException("METHOD_NOT_ALLOWED",405);
                if(globalRules) id=FinanceRequest.id(request.getParameter("organizationId"));
                HttpResponses.data(response,finance().readJson(actor,kind,id)); return;
            } else {
                UUID key=FinanceRequest.id(request.getHeader("Idempotency-Key"));
                if(rules || edit || globalRules) {
                    var body=FinanceRequest.body(request.getReader()); UUID bodyOrg=body.containsKey("organizationId")?FinanceRequest.id(body.get("organizationId")):null;
                    if(rules && bodyOrg!=null && !id.equals(bodyOrg)) throw new FinanceException("COMMISSION_RULE_INVALID",400);
                    UUID org=rules?id:bodyOrg; if(globalRules && org==null) throw new FinanceException("COMMISSION_RULE_INVALID",400);
                    result=finance().commission(actor,org,edit?id:null,key,FinanceRequest.commission(body));
                } else if(event && p.length==6) result=finance().recalculate(actor,id);
                else if(settlement && p[4].equals("confirm")) result=finance().confirm(actor,id);
                else if(settlement && p[4].equals("payouts")) result=finance().payout(actor,id,key,FinanceRequest.payout(FinanceRequest.body(request.getReader())));
                else if(reconcile) {
                    UUID payout=FinanceRequest.id(p[5]); if(!payout.equals(key)) throw new FinanceException("REQUEST_INVALID",400);
                    var body=FinanceRequest.body(request.getReader());
                    result=finance().reconcile(actor,id,payout,body.isEmpty()?null:FinanceRequest.payout(body));
                }
                else throw new FinanceException("METHOD_NOT_ALLOWED",405);
            }
            HttpResponses.data(response,FinanceJson.write(result));
            if(mutation && (rules || globalRules) && result instanceof Map<?,?> data && !Boolean.TRUE.equals(data.get("isReplay"))) response.setStatus(201);
        } catch(FinanceException failure) { HttpResponses.error(response,failure.status(),failure.code(),"The finance request could not be completed"); }
        catch(java.sql.SQLException failure) { HttpResponses.error(response,503,"DATABASE_UNAVAILABLE","The finance service is temporarily unavailable"); }
        catch(IllegalArgumentException failure) { HttpResponses.error(response,400,"REQUEST_INVALID","Invalid finance request"); }
    }
}
