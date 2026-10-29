package vn.ticketscenter.config.persistence;

import java.sql.SQLException;

public final class SqlErrors {

    private SqlErrors() {}

    public static boolean isTransient(SQLException failure, int... extraNumbers) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            int code = error.getErrorCode();
            if (code == 1205 || code == 1222) {
                return true;
            }
            for (int extra : extraNumbers) {
                if (code == extra) {
                    return true;
                }
            }
            String state = error.getSQLState();
            if (state != null
                    && (state.startsWith("08") || state.equals("HYT00") || state.equals("HYT01"))) {
                return true;
            }
        }
        return false;
    }
}
