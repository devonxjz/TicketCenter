package vn.ticketscenter.payment.integration;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public class SimulatedRefundGateway implements RefundGateway {
    private final Path stateDirectory;
    private final Status simulatedOutcome;
    private final boolean timeoutAfterSideEffect;

    public SimulatedRefundGateway(Path stateDirectory, Status simulatedOutcome, boolean timeoutAfterSideEffect) {
        this.stateDirectory = Objects.requireNonNull(stateDirectory);
        this.simulatedOutcome = Objects.requireNonNull(simulatedOutcome);
        this.timeoutAfterSideEffect = timeoutAfterSideEffect;
    }

    @Override
    public Result submit(UUID refundId, BigDecimal amount) throws Timeout {
        if (timeoutAfterSideEffect) {
            throw new Timeout();
        }
        return new Result(simulatedOutcome, "SIM-" + refundId);
    }

    @Override
    public Result query(String providerReference) throws Timeout {
        return new Result(simulatedOutcome, providerReference);
    }
}
