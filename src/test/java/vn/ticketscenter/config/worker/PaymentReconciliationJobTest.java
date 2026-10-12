package vn.ticketscenter.config.worker;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.integration.VnpayQueryClient;
import vn.ticketscenter.payment.integration.VnpayQueryClient.Outcome;
import vn.ticketscenter.payment.repository.PaymentRepository.ReconcileCandidate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentReconciliationJobTest {
    private static ReconcileCandidate c() {
        return new ReconcileCandidate(UUID.randomUUID(), UUID.randomUUID(), "T" + UUID.randomUUID(), BigDecimal.TEN, Instant.now());
    }

    private final List<String> applied = new ArrayList<>();

    private int run(List<ReconcileCandidate> batch, Map<ReconcileCandidate, VnpayQueryClient.Result> results, Set<ReconcileCandidate> applyFails) {
        return new PaymentReconciliationJob(limit -> batch,
                cand -> { var r = results.get(cand); if (r == null) throw new IllegalStateException("net"); return r; },
                (cand, status, ref) -> { if (applyFails.contains(cand)) throw new IllegalStateException("db"); applied.add(status + ":" + ref); },
                id -> () -> { }, 10).runOnce();
    }

    @Test void appliesCapturedAndFailed() {
        var a = c(); var b = c();
        int n = run(List.of(a, b), Map.of(a, new VnpayQueryClient.Result(Outcome.CAPTURED, "1"), b, new VnpayQueryClient.Result(Outcome.FAILED, "2")), Set.of());
        assertEquals(2, n);
        assertEquals(List.of("CAPTURED:1", "FAILED:2"), applied);
    }
    @Test void unknownIsLeftForRetry() {
        var a = c();
        assertEquals(0, run(List.of(a), Map.of(a, VnpayQueryClient.Result.unknown()), Set.of()));
        assertEquals(List.of(), applied);
    }
    @Test void oneFailingPaymentDoesNotBlockBatch() {
        var broken = c(); var failsApply = c(); var ok = c();
        int n = run(List.of(broken, failsApply, ok), Map.of(failsApply, new VnpayQueryClient.Result(Outcome.CAPTURED, "x"), ok, new VnpayQueryClient.Result(Outcome.CAPTURED, "ok")), Set.of(failsApply));
        assertEquals(1, n);
        assertEquals(List.of("CAPTURED:ok"), applied);
    }
}
