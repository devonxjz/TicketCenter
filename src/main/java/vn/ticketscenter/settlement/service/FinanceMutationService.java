package vn.ticketscenter.settlement.service;

import java.util.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.commission.dto.CommissionRuleCreateDto;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
import vn.ticketscenter.settlement.payout.*;
import vn.ticketscenter.settlement.dao.FinanceRepository;
import vn.ticketscenter.settlement.commission.service.CommissionRuleCache;
import vn.ticketscenter.settlement.infrastructure.FinanceJson;

public final class FinanceMutationService {
    private final FinanceRepository repository;
    private final PayoutAdapter adapter;
    private final CommissionRuleCache cache;
    private final SettlementReadCache settlementCache;

    public FinanceMutationService(FinanceRepository repository, PayoutAdapter adapter) {
        this(repository, adapter, CommissionRuleCache.none(), SettlementReadCache.none());
    }

    public FinanceMutationService(
            FinanceRepository repository,
            PayoutAdapter adapter,
            CommissionRuleCache cache,
            SettlementReadCache settlementCache) {
        this.repository = repository;
        this.adapter = adapter;
        this.cache = cache;
        this.settlementCache = settlementCache;
    }

    public String readJson(UserPrincipal actor, String kind, UUID id) {
        if (kind.equals("rules")) {
            repository.requireRuleRead(actor, id);
            return cache.read(id, () -> FinanceJson.write(repository.rules(actor, id)));
        }
        if (kind.equals("summary") || kind.equals("blockers")) {
            repository.requireEventRead(actor, id);
            return settlementCache.read(
                    id,
                    kind.equals("summary")
                            ? SettlementReadCache.Kind.SUMMARY
                            : SettlementReadCache.Kind.BLOCKERS,
                    () -> FinanceJson.write(read(actor, kind, id)));
        }
        return FinanceJson.write(read(actor, kind, id));
    }

    public Object read(UserPrincipal actor, String kind, UUID id) {
        return switch (kind) {
            case "rules" -> repository.rules(actor, id);
            case "summary" -> repository.summary(actor, id);
            case "blockers" -> {
                var rows = repository.blockers(actor, id);
                yield Map.of("blockerCount", rows.size(), "items", rows);
            }
            case "payouts" -> repository.payouts(actor, id);
            default -> throw new FinanceException("NOT_FOUND", 404);
        };
    }

    public Map<String, Object> commission(
            UserPrincipal actor, UUID org, UUID rule, UUID key, CommissionRuleCreateDto terms) {
        if (rule == null) return repository.createRule(actor, org, key, terms);
        UUID currentOrg = repository.ruleOrganization(actor, rule);
        if (org != null && !org.equals(currentOrg))
            throw new FinanceException("COMMISSION_RULE_INVALID", 400);
        return repository.updateRule(actor, rule, currentOrg, key, terms);
    }

    public Map<String, Object> recalculate(UserPrincipal actor, UUID event) {
        return repository.recalculate(actor, event);
    }

    public Map<String, Object> confirm(UserPrincipal actor, UUID settlement) {
        return repository.confirm(actor, settlement);
    }

    public Map<String, Object> payout(
            UserPrincipal actor, UUID settlement, UUID key, PayoutRequestDto terms)
            throws java.sql.SQLException {
        repository.requireAdministrator(actor);
        return new PayoutExecutionService(repository, adapter)
                .execute(actor, settlement, key, terms);
    }

    public Map<String, Object> reconcile(
            UserPrincipal actor, UUID settlement, UUID payout, PayoutRequestDto terms)
            throws java.sql.SQLException {
        repository.requireAdministrator(actor);
        var existing =
                terms == null
                        ? repository.payoutForReconciliation(actor, settlement, payout)
                        : repository.existing(actor, settlement, payout, terms);
        if (existing == null) throw new FinanceException("NOT_FOUND", 404);
        if (!"PENDING".equals(existing.get("status"))) return existing;
        PayoutAdapter.Result verified;
        try {
            verified = adapter.lookup(payout);
        } catch (Exception unavailable) {
            throw new FinanceException("PAYOUT_RECONCILIATION_UNAVAILABLE", 503);
        }
        return repository.recordStored(actor, settlement, payout, verified);
    }
}
