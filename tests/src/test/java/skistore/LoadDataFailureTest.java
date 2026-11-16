package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * loaddata.jsp's error handling: what the page shows when the reset fails partway
 * and the store tables are missing. We force a failure by pinning the customer
 * table with a schema-bound view, so the DDL's DROP TABLE customer is refused.
 * The database is always restored afterwards.
 */
class LoadDataFailureTest {

    @BeforeEach
    void pinCustomerTable() throws Exception {
        ResetDatabaseTest.reset();
        DbClient.update("IF OBJECT_ID('dbo.block_reset', 'V') IS NOT NULL DROP VIEW dbo.block_reset");
        DbClient.update("EXEC('CREATE VIEW dbo.block_reset WITH SCHEMABINDING AS "
                      + "SELECT customerId FROM dbo.customer')");
    }

    @AfterEach
    void restoreDatabase() throws Exception {
        DbClient.update("IF OBJECT_ID('dbo.block_reset', 'V') IS NOT NULL DROP VIEW dbo.block_reset");
        ResetDatabaseTest.reset();
        assertTrue(DbClient.queryInt("SELECT COUNT(*) FROM product") > 0, "database should be restored");
    }

    @Test
    void failedResetIsReportedWithoutLeakingDetails() throws Exception {
        HttpResponse<String> response = AppClient.post("loaddata.jsp", "");
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("Database reset failed"), body);
        assertFalse(body.contains("Database reset completed successfully"));
        // The error detail goes to the Tomcat log, not to the browser.
        assertFalse(body.contains("SQLServerException"), "stack traces must not be shown to users");
        assertFalse(body.contains("block_reset"));
    }

    @Test
    void brokenDatabaseShowsAFriendlyMessage() throws Exception {
        AppClient.post("loaddata.jsp", "");   // fails partway: some tables are gone now
        String body = AppClient.get("loaddata.jsp").body();
        assertTrue(body.contains("Unable to access the store database"), body);
        assertTrue(body.contains("Check that Docker and SQL Server are running"));
        assertFalse(body.contains("Database connection successful"));
        // The page itself still works, so the Reset button is there to repair things.
        assertTrue(body.contains("value=\"Reset Database\""));
    }

    @Test
    void resetRepairsTheDatabaseOnceTheBlockIsGone() throws Exception {
        AppClient.post("loaddata.jsp", "");
        DbClient.update("DROP VIEW dbo.block_reset");
        HttpResponse<String> response = AppClient.post("loaddata.jsp", "");
        assertTrue(response.body().contains("Database reset completed successfully"), response.body());
        assertTrue(response.body().contains("Database connection successful"));
    }
}
