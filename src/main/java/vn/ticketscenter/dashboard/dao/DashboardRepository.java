package vn.ticketscenter.dashboard.dao;

import java.sql.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.report.dao.ReadProjection;
import vn.ticketscenter.settlement.service.FinanceException;

public final class DashboardRepository {
    public Map<String, Object> overview(Connection db, UUID org, Instant now) throws SQLException {
        Instant
                from =
                        now.atOffset(ZoneOffset.UTC)
                                .toLocalDate()
                                .atStartOfDay()
                                .toInstant(ZoneOffset.UTC),
                to = from.plus(Duration.ofDays(1));
        var result = new LinkedHashMap<String, Object>();
        result.put("asOf", now);
        result.put("timezone", "UTC");
        result.put("from", from);
        result.put("to", to);
        result.put("organizationId", org);
        if (org == null) {
            var counts =
                    counts(
                            db,
                            "SELECT total_users,active_organizations,pending_organizations,pending_events,pending_refunds,draft_settlements FROM dbo.vw_DashboardPlatformCounts",
                            List.of(),
                            6);
            result.put("totalUsers", counts[0]);
            result.put("activeOrganizations", counts[1]);
            result.put("pendingOrganizations", counts[2]);
            result.put("pendingEvents", counts[3]);
            result.put("pendingRefunds", counts[4]);
            result.put("draftSettlements", counts[5]);
        } else {
            var counts =
                    counts(
                            db,
                            "SELECT event_count,pending_events,paid_orders FROM dbo.fn_DashboardOrganizationCounts(?)",
                            List.of(org),
                            3);
            result.put("eventCount", counts[0]);
            result.put("pendingEvents", counts[1]);
            result.put("paidOrders", counts[2]);
        }
        var cash = cash(db, org, from, to);
        var cohort = cohort(db, org, from, to);
        result.put("todayCashFlow", cash);
        result.put("todayCohortGrossRevenue", cohort.get("grossRevenue"));
        result.put("todayCohortCommission", cohort.get("totalCommission"));
        var cumulative = cohort(db, org, Instant.EPOCH, now);
        result.put("cumulativeGrossRevenue", cumulative.get("grossRevenue"));
        result.put("cumulativeCommission", cumulative.get("totalCommission"));
        result.put("cumulativeNetCashFlow", cash(db, org, Instant.EPOCH, now).get("netCashFlow"));
        result.put("cumulativeFrom", Instant.EPOCH);
        result.put("cumulativeTo", now);
        result.put("commissionTimeBasis", "ORDER_PAID_AT_WITH_REFUNDS_BEFORE_TO");
        var days = new ArrayList<Map<String, Object>>();
        for (int previous = 6; previous >= 0; previous--) {
            Instant dayFrom = from.minus(Duration.ofDays(previous)),
                    dayTo = dayFrom.plus(Duration.ofDays(1));
            var dayCash = previous == 0 ? cash : cash(db, org, dayFrom, dayTo);
            var dayCohort = previous == 0 ? cohort : cohort(db, org, dayFrom, dayTo);
            var day = new LinkedHashMap<String, Object>();
            day.put("date", dayFrom.atOffset(ZoneOffset.UTC).toLocalDate().toString());
            day.put("from", dayFrom);
            day.put("to", dayTo);
            day.put("cohortGrossRevenue", dayCohort.get("grossRevenue"));
            day.put("netCashFlow", dayCash.get("netCashFlow"));
            days.add(day);
        }
        result.put("dailyMetrics", days);
        result.put(
                "dailyMetricTimeBasis",
                Map.of(
                        "cohortGrossRevenue",
                        "ORDER_PAID_AT",
                        "netCashFlow",
                        "PAYMENT_PAID_AT_AND_REFUND_PROCESSED_AT"));
        return result;
    }

    private Map<String, Object> cash(Connection db, UUID org, Instant from, Instant to)
            throws SQLException {
        var columns =
                List.of("ticket_captured", "compensation_captured", "ticket_refund", "compensation_refund", "net_cash_flow");
        String sums = String.join(",", columns.stream().map(c -> "COALESCE(SUM(" + c + "),0) " + c).toList());
        return ReadProjection.first(
                db,
                "SELECT " + sums + " FROM dbo.fn_GetOrganizationCashFlow(?,?,?)",
                parameters(org, from, to),
                row -> ReadProjection.row(row, columns));
    }

    private Map<String, Object> cohort(Connection db, UUID org, Instant from, Instant to)
            throws SQLException {
        return ReadProjection.first(
                db,
                "SELECT COALESCE(SUM(gross_revenue),0) gross_revenue,COALESCE(SUM(total_commission),0) total_commission FROM dbo.fn_GetOrganizationRevenue(?,?,?)",
                parameters(org, from, to),
                row -> ReadProjection.row(row, List.of("gross_revenue", "total_commission")));
    }

    /** Arrays.asList keeps a null organization (platform-wide) that List.of would reject. */
    private List<Object> parameters(UUID org, Instant from, Instant to) {
        return Arrays.asList(org, from, to);
    }

    private long[] counts(Connection db, String sql, List<Object> values, int columns)
            throws SQLException {
        return ReadProjection.first(
                db,
                sql,
                values,
                row -> {
                    long[] result = new long[columns];
                    for (int index = 0; index < columns; index++) result[index] = row.getLong(index + 1);
                    return result;
                });
    }

    public void requireOrganization(Connection db, UUID org) throws SQLException {
        try (var query =
                db.prepareStatement("SELECT 1 FROM dbo.vw_FinanceOrganizations WHERE id=?")) {
            query.setString(1, org.toString());
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new FinanceException("NOT_FOUND", 404);
            }
        }
    }
}
