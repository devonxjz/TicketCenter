package vn.ticketscenter.dashboard.service;

import vn.ticketscenter.settlement.dto.FinanceJson;
import vn.ticketscenter.settlement.dao.FinanceDatabase;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.dashboard.cache.DashboardCache;
import vn.ticketscenter.dashboard.dao.DashboardRepository;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.dao.FinanceAuthorization;

public final class DashboardServiceImpl implements DashboardService {
    private final FinanceDatabase database;
    private final DashboardRepository repository = new DashboardRepository();
    private final Clock clock;
    private final DashboardCache cache;

    public DashboardServiceImpl(FinanceDatabase database) {
        this(database, DashboardCache.none(), Clock.systemUTC());
    }

    public DashboardServiceImpl(FinanceDatabase database, Clock clock) {
        this(database, DashboardCache.none(), clock);
    }

    public DashboardServiceImpl(FinanceDatabase database, DashboardCache cache) {
        this(database, cache, Clock.systemUTC());
    }

    public DashboardServiceImpl(FinanceDatabase database, DashboardCache cache, Clock clock) {
        this.database = Objects.requireNonNull(database);
        this.cache = Objects.requireNonNull(cache);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public String admin(UserPrincipal actor) throws SQLException {
        try (var db = database.open()) {
            FinanceAuthorization.requireAdmin(db, actor);
            String hit = cache.get();
            if (hit != null) return hit;
            String json =
                    FinanceJson.write(
                            Map.of("data", repository.overview(db, null, clock.instant())));
            cache.put(json);
            return json;
        }
    }

    @Override
    public String organization(UserPrincipal actor, UUID org) throws SQLException {
        try (var db = database.open()) {
            FinanceAuthorization.requireManager(db, actor, org);
            repository.requireOrganization(db, org);
            return FinanceJson.write(Map.of("data", repository.overview(db, org, clock.instant())));
        }
    }
}
