package vn.ticketscenter.audit.service;
import java.sql.*;
import java.util.*;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.audit.dao.AuditRepository;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.infrastructure.*;
public final class AuditService {
    private final FinanceDatabase database;private final AuditRepository repository=new AuditRepository();
    public AuditService(FinanceDatabase database){this.database=Objects.requireNonNull(database);}
    public String list(UserPrincipal actor,AuditFilter filter)throws SQLException{try(var db=database.open()){FinanceAuthorization.requireAdmin(db,actor);return FinanceJson.write(Map.of("data",repository.list(db,filter)));}}
    public String detail(UserPrincipal actor,UUID id)throws SQLException{try(var db=database.open()){FinanceAuthorization.requireAdmin(db,actor);return FinanceJson.write(Map.of("data",repository.detail(db,id)));}}
}
