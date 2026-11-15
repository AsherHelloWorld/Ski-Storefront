package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * Step 2: the Reset Database button re-runs the DDL.
 * WARNING: this wipes the database back to the DDL's sample data on every run.
 */
class ResetDatabaseTest {

    @Test
    void resetRestoresSampleData() throws Exception {
        // Reset once so we start from the DDL's data, then record the counts.
        AppClient.post("loaddata.jsp", "");
        int customers = DbClient.queryInt("SELECT COUNT(*) FROM customer");
        int products = DbClient.queryInt("SELECT COUNT(*) FROM product");
        int orders = DbClient.queryInt("SELECT COUNT(*) FROM ordersummary");

        // Dirty the data: add a customer and rename a product.
        // (Deleting a product isn't allowed: orderproduct rows reference it.)
        DbClient.update("INSERT INTO customer (firstName, lastName, userid, password) "
                      + "VALUES ('Reset', 'Test', 'resettest', 'pw')");
        DbClient.update("UPDATE product SET productName = 'RESET-TEST' "
                      + "WHERE productId = (SELECT MIN(productId) FROM product)");
        assertEquals(customers + 1, DbClient.queryInt("SELECT COUNT(*) FROM customer"));
        assertEquals(1, DbClient.queryInt("SELECT COUNT(*) FROM product WHERE productName = 'RESET-TEST'"));

        HttpResponse<String> response = AppClient.post("loaddata.jsp", "");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Database reset completed successfully"), response.body());

        // Everything is back to the sample data.
        assertEquals(customers, DbClient.queryInt("SELECT COUNT(*) FROM customer"));
        assertEquals(products, DbClient.queryInt("SELECT COUNT(*) FROM product"));
        assertEquals(orders, DbClient.queryInt("SELECT COUNT(*) FROM ordersummary"));
        assertEquals(0, DbClient.queryInt("SELECT COUNT(*) FROM customer WHERE userid = 'resettest'"));
        assertEquals(0, DbClient.queryInt("SELECT COUNT(*) FROM product WHERE productName = 'RESET-TEST'"));
    }

    @Test
    void pageStillShowsCountsAfterReset() throws Exception {
        String body = AppClient.post("loaddata.jsp", "").body();
        assertTrue(body.contains("Database connection successful"));
        assertEquals(DbClient.queryInt("SELECT COUNT(*) FROM product"),
                     LoadDataPageTest.count(body, "Products"));
    }
}
