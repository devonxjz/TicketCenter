package vn.ticketscenter.settlement.dao;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.commission.dto.CommissionRuleCreateDto;
import vn.ticketscenter.settlement.infrastructure.*;
import vn.ticketscenter.settlement.payout.*;

/** Canonical bound JDBC projections; procedures own all finance writes. */
public final class FinanceRepository implements PayoutStore {
    private final FinanceDatabase database;
    private final Consumer<UUID> invalidation;
    private final Consumer<UUID> eventInvalidation;

    public FinanceRepository(FinanceDatabase database) {
        this(database, id -> {});
    }

    public FinanceRepository(FinanceDatabase database, Consumer<UUID> invalidation) {
        this(database, invalidation, id -> {});
    }

    public FinanceRepository(
            FinanceDatabase database,
            Consumer<UUID> invalidation,
            Consumer<UUID> eventInvalidation) {
        this.database = Objects.requireNonNull(database);
        this.invalidation = Objects.requireNonNull(invalidation);
        this.eventInvalidation = Objects.requireNonNull(eventInvalidation);
    }

    public void requireAdministrator(UserPrincipal actor) {
        admin(actor, c -> null);
    }

    public void requireRuleRead(UserPrincipal actor, UUID organizationId) {
        admin(
                actor,
                c -> {
                    require(c, "dbo.vw_FinanceOrganizations", organizationId);
                    return null;
                });
    }

    public void requireEventRead(UserPrincipal actor, UUID eventId) {
        admin(
                actor,
                c -> {
                    require(c, "dbo.vw_FinanceEvents", eventId);
                    return null;
                });
    }

    public Map<String, Object> options(UserPrincipal actor) {
        return options(actor, false);
    }

    public Map<String, Object> options(UserPrincipal actor, boolean endedOnly) {
        return admin(
                actor,
                c -> {
                    List<Map<String, Object>> orgs, events;
                    try (var q =
                            c.prepareStatement(
                                    "SELECT TOP(500) id,name FROM dbo.vw_FinanceOrganizations ORDER BY name,id")) {
                        FinanceJdbc.bind(q);
                        orgs = FinanceJdbc.list(q);
                    }
                    String eventSql =
                            "SELECT TOP(500) id,title,organization_id,settlement_id,settlement_status FROM dbo.vw_FinanceEvents"
                                    + (endedOnly ? " WHERE end_time<=?" : "")
                                    + " ORDER BY created_at DESC,id";
                    try (var q = c.prepareStatement(eventSql)) {
                        if (endedOnly) FinanceJdbc.bind(q, Instant.now());
                        else FinanceJdbc.bind(q);
                        events = FinanceJdbc.list(q);
                    }
                    return Map.of("organizations", orgs, "events", events);
                });
    }

    public List<Map<String, Object>> rules(UserPrincipal actor, UUID org) {
        return admin(
                actor,
                c -> {
                    require(c, "dbo.vw_FinanceOrganizations", org);
                    try (var q =
                            c.prepareStatement(
                                    "SELECT rule_id,organization_id,rate_percent,fixed_fee,effective_from,effective_to,associated_events_count FROM dbo.vw_CommissionRuleUsage WHERE organization_id=? ORDER BY effective_from DESC,rule_id")) {
                        FinanceJdbc.bind(q, org);
                        return FinanceJdbc.list(q);
                    }
                });
    }

    public List<Map<String, Object>> readySettlementEvents(UserPrincipal actor) {
        return admin(
                actor,
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    """
                    SELECT e.id,e.title,e.end_time
                    FROM dbo.vw_FinanceEvents e
                    WHERE e.end_time<=? AND (e.settlement_status IS NULL OR e.settlement_status='DRAFT')
                      AND EXISTS(SELECT 1 FROM dbo.vw_OrderFinancialSummary f WHERE f.event_id=e.id AND f.status='PAID')
                      AND NOT EXISTS(SELECT 1 FROM dbo.fn_GetSettlementBlockers(e.id,?))
                    ORDER BY e.end_time,e.id
                    """)) {
                        var now = Instant.now();
                        FinanceJdbc.bind(q, now, now);
                        return FinanceJdbc.list(q);
                    }
                });
    }

    public UUID ruleOrganization(UserPrincipal actor, UUID rule) {
        return admin(
                actor,
                c ->
                        organization(
                                c,
                                "SELECT organization_id FROM dbo.vw_CommissionRuleUsage WHERE rule_id=?",
                                rule));
    }

    public Map<String, Object> createRule(
            UserPrincipal actor, UUID org, UUID key, CommissionRuleCreateDto terms) {
        var result =
                admin(
                        actor,
                        c ->
                                FinanceJdbc.call(
                                        c,
                                        "{call dbo.usp_CreateCommissionRule(?,?,?,?,?,?,?,?)}",
                                        org,
                                        actor.actorId(),
                                        key,
                                        terms.ratePercent(),
                                        BigDecimal.valueOf(terms.fixedFee()),
                                        terms.effectiveFrom(),
                                        terms.effectiveTo(),
                                        actor.authVersion()));
        invalidate(org);
        return result;
    }

    public Map<String, Object> updateRule(
            UserPrincipal actor, UUID rule, UUID org, UUID key, CommissionRuleCreateDto terms) {
        var result =
                admin(
                        actor,
                        c ->
                                FinanceJdbc.call(
                                        c,
                                        "{call dbo.usp_UpdateCommissionRule(?,?,?,?,?,?,?,?,?)}",
                                        rule,
                                        org,
                                        actor.actorId(),
                                        key,
                                        terms.ratePercent(),
                                        BigDecimal.valueOf(terms.fixedFee()),
                                        terms.effectiveFrom(),
                                        terms.effectiveTo(),
                                        actor.authVersion()));
        invalidate(org);
        return result;
    }

    public Map<String, Object> summary(UserPrincipal actor, UUID event) {
        return admin(
                actor,
                c -> {
                    require(c, "dbo.vw_FinanceEvents", event);
                    try (var q =
                            c.prepareStatement(
                                    "SELECT settlement_id,event_id,organization_id,status,gross_revenue,total_refund,total_commission,net_payable,paid_amount,pending_amount,available_to_pay,confirmed_at,end_time FROM dbo.vw_SettlementSummary WHERE event_id=?")) {
                        FinanceJdbc.bind(q, event);
                        var rows = FinanceJdbc.list(q);
                        if (!rows.isEmpty()) return rows.getFirst();
                    }
                    try (var q =
                            c.prepareStatement(
                                    "SELECT event_id,organization_id,end_time,gross_revenue,total_refund,total_commission,net_payable FROM dbo.vw_EventSalesReport WHERE event_id=?")) {
                        FinanceJdbc.bind(q, event);
                        var row = FinanceJdbc.list(q).getFirst();
                        row.put("settlementId", null);
                        row.put("status", "DRAFT");
                        row.put("paidAmount", BigDecimal.ZERO);
                        row.put("pendingAmount", BigDecimal.ZERO);
                        row.put("availableToPay", row.get("netPayable"));
                        row.put("confirmedAt", null);
                        return row;
                    }
                });
    }

    public List<Map<String, Object>> blockers(UserPrincipal actor, UUID event) {
        return admin(
                actor,
                c -> {
                    require(c, "dbo.vw_FinanceEvents", event);
                    try (var q =
                            c.prepareStatement(
                                    "SELECT blocker_type,object_id,detail FROM dbo.fn_GetSettlementBlockers(?,?) ORDER BY blocker_type,object_id")) {
                        FinanceJdbc.bind(q, event, Instant.now());
                        return FinanceJdbc.list(q);
                    }
                });
    }

    public Map<String, Object> recalculate(UserPrincipal actor, UUID event) {
        return mutate(
                actor,
                "SELECT organization_id FROM dbo.vw_FinanceEvents WHERE id=?",
                event,
                "{call dbo.usp_RecalculateSettlement(?,?,?)}",
                event,
                actor.actorId(),
                actor.authVersion());
    }

    public Map<String, Object> confirm(UserPrincipal actor, UUID settlement) {
        return mutate(
                actor,
                settlementOrg(),
                settlement,
                "{call dbo.usp_ConfirmSettlement(?,?,?)}",
                settlement,
                actor.actorId(),
                actor.authVersion());
    }

    public List<Map<String, Object>> payouts(UserPrincipal actor, UUID settlement) {
        return admin(
                actor,
                c -> {
                    require(c, "dbo.vw_FinanceSettlements", settlement);
                    try (var q =
                            c.prepareStatement(
                                    "SELECT payout_id,settlement_id,amount,status,verification_state,provider_reference,processed_at FROM dbo.vw_SettlementPayouts WHERE settlement_id=? ORDER BY created_at DESC,payout_id DESC")) {
                        FinanceJdbc.bind(q, settlement);
                        return FinanceJdbc.list(q);
                    }
                });
    }

    public Map<String, Object> payoutForReconciliation(
            UserPrincipal actor, UUID settlement, UUID payout) {
        return admin(
                actor,
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT payout_id,settlement_id,amount,status,verification_state,provider_reference,processed_at,settlement_status,net_payable,paid_amount,pending_amount,available_to_pay FROM dbo.vw_SettlementPayouts WHERE payout_id=? AND settlement_id=?")) {
                        FinanceJdbc.bind(q, payout, settlement);
                        var rows = FinanceJdbc.list(q);
                        if (rows.isEmpty()) throw new FinanceException("NOT_FOUND", 404);
                        var result = rows.getFirst();
                        result.put("isReplay", true);
                        return result;
                    }
                });
    }

    /** Uses immutable reservation terms; recovery never requires retaining bank details. */
    public Map<String, Object> recordStored(
            UserPrincipal actor, UUID settlement, UUID payout, PayoutAdapter.Result verified) {
        UUID[] org = {null}, event = {null};
        var result =
                admin(
                        actor,
                        c -> {
                            org[0] = organization(c, settlementOrg(), settlement);
                            event[0] =
                                    organization(
                                            c,
                                            "SELECT event_id FROM dbo.vw_FinanceSettlements WHERE id=?",
                                            settlement);
                            BigDecimal amount;
                            byte[] fingerprint;
                            try (var q =
                                    c.prepareStatement(
                                            "SELECT amount,request_fingerprint FROM dbo.vw_SettlementPayouts WHERE payout_id=? AND settlement_id=?")) {
                                FinanceJdbc.bind(q, payout, settlement);
                                try (var rows = q.executeQuery()) {
                                    if (!rows.next()) throw new FinanceException("NOT_FOUND", 404);
                                    amount = rows.getBigDecimal(1);
                                    fingerprint = rows.getBytes(2);
                                }
                            }
                            return FinanceJdbc.call(
                                    c,
                                    "{call dbo.usp_RecordPayout(?,?,?,?,?,?,?,?)}",
                                    settlement,
                                    payout,
                                    actor.actorId(),
                                    amount,
                                    verified.reference(),
                                    verified.status(),
                                    fingerprint,
                                    actor.authVersion());
                        });
        invalidate(org[0]);
        invalidateEvent(event[0]);
        return result;
    }

    @Override
    public Map<String, Object> reserve(
            UserPrincipal actor, UUID settlement, UUID key, PayoutRequestDto terms) {
        return payout(actor, settlement, key, terms, null, null);
    }

    @Override
    public Map<String, Object> existing(
            UserPrincipal actor, UUID settlement, UUID key, PayoutRequestDto terms) {
        return admin(
                actor,
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT payout_id,settlement_id,amount,status,verification_state,provider_reference,processed_at,request_fingerprint,settlement_status,net_payable,paid_amount,pending_amount,available_to_pay FROM dbo.vw_SettlementPayouts WHERE payout_id=?")) {
                        FinanceJdbc.bind(q, key);
                        try (var rows = q.executeQuery()) {
                            if (!rows.next()) return null;
                            if (!settlement
                                            .toString()
                                            .equalsIgnoreCase(rows.getString("settlement_id"))
                                    || !java.security.MessageDigest.isEqual(
                                            terms.fingerprint(settlement),
                                            rows.getBytes("request_fingerprint")))
                                throw new FinanceException("PAYOUT_TERMS_CONFLICT", 409);
                            var result = FinanceJdbc.row(rows);
                            result.remove("requestFingerprint");
                            result.put("isReplay", true);
                            return result;
                        }
                    }
                });
    }

    @Override
    public Map<String, Object> record(
            UserPrincipal actor,
            UUID settlement,
            UUID key,
            PayoutRequestDto terms,
            PayoutAdapter.Result result) {
        return payout(actor, settlement, key, terms, result.status(), result.reference());
    }

    private Map<String, Object> payout(
            UserPrincipal actor,
            UUID settlement,
            UUID key,
            PayoutRequestDto terms,
            String verified,
            String reference) {
        return mutate(
                actor,
                settlementOrg(),
                settlement,
                "{call dbo.usp_RecordPayout(?,?,?,?,?,?,?,?)}",
                settlement,
                key,
                actor.actorId(),
                terms.amount(),
                reference,
                verified,
                terms.fingerprint(settlement),
                actor.authVersion());
    }

    private static String settlementOrg() {
        return "SELECT organization_id FROM dbo.vw_FinanceSettlements WHERE id=?";
    }

    private Map<String, Object> mutate(
            UserPrincipal actor, String lookup, UUID id, String call, Object... values) {
        UUID[] org = {null}, event = {null};
        var result =
                admin(
                        actor,
                        c -> {
                            org[0] = organization(c, lookup, id);
                            var response = FinanceJdbc.call(c, call, values);
                            event[0] =
                                    response.get("eventId") != null
                                            ? UUID.fromString(response.get("eventId").toString())
                                            : organization(
                                                    c,
                                                    "SELECT event_id FROM dbo.vw_FinanceSettlements WHERE id=?",
                                                    id);
                            return response;
                        });
        invalidate(org[0]);
        invalidateEvent(event[0]);
        return result;
    }

    private void invalidate(UUID org) {
        try {
            invalidation.accept(org);
        } catch (RuntimeException cacheOutage) {
            /* Commit is authoritative; cache is expendable. */
        }
    }

    private void invalidateEvent(UUID event) {
        try {
            eventInvalidation.accept(event);
        } catch (RuntimeException cacheOutage) {
            /* Bounded read TTL. */
        }
    }

    private static UUID organization(Connection c, String sql, UUID id) throws SQLException {
        try (var q = c.prepareStatement(sql)) {
            FinanceJdbc.bind(q, id);
            try (var rows = q.executeQuery()) {
                if (!rows.next()) throw new FinanceException("NOT_FOUND", 404);
                return UUID.fromString(rows.getString(1));
            }
        }
    }

    private static void require(Connection c, String view, UUID id) throws SQLException {
        if (id == null) throw new FinanceException("INVALID_ID", 400);
        try (var q = c.prepareStatement("SELECT id FROM " + view + " WHERE id=?")) {
            FinanceJdbc.bind(q, id);
            try (var row = q.executeQuery()) {
                if (!row.next()) throw new FinanceException("NOT_FOUND", 404);
            }
        }
    }

    @FunctionalInterface
    private interface Read<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> T admin(UserPrincipal actor, Read<T> operation) {
        if (actor == null) throw new FinanceException("UNAUTHENTICATED", 401);
        try (var c = database.open()) {
            if (!c.getAutoCommit())
                throw new SQLException("Finance connection must use procedure-owned transactions");
            FinanceAuthorization.requireAdmin(c, actor);
            return operation.run(c);
        } catch (SQLException failure) {
            throw FinanceJdbc.error(failure);
        }
    }
}
