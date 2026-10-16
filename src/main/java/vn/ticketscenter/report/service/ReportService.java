package vn.ticketscenter.report.service;

import java.io.*;
import java.sql.*;
import java.util.Objects;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.csv.StreamingCsvExporter;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.report.dao.ReportRepository;
import vn.ticketscenter.settlement.infrastructure.*;

public final class ReportService {
    private final FinanceDatabase database;private final ReportCache cache;private final ReportRepository repository=new ReportRepository();
    public ReportService(FinanceDatabase database,ReportCache cache){this.database=Objects.requireNonNull(database);this.cache=Objects.requireNonNull(cache);}
    private void authorize(Connection db,UserPrincipal actor,ReportFilter f)throws SQLException{
        if(f.organizationId()==null)FinanceAuthorization.requireAdmin(db,actor);else FinanceAuthorization.requireManager(db,actor,f.organizationId());repository.requireEventScope(db,f);
    }
    public String json(UserPrincipal actor,ReportFilter filter)throws SQLException{
        return json(actor,filter,false);
    }
    public String jsonAdmin(UserPrincipal actor,ReportFilter filter)throws SQLException{return json(actor,filter,true);}
    private String json(UserPrincipal actor,ReportFilter filter,boolean adminOnly)throws SQLException{
        try(var db=database.open()){if(adminOnly)FinanceAuthorization.requireAdmin(db,actor);authorize(db,actor,filter);String key=cache.key(actor,filter),hit=cache.get(key);if(hit!=null)return hit;
            String json=FinanceJson.write(java.util.Map.of("data",repository.page(db,filter)));cache.put(key,json);return json;
        }
    }
    public void export(UserPrincipal actor,ReportFilter filter,OutputStream output)throws SQLException,IOException{
        try(var db=database.open()){authorize(db,actor,filter);try(var cursor=repository.cursor(db,filter)){StreamingCsvExporter.write(output,cursor.headers(),cursor);}}
    }
    public String options(UserPrincipal actor,java.util.UUID selectedOrg)throws SQLException{
        try(var db=database.open()){
            boolean admin=true;try{FinanceAuthorization.requireAdmin(db,actor);}catch(FinanceException e){if(e.status()!=403)throw e;admin=false;}
            if(selectedOrg!=null)FinanceAuthorization.requireManager(db,actor,selectedOrg);
            var organizations=new java.util.ArrayList<java.util.Map<String,Object>>();
            String sql="SELECT o.id,o.name FROM dbo.vw_FinanceOrganizations o"+(admin?"":" WHERE EXISTS (SELECT 1 FROM dbo.vw_ActiveManagerMemberships m WHERE m.organization_id=o.id AND m.user_id=?)")+" ORDER BY o.name,o.id";
            try(var q=db.prepareStatement(sql)){if(!admin)q.setString(1,actor.actorId().toString());try(var r=q.executeQuery()){while(r.next())organizations.add(vn.ticketscenter.report.dao.ReadProjection.row(r,java.util.List.of("id","name")));}}
            if(!admin && organizations.isEmpty())throw new FinanceException("FORBIDDEN",403);
            var events=new java.util.ArrayList<java.util.Map<String,Object>>();
            if(selectedOrg!=null){try(var q=db.prepareStatement("SELECT id,title FROM dbo.vw_FinanceEvents WHERE organization_id=? ORDER BY title,id")){q.setString(1,selectedOrg.toString());try(var r=q.executeQuery()){while(r.next())events.add(vn.ticketscenter.report.dao.ReadProjection.row(r,java.util.List.of("id","title")));}}}
            return FinanceJson.write(java.util.Map.of("data",java.util.Map.of("isAdmin",admin,"organizations",organizations,"events",events)));
        }
    }
}
