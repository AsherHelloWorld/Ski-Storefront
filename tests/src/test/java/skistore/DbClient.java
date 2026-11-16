package skistore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Direct JDBC access to the store database, bypassing the website. */
public final class DbClient {

    /** Inside Docker the DB is reachable by its course alias; from the Mac it's localhost. */
    public static final String URL = System.getenv().getOrDefault("DB_URL",
            "jdbc:sqlserver://localhost:1433;databaseName=orders;encrypt=true;trustServerCertificate=true");

    private static final String USER = "sa";
    private static final String PASSWORD = "304#sa#pw";

    private static volatile boolean ready = false;

    private DbClient() {}

    /** Open a connection to the orders database (caller closes it). */
    public static Connection connect() throws SQLException, InterruptedException {
        waitForDatabase();
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Run a query that returns a single number, e.g. SELECT COUNT(*) ... */
    public static int queryInt(String sql) throws SQLException, InterruptedException {
        try (Connection con = connect();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Run a query and return each row as "col1|col2|..." (handy for before/after comparisons). */
    public static java.util.List<String> queryRows(String sql) throws SQLException, InterruptedException {
        java.util.List<String> rows = new java.util.ArrayList<>();
        try (Connection con = connect();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) row.append('|');
                    row.append(rs.getString(i));
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }

    /** Run an INSERT/UPDATE/DELETE and return the affected row count. */
    public static int update(String sql) throws SQLException, InterruptedException {
        try (Connection con = connect();
             Statement stmt = con.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    /**
     * SQL Server under emulation can take a couple of minutes on first start, and
     * the container then loads the DDL. Wait until that load has finished: the
     * DDL's last statement sets product image URLs, so once any are set it's done.
     */
    private static void waitForDatabase() throws InterruptedException {
        if (ready) return;
        long deadline = System.currentTimeMillis() + 300_000;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
                 Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM product WHERE productImageURL IS NOT NULL")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    ready = true;
                    return;
                }
                last = new IllegalStateException("DDL still loading");
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(3_000);
        }
        throw new IllegalStateException("Database not ready at " + URL, last);
    }
}
