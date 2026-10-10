package vn.ticketscenter.config.persistence;

import javax.sql.DataSource;

public final class ConnectionBudgets {
    private ConnectionBudgets() {}

    public static DataSource wrap(DataSource source) {
        return new ConnectionBudgetDataSource(source);
    }
}
