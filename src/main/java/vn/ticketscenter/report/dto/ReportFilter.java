package vn.ticketscenter.report.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.ticketscenter.settlement.infrastructure.FinanceException;

/** One UTC/filter contract shared by JSON and unpaged CSV. */
public record ReportFilter(
        Metric metric,
        UUID organizationId,
        UUID eventId,
        Instant from,
        Instant to,
        int page,
        int pageSize) {
    public enum Metric {
        COHORT_REVENUE,
        CASH_FLOW,
        EVENT_SALES,
        SETTLEMENT_BALANCE
    }

    public static ReportFilter parse(Map<String, String> params, UUID routeOrganization) {
        try {
            var metric = Metric.valueOf(params.getOrDefault("metric", "COHORT_REVENUE"));
            UUID org = uuid(params.get("organizationId")), event = uuid(params.get("eventId"));
            if (routeOrganization != null && org != null && !routeOrganization.equals(org))
                throw invalid();
            if (routeOrganization != null) org = routeOrganization;
            String scope = params.get("scope"), format = params.get("format");
            if (scope != null
                    && !(scope.equals("ORGANIZATION") && org != null
                            || scope.equals("PLATFORM") && org == null)) throw invalid();
            if (format != null && !format.equals("csv")) throw invalid();
            Instant from = Instant.parse(params.get("from")), to = Instant.parse(params.get("to"));
            if (!from.isBefore(to)
                    || from.getNano() % 1_000_000 != 0
                    || to.getNano() % 1_000_000 != 0) throw invalid();
            int page = Integer.parseInt(params.getOrDefault("page", "1")),
                    size = Integer.parseInt(params.getOrDefault("pageSize", "20"));
            if (page < 1 || size < 1 || size > 100 || ((long) page - 1) * size > Integer.MAX_VALUE)
                throw invalid();
            return new ReportFilter(metric, org, event, from, to, page, size);
        } catch (FinanceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    public static UUID uuid(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) throw invalid();
        return UUID.fromString(value);
    }

    public int offset() {
        return (page - 1) * pageSize;
    }

    public String cacheIdentity() {
        return metric
                + "|"
                + organizationId
                + "|"
                + eventId
                + "|"
                + from
                + "|"
                + to
                + "|"
                + page
                + "|"
                + pageSize;
    }

    private static FinanceException invalid() {
        return new FinanceException("INVALID_REPORT_FILTER", 400);
    }
}
