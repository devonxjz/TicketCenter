package vn.ticketscenter.settlement.http;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Map;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.settlement.controller.FinanceWeb;
import vn.ticketscenter.settlement.dto.FinanceJson;

/** Test class only, explicitly deployed by the isolated loopback HTTP smoke. */
public final class FixtureSessionServlet extends HttpServlet {
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response) throws IOException {
        String expected=System.getenv("TC_FINANCE_HTTP_FIXTURE_TOKEN"),provided=request.getHeader("X-Fixture-Token");
        if(expected==null || expected.length()<32 || provided==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8)) || !"127.0.0.1".equals(request.getRemoteAddr())) {
            HttpResponses.error(response,403,"FIXTURE_FORBIDDEN","Fixture session is unavailable"); return;
        }
        try(var c=DriverManager.getConnection(DatabaseConfig.getJdbcUrl(),DatabaseConfig.getUser(),DatabaseConfig.getPassword())) {
            if(!c.getCatalog().matches("TC_Vuong_Test_[A-Za-z0-9_]+")) throw new SQLException();
            try(var s=c.prepareStatement("SELECT fixture_key FROM dbo.tc_vuong_test_fixture WHERE fixture_key='vuong-finance-v1'");var rows=s.executeQuery()) { if(!rows.next()) throw new SQLException(); }
            var actor=FinanceWeb.id(FinanceWeb.body(request.getReader()).get("actorId"));
            try(var query=c.prepareStatement("SELECT platform_role,auth_version FROM dbo.tc_users WHERE id=? AND status='ACTIVE'")) {
                query.setString(1,actor.toString()); query.setQueryTimeout(5);
                try(var row=query.executeQuery()) {
                    if(!row.next()) { HttpResponses.error(response,401,"FIXTURE_ACTOR_MISSING","Fixture actor is unavailable"); return; }
                    SecurityContext.setSessionUser(request,new UserPrincipal(actor,PlatformRole.valueOf(row.getString(1)),row.getInt(2)));
                }
            }
            HttpResponses.data(response,FinanceJson.write(Map.of("csrfToken",SecurityContext.getOrCreateCsrfToken(request))));
        } catch(Exception failure) { HttpResponses.error(response,503,"FIXTURE_UNAVAILABLE","Fixture session is unavailable"); }
    }
}
