package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Reset Database button (loaddata.jsp) re-runs the DDL.
 *
 * Contract: reset wipes everything users did on the site (orders placed, accounts
 * created, edits) but always restores the full catalog, so the site stays usable.
 * WARNING: these tests reset the database on every run.
 */
class ResetDatabaseTest {

    /** Everything a shopper sees about a product, keyed by id (carts store ids). */
    static final String CATALOG_SQL =
            "SELECT p.productId, p.productName, p.productPrice, p.productDesc, p.productImageURL, "
          + "c.categoryId, c.categoryName "
          + "FROM product p JOIN category c ON p.categoryId = c.categoryId ORDER BY p.productId";

    static final String CATEGORIES_SQL =
            "SELECT categoryId, categoryName FROM category ORDER BY categoryId";

    static final String INVENTORY_SQL =
            "SELECT productId, warehouseId, quantity, price FROM productinventory ORDER BY productId";

    private List<String> catalog;
    private List<String> categories;
    private List<String> inventory;
    private int customers;
    private int orders;
    private int orderLines;

    /** Start every test from a clean reset and record the DDL's state. */
    @BeforeEach
    void resetAndSnapshot() throws Exception {
        reset();
        catalog = DbClient.queryRows(CATALOG_SQL);
        categories = DbClient.queryRows(CATEGORIES_SQL);
        inventory = DbClient.queryRows(INVENTORY_SQL);
        customers = DbClient.queryInt("SELECT COUNT(*) FROM customer");
        orders = DbClient.queryInt("SELECT COUNT(*) FROM ordersummary");
        orderLines = DbClient.queryInt("SELECT COUNT(*) FROM orderproduct");
    }

    // ---- What reset wipes: user activity ------------------------------------

    @Test
    void resetDeletesNewCustomerAccounts() throws Exception {
        DbClient.update("INSERT INTO customer (firstName, lastName, userid, password) "
                      + "VALUES ('Reset', 'Test', 'resettest', 'pw')");
        assertEquals(customers + 1, DbClient.queryInt("SELECT COUNT(*) FROM customer"));

        reset();

        assertEquals(customers, DbClient.queryInt("SELECT COUNT(*) FROM customer"));
        assertEquals(0, DbClient.queryInt("SELECT COUNT(*) FROM customer WHERE userid = 'resettest'"));
    }

    @Test
    void resetDeletesPlacedOrders() throws Exception {
        // Place an order the way order.jsp will: a summary row plus line items.
        DbClient.update("INSERT INTO ordersummary (customerId, orderDate, totalAmount) "
                      + "VALUES (1, GETDATE(), 1599.98)");
        int newOrder = DbClient.queryInt("SELECT MAX(orderId) FROM ordersummary");
        DbClient.update("INSERT INTO orderproduct (orderId, productId, quantity, price) "
                      + "VALUES (" + newOrder + ", 2, 2, 799.99)");
        assertEquals(orders + 1, DbClient.queryInt("SELECT COUNT(*) FROM ordersummary"));

        reset();

        assertEquals(orders, DbClient.queryInt("SELECT COUNT(*) FROM ordersummary"));
        assertEquals(orderLines, DbClient.queryInt("SELECT COUNT(*) FROM orderproduct"));
    }

    @Test
    void resetRestartsOrderNumbering() throws Exception {
        DbClient.update("INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (1, GETDATE(), 0)");
        reset();
        DbClient.update("INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (1, GETDATE(), 0)");
        assertEquals(orders + 1, DbClient.queryInt("SELECT MAX(orderId) FROM ordersummary"),
                "the first order after a reset should get the next id after the sample orders");
    }

    @Test
    void resetDeletesSavedCartsAndReviews() throws Exception {
        DbClient.update("INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (1, GETDATE(), 0)");
        int newOrder = DbClient.queryInt("SELECT MAX(orderId) FROM ordersummary");
        DbClient.update("INSERT INTO incart (orderId, productId, quantity, price) "
                      + "VALUES (" + newOrder + ", 1, 1, 799.99)");
        DbClient.update("INSERT INTO review (reviewRating, reviewDate, customerId, productId, reviewComment) "
                      + "VALUES (5, GETDATE(), 1, 1, 'Great ski')");

        reset();

        assertEquals(0, DbClient.queryInt("SELECT COUNT(*) FROM incart"));
        assertEquals(0, DbClient.queryInt("SELECT COUNT(*) FROM review"));
    }

    // ---- What reset keeps: the catalog --------------------------------------

    @Test
    void resetRestoresTheExactCatalog() throws Exception {
        // Damage the catalog in every way: edit, add, remove.
        DbClient.update("UPDATE product SET productName = 'RESET-TEST', productPrice = 1 WHERE productId = 1");
        DbClient.update("INSERT INTO product (productName, productPrice, categoryId) VALUES ('Not In DDL', 500, 1)");
        DbClient.update("DELETE FROM productinventory WHERE productId = 30");
        DbClient.update("DELETE FROM product WHERE productId = 30");   // not referenced by any order
        DbClient.update("UPDATE category SET categoryName = 'Broken' WHERE categoryId = 1");

        reset();

        assertEquals(catalog, DbClient.queryRows(CATALOG_SQL), "products should match the DDL exactly");
        assertEquals(categories, DbClient.queryRows(CATEGORIES_SQL), "categories should match the DDL exactly");
    }

    @Test
    void productIdsAreStableAcrossResets() throws Exception {
        // Carts in the browser session store product ids, so a reset must not renumber skis.
        reset();
        assertEquals(catalog, DbClient.queryRows(CATALOG_SQL));
    }

    @Test
    void resetRestoresInventory() throws Exception {
        DbClient.update("UPDATE productinventory SET quantity = 999");
        reset();
        assertEquals(inventory, DbClient.queryRows(INVENTORY_SQL));
    }

    @Test
    void sampleCustomersCanStillLogInAfterReset() throws Exception {
        DbClient.update("UPDATE customer SET password = 'changed' WHERE userid = 'arnold'");
        reset();
        assertEquals(1, DbClient.queryInt(
                "SELECT COUNT(*) FROM customer WHERE userid = 'arnold' AND password = '304Arnold!'"));
    }

    // ---- The site still works after a reset ---------------------------------

    @Test
    void siteIsUsableAfterReset() throws Exception {
        assertEquals(200, AppClient.get("index.jsp").statusCode());
        assertEquals(200, AppClient.get(SkiDataTest.PLACEHOLDER).statusCode());
        String body = AppClient.get("loaddata.jsp").body();
        assertTrue(body.contains("Database connection successful"));
        assertEquals(catalog.size(), LoadDataPageTest.count(body, "Products"));
    }

    @Test
    void resetPageReportsSuccessAndCounts() throws Exception {
        HttpResponse<String> response = AppClient.post("loaddata.jsp", "");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Database reset completed successfully"), response.body());
        assertEquals(catalog.size(), LoadDataPageTest.count(response.body(), "Products"));
        assertEquals(customers, LoadDataPageTest.count(response.body(), "Customers"));
        assertEquals(orders, LoadDataPageTest.count(response.body(), "Orders"));
    }

    @Test
    void resetCanBePressedRepeatedly() throws Exception {
        for (int i = 0; i < 3; i++) reset();
        assertEquals(catalog, DbClient.queryRows(CATALOG_SQL));
        assertEquals(customers, DbClient.queryInt("SELECT COUNT(*) FROM customer"));
    }

    static void reset() throws Exception {
        DbClient.connect().close();   // waits for SQL Server + the initial DDL load
        HttpResponse<String> response = AppClient.post("loaddata.jsp", "");
        assertTrue(response.body().contains("Database reset completed successfully"), response.body());
    }
}
