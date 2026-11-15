package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Step 3: the sample data is skis, and it is internally consistent. */
class SkiDataTest {

    static final Set<String> SKI_CATEGORIES = Set.of(
            "All-Mountain", "Powder", "Carving", "Park/Freestyle", "Touring", "Kids");

    static final String PLACEHOLDER = "img/placeholder.svg";

    /** Load the current DDL first, so these checks never run against stale data. */
    @BeforeAll
    static void resetToDdlData() throws Exception {
        DbClient.connect().close();
        HttpResponse<String> response = AppClient.post("loaddata.jsp", "");
        assertTrue(response.body().contains("Database reset completed successfully"), response.body());
    }

    // ---- Categories -------------------------------------------------------

    @Test
    void categoriesAreExactlyTheSkiTypes() throws Exception {
        Set<String> found = new HashSet<>();
        try (Connection con = DbClient.connect();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT categoryName FROM category")) {
            while (rs.next()) found.add(rs.getString(1));
        }
        assertEquals(SKI_CATEGORIES, found);
    }

    @Test
    void everyCategoryHasAtLeastOneSki() throws Exception {
        int empty = DbClient.queryInt(
                "SELECT COUNT(*) FROM category c "
              + "WHERE NOT EXISTS (SELECT 1 FROM product p WHERE p.categoryId = c.categoryId)");
        assertEquals(0, empty, "every category should have at least one ski");
    }

    // ---- Products ---------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"Atomic", "Zag", "Icelantic", "Armada", "K2", "Blizzard"})
    void requestedBrandsHaveSeveralSkis(String brand) throws Exception {
        int count = DbClient.queryInt(
                "SELECT COUNT(*) FROM product WHERE productName LIKE '" + brand + " %'");
        assertTrue(count >= 2, brand + " should have at least 2 skis, has " + count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Chai", "Tofu", "Ikura", "Pavlova", "Chang", "Sasquatch Ale"})
    void noGroceriesRemain(String grocery) throws Exception {
        assertEquals(0, DbClient.queryInt(
                "SELECT COUNT(*) FROM product WHERE productName = '" + grocery + "'"));
    }

    @Test
    void productNamesAreUnique() throws Exception {
        assertEquals(0, DbClient.queryInt(
                "SELECT COUNT(*) FROM (SELECT productName FROM product "
              + "GROUP BY productName HAVING COUNT(*) > 1) d"));
    }

    @Test
    void everyDescriptionHasSkiSpecs() throws Exception {
        // Specs live in productDesc (no new columns allowed): lengths in cm, waist in mm.
        int missing = DbClient.queryInt(
                "SELECT COUNT(*) FROM product WHERE productDesc IS NULL "
              + "OR productDesc NOT LIKE '%Lengths:%cm%' OR productDesc NOT LIKE '%Waist:%mm%'");
        assertEquals(0, missing, "every ski needs Lengths (cm) and Waist (mm) in its description");
    }

    @Test
    void pricesAreRealisticForSkis() throws Exception {
        assertEquals(0, DbClient.queryInt(
                "SELECT COUNT(*) FROM product WHERE productPrice < 150 OR productPrice > 2000"));
    }

    // ---- Placeholder image ------------------------------------------------

    @Test
    void everyProductUsesThePlaceholderImage() throws Exception {
        assertEquals(0, DbClient.queryInt(
                "SELECT COUNT(*) FROM product WHERE productImageURL IS NULL "
              + "OR productImageURL <> '" + PLACEHOLDER + "'"));
    }

    @Test
    void noGroceryPhotoIsStoredInTheDatabase() throws Exception {
        assertEquals(0, DbClient.queryInt("SELECT COUNT(*) FROM product WHERE productImage IS NOT NULL"));
    }

    @Test
    void placeholderImageIsServed() throws Exception {
        HttpResponse<String> response = AppClient.get(PLACEHOLDER);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("image/svg+xml"));
        assertTrue(response.body().contains("<svg"));
    }

    // ---- Orders -----------------------------------------------------------

    @Test
    void everyOrderHasItems() throws Exception {
        assertEquals(0, DbClient.queryInt(
                "SELECT COUNT(*) FROM ordersummary o "
              + "WHERE NOT EXISTS (SELECT 1 FROM orderproduct op WHERE op.orderId = o.orderId)"));
    }

    @Test
    void orderTotalsEqualTheSumOfTheirItems() throws Exception {
        int wrong = DbClient.queryInt(
                "SELECT COUNT(*) FROM ordersummary o "
              + "WHERE o.totalAmount <> (SELECT SUM(op.quantity * op.price) "
              + "FROM orderproduct op WHERE op.orderId = o.orderId)");
        assertEquals(0, wrong, "ordersummary.totalAmount should equal sum(quantity * price)");
    }

    @Test
    void orderLinePricesMatchProductPrices() throws Exception {
        assertEquals(0, DbClient.queryInt(
                "SELECT COUNT(*) FROM orderproduct op JOIN product p ON op.productId = p.productId "
              + "WHERE op.price <> p.productPrice"));
    }

    @Test
    void orderQuantitiesArePositive() throws Exception {
        assertEquals(0, DbClient.queryInt("SELECT COUNT(*) FROM orderproduct WHERE quantity <= 0"));
    }

    // ---- Inventory --------------------------------------------------------

    @Test
    void inventoryPricesMatchProductPrices() throws Exception {
        assertEquals(0, DbClient.queryInt(
                "SELECT COUNT(*) FROM productinventory pi JOIN product p ON pi.productId = p.productId "
              + "WHERE pi.price <> p.productPrice"));
    }

    @Test
    void oneSkiIsOutOfStock() throws Exception {
        // Kept from the course data; later labs use it.
        assertTrue(DbClient.queryInt("SELECT COUNT(*) FROM productinventory WHERE quantity = 0") >= 1);
    }

    @Test
    void oneOrderCannotBeShipped() throws Exception {
        // Kept from the course data: an order wanting more of an item than is in stock.
        int unshippable = DbClient.queryInt(
                "SELECT COUNT(DISTINCT op.orderId) FROM orderproduct op "
              + "JOIN productinventory pi ON op.productId = pi.productId "
              + "WHERE op.quantity > pi.quantity");
        assertTrue(unshippable >= 1);
    }
}
