package vn.ticketscenter.settlement.payout;

import java.io.IOException;
import java.util.Set;
import vn.ticketscenter.settlement.service.FinanceException;
import vn.ticketscenter.settlement.dao.FinanceDatabase;
import java.sql.*;
import java.security.MessageDigest;
import java.util.UUID;
import vn.ticketscenter.settlement.dao.FinanceJdbc;

/** Explicit, durable fixture simulator. Never represents an actual bank transfer. */
public final class PayoutAdapterImpl implements PayoutAdapter {
    private final FinanceDatabase database;
    private final String outcome;
    private final boolean timeoutAfterSideEffect;

    public PayoutAdapterImpl(
            FinanceDatabase database, String outcome, boolean timeoutAfterSideEffect) {
        if (!Set.of("SUCCEEDED", "FAILED", "UNKNOWN").contains(outcome)) {
            throw new IllegalArgumentException("Invalid simulated outcome");
        }
        this.database = database;
        this.outcome = outcome;
        this.timeoutAfterSideEffect = timeoutAfterSideEffect;
    }

    @Override
    public void requireAvailable() {
        try (Connection c = database.open()) {
            requireFixture(c);
        } catch (SQLException failure) {
            throw new FinanceException("PAYOUT_PROVIDER_UNAVAILABLE", 503);
        }
    }

    @Override
    public Result transfer(UUID payoutId, PayoutRequestDto request) throws Exception {
        Result result;
        try (Connection c = database.open()) {
            requireFixture(c);
            c.setAutoCommit(false);
            try {
                // Provider identity is its stable payout UUID, with a durable terms hash.
                byte[] fingerprint = request.fingerprint(payoutId);
                try (PreparedStatement q =
                        c.prepareStatement(
                                "SELECT fingerprint,result,provider_reference FROM dbo.tc_vuong_simulated_transfers WITH(UPDLOCK,HOLDLOCK) WHERE payout_id=?")) {
                    FinanceJdbc.bind(q, payoutId);
                    try (ResultSet rows = q.executeQuery()) {
                        if (rows.next()) {
                            if (!MessageDigest.isEqual(fingerprint, rows.getBytes(1))) {
                                throw new FinanceException("PAYOUT_TERMS_CONFLICT", 409);
                            }
                            result = new Result(rows.getString(2), rows.getString(3));
                        } else {
                            String reference = "SIM_" + payoutId.toString().replace("-", "");
                            try (PreparedStatement insert =
                                    c.prepareStatement(
                                            "INSERT dbo.tc_vuong_simulated_transfers(payout_id,fingerprint,result,provider_reference) VALUES(?,?,?,?)")) {
                                FinanceJdbc.bind(insert, payoutId, fingerprint, outcome, reference);
                                insert.executeUpdate();
                            }
                            result = new Result(outcome, reference);
                        }
                    }
                }
                c.commit();
            } catch (Exception failure) {
                c.rollback();
                throw failure;
            }
        }
        if (timeoutAfterSideEffect) {
            throw new IOException("Simulated timeout after durable fixture result");
        }
        return result;
    }

    /** Can be used by an authenticated reconciliation flow without making another transfer. */
    public Result lookup(UUID payoutId) throws SQLException {
        try (Connection c = database.open()) {
            requireFixture(c);
            try (PreparedStatement q =
                    c.prepareStatement(
                            "SELECT result,provider_reference FROM dbo.tc_vuong_simulated_transfers WHERE payout_id=?")) {
                FinanceJdbc.bind(q, payoutId);
                try (ResultSet rows = q.executeQuery()) {
                    if (!rows.next()) {
                        return new Result("UNKNOWN", null);
                    }
                    return new Result(rows.getString(1), rows.getString(2));
                }
            }
        }
    }

    private static void requireFixture(Connection c) throws SQLException {
        if (c.getCatalog() == null || !c.getCatalog().matches("TC_Vuong_Test_[A-Za-z0-9_]+")) {
            throw new FinanceException("PAYOUT_SIMULATOR_REQUIRES_FIXTURE", 503);
        }
        String url = c.getMetaData().getURL();
        if (!url.matches("(?i)^jdbc:sqlserver://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?;.*")) {
            throw new FinanceException("PAYOUT_SIMULATOR_REQUIRES_FIXTURE", 503);
        }
        try (PreparedStatement q =
                        c.prepareStatement(
                                "SELECT fixture_key FROM dbo.tc_vuong_test_fixture WHERE fixture_key='vuong-finance-v1'");
                ResultSet rows = q.executeQuery()) {
            if (!rows.next()) {
                throw new FinanceException("PAYOUT_SIMULATOR_REQUIRES_FIXTURE", 503);
            }
        }
    }
}
