package vn.ticketscenter.settlement.infrastructure;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface FinanceDatabase {
    Connection open() throws SQLException;
}
