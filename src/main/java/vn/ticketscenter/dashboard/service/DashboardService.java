package vn.ticketscenter.dashboard.service;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.dashboard.repository.DashboardRepository;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.infrastructure.*;
public final class DashboardService {
    private final FinanceDatabase database;private final DashboardRepository repository=new DashboardRepository();private final Clock clock;private final DashboardCache cache;
    public DashboardService(FinanceDatabase database){this(database,DashboardCache.none(),Clock.systemUTC());}
    public DashboardService(FinanceDatabase database,Clock clock){this(database,DashboardCache.none(),clock);}
    public DashboardService(FinanceDatabase database,DashboardCache cache){this(database,cache,Clock.systemUTC());}
    public DashboardService(FinanceDatabase database,DashboardCache cache,Clock clock){this.database=Objects.requireNonNull(database);this.cache=Objects.requireNonNull(cache);this.clock=Objects.requireNonNull(clock);}
    public String admin(UserPrincipal actor)throws SQLException{try(var db=database.open()){
        FinanceAuthorization.requireAdmin(db,actor);String hit=cache.get();if(hit!=null)return hit;
        String json=FinanceJson.write(Map.of("data",repository.overview(db,null,clock.instant())));cache.put(json);return json;
    }}
    public String organization(UserPrincipal actor,UUID org)throws SQLException{try(var db=database.open()){FinanceAuthorization.requireManager(db,actor,org);return FinanceJson.write(Map.of("data",repository.overview(db,org,clock.instant())));}}
}
