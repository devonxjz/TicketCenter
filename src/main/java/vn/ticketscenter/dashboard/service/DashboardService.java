package vn.ticketscenter.dashboard.service;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.dashboard.repository.DashboardRepository;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.infrastructure.*;
public final class DashboardService {
    private final FinanceDatabase database;private final DashboardRepository repository=new DashboardRepository();private final Clock clock;
    public DashboardService(FinanceDatabase database){this(database,Clock.systemUTC());}
    public DashboardService(FinanceDatabase database,Clock clock){this.database=Objects.requireNonNull(database);this.clock=clock;}
    public String admin(UserPrincipal actor)throws SQLException{try(var db=database.open()){FinanceAuthorization.requireAdmin(db,actor);return FinanceJson.write(Map.of("data",repository.overview(db,null,clock.instant())));}}
    public String organization(UserPrincipal actor,UUID org)throws SQLException{try(var db=database.open()){FinanceAuthorization.requireManager(db,actor,org);return FinanceJson.write(Map.of("data",repository.overview(db,org,clock.instant())));}}
}
