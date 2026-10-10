package vn.ticketscenter.report.service;

import java.io.*;
import java.sql.*;
import java.util.Objects;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.csv.StreamingCsvExporter;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.report.repository.ReportRepository;
import vn.ticketscenter.settlement.infrastructure.*;

public final class ReportService {
    private final FinanceDatabase database;private final ReportCache cache;private final ReportRepository repository=new ReportRepository();
    public ReportService(FinanceDatabase database,ReportCache cache){this.database=Objects.requireNonNull(database);this.cache=Objects.requireNonNull(cache);}
    private void authorize(Connection db,UserPrincipal actor,ReportFilter f)throws SQLException{
        if(f.organizationId()==null)FinanceAuthorization.requireAdmin(db,actor);else FinanceAuthorization.requireManager(db,actor,f.organizationId());repository.requireEventScope(db,f);
    }
    public String json(UserPrincipal actor,ReportFilter filter)throws SQLException{
        try(var db=database.open()){authorize(db,actor,filter);String key=cache.key(actor,filter),hit=cache.get(key);if(hit!=null)return hit;
            String json=FinanceJson.write(java.util.Map.of("data",repository.page(db,filter)));cache.put(key,json);return json;
        }
    }
    public void export(UserPrincipal actor,ReportFilter filter,OutputStream output)throws SQLException,IOException{
        try(var db=database.open()){authorize(db,actor,filter);try(var cursor=repository.cursor(db,filter)){StreamingCsvExporter.write(output,cursor.headers(),cursor);}}
    }
}
