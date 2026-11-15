package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Step 2: loaddata.jsp can reach the database and shows row counts. */
class LoadDataPageTest {

    @BeforeAll
    static void databaseIsUp() throws Exception {
        // Make sure SQL Server has finished starting before hitting the page.
        DbClient.connect().close();
    }

    @Test
    void pageLoads() throws Exception {
        assertEquals(200, AppClient.get("loaddata.jsp").statusCode());
    }

    @Test
    void reportsSuccessfulConnection() throws Exception {
        String body = AppClient.get("loaddata.jsp").body();
        assertTrue(body.contains("Database connection successful"), body);
        assertFalse(body.contains("Unable to access the store database"));
    }

    @Test
    void countsMatchTheDatabase() throws Exception {
        String body = AppClient.get("loaddata.jsp").body();
        assertEquals(DbClient.queryInt("SELECT COUNT(*) FROM customer"), count(body, "Customers"));
        assertEquals(DbClient.queryInt("SELECT COUNT(*) FROM product"), count(body, "Products"));
        assertEquals(DbClient.queryInt("SELECT COUNT(*) FROM ordersummary"), count(body, "Orders"));
    }

    @Test
    void isBrandedSkiStore() throws Exception {
        String body = AppClient.get("loaddata.jsp").body();
        assertTrue(body.contains("Ski Store"));
        assertFalse(body.contains("COSC 304"), "starter branding should be replaced");
    }

    @Test
    void hasResetFormAndLinkHome() throws Exception {
        String body = AppClient.get("loaddata.jsp").body();
        assertTrue(body.contains("method=\"post\""), "reset form should POST");
        assertTrue(body.contains("value=\"Reset Database\""));
        assertTrue(body.contains("href=\"index.jsp\""));
    }

    /** Pull the number out of e.g. "<p>Customers: 5</p>". */
    static int count(String body, String label) {
        Matcher m = Pattern.compile(label + ":\\s*(\\d+)").matcher(body);
        assertTrue(m.find(), "page should show a count for " + label);
        return Integer.parseInt(m.group(1));
    }
}
