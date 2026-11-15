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

    /** Run an INSERT/UPDATE/DELETE and return the affected row count. */
    public static int update(String sql) throws SQLException, InterruptedException {
        try (Connection con = connect();
             Statement stmt = con.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    /**
     * SQL Server under emulation can take a couple of minutes on first start,
     * and the orders DB only exists once the DDL has run. Retry until a query
     * against the customer table succeeds.
     */
    private static void waitForDatabase() throws InterruptedException {
        if (ready) return;
        long deadline = System.currentTimeMillis() + 180_000;
        SQLException last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
                 Statement stmt = con.createStatement()) {
                stmt.executeQuery("SELECT COUNT(*) FROM customer").close();
                ready = true;
                return;
            } catch (SQLException e) {
                last = e;
                Thread.sleep(3_000);
            }
        }
        throw new IllegalStateException("Database not ready at " + URL, last);
    }
}
