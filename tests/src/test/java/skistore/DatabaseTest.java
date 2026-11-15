package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Step 2: the orders database exists with the course schema and sample data. */
class DatabaseTest {

    /** Every table created by ddl/SQLServer_orderdb.ddl. The schema is fixed by the course. */
    private static final List<String> TABLES = List.of(
            "customer", "paymentmethod", "ordersummary", "category", "product",
            "orderproduct", "incart", "warehouse", "shipment", "productinventory", "review");

    @Test
    void canConnectToOrdersDatabase() throws Exception {
        try (Connection con = DbClient.connect()) {
            assertEquals("orders", con.getCatalog());
        }
    }

    @Test
    void allCourseTablesExist() throws Exception {
        Set<String> found = new HashSet<>();
        try (Connection con = DbClient.connect();
             ResultSet rs = con.getMetaData().getTables("orders", "dbo", "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                found.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }
        for (String table : TABLES) {
            assertTrue(found.contains(table), "missing table: " + table);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "product", "category"})
    void sampleDataIsLoaded(String table) throws Exception {
        assertTrue(DbClient.queryInt("SELECT COUNT(*) FROM " + table) > 0, table + " should have rows");
    }

    @Test
    void productsHaveNameAndPositivePrice() throws Exception {
        int bad = DbClient.queryInt(
                "SELECT COUNT(*) FROM product "
              + "WHERE productName IS NULL OR LTRIM(RTRIM(productName)) = '' "
              + "OR productPrice IS NULL OR productPrice <= 0");
        assertEquals(0, bad, "every product needs a name and a price above 0");
    }

    @Test
    void productsBelongToAnExistingCategory() throws Exception {
        int orphans = DbClient.queryInt(
                "SELECT COUNT(*) FROM product p "
              + "LEFT JOIN category c ON p.categoryId = c.categoryId "
              + "WHERE c.categoryId IS NULL");
        assertEquals(0, orphans, "every product should have a valid category");
    }

    @Test
    void customersHaveLoginCredentials() throws Exception {
        // order.jsp (and the password bonus) rely on these columns.
        int missing = DbClient.queryInt(
                "SELECT COUNT(*) FROM customer WHERE userid IS NULL OR password IS NULL");
        assertEquals(0, missing, "every customer should have a userid and password");
    }

    @Test
    void ordersummaryIdIsGeneratedByTheDatabase() throws Exception {
        // order.jsp relies on RETURN_GENERATED_KEYS, so orderId must be an identity column.
        try (Connection con = DbClient.connect()) {
            DatabaseMetaData meta = con.getMetaData();
            try (ResultSet rs = meta.getColumns("orders", "dbo", "ordersummary", "orderId")) {
                assertTrue(rs.next(), "ordersummary.orderId should exist");
                assertEquals("YES", rs.getString("IS_AUTOINCREMENT"));
            }
        }
    }
}
