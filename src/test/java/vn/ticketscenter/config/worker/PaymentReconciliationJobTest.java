package vn.ticketscenter.config.worker;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.integration.PayosGateway;
import vn.ticketscenter.payment.integration.PayosGateway.Outcome;
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

    private int run(List<ReconcileCandidate> batch, Map<ReconcileCandidate, PayosGateway.Result> results, Set<ReconcileCandidate> applyFails) {
        return new PaymentReconciliationJob(limit -> batch,
                cand -> { var r = results.get(cand); if (r == null) throw new IllegalStateException("net"); return r; },
                (cand, status, ref) -> { if (applyFails.contains(cand)) throw new IllegalStateException("db"); applied.add(status + ":" + ref); },
                id -> () -> { }, 10).runOnce();
    }

    @Test void appliesCapturedAndFailed() {
        var a = c(); var b = c();
        int n = run(List.of(a, b), Map.of(a, new PayosGateway.Result(Outcome.CAPTURED, "1"), b, new PayosGateway.Result(Outcome.FAILED, "2")), Set.of());
        assertEquals(2, n);
        assertEquals(List.of("CAPTURED:1", "FAILED:2"), applied);
    }
    @Test void unknownIsLeftForRetry() {
        var a = c();
        assertEquals(0, run(List.of(a), Map.of(a, PayosGateway.Result.unknown()), Set.of()));
        assertEquals(List.of(), applied);
    }
    @Test void oneFailingPaymentDoesNotBlockBatch() {
        var broken = c(); var failsApply = c(); var ok = c();
        int n = run(List.of(broken, failsApply, ok), Map.of(failsApply, new PayosGateway.Result(Outcome.CAPTURED, "x"), ok, new PayosGateway.Result(Outcome.CAPTURED, "ok")), Set.of(failsApply));
        assertEquals(1, n);
        assertEquals(List.of("CAPTURED:ok"), applied);
    }
    @Test void unresolvedPaymentsBackOffSoNewerOnesAreNotStarved() {
        long[] now = {0};
        List<ReconcileCandidate> stuck = new ArrayList<>();
        for (int i = 0; i < 3; i++) stuck.add(c());
        var real = c();
        List<ReconcileCandidate> queue = new ArrayList<>(stuck);
        queue.add(real);
        List<ReconcileCandidate> asked = new ArrayList<>();
        var job = new PaymentReconciliationJob(limit -> queue,
                cand -> { asked.add(cand); return cand == real ? new PayosGateway.Result(Outcome.CAPTURED, "ok") : PayosGateway.Result.unknown(); },
                (cand, status, ref) -> applied.add(status + ":" + ref), id -> () -> { }, 3, () -> now[0]);

        assertEquals(0, job.runOnce());
        assertEquals(stuck, asked);
        asked.clear();
        assertEquals(1, job.runOnce());
        assertEquals(List.of(real), asked);
        assertEquals(List.of("CAPTURED:ok"), applied);

        queue.remove(real);
        asked.clear();
        now[0] += java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
        assertEquals(0, job.runOnce());
        assertEquals(List.of(), asked);
        now[0] += java.util.concurrent.TimeUnit.MINUTES.toNanos(2);
        job.runOnce();
        assertEquals(stuck, asked);
    }
}
