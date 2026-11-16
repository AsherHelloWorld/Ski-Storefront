package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Step 4: listorder.jsp lists every order and the skis in it (10 marks). */
class ListOrderPageTest {

    static final String PAGE = "listorder.jsp";

    @BeforeEach
    void freshData() throws Exception {
        ResetDatabaseTest.reset();
    }

    @AfterEach
    void cleanUp() throws Exception {
        // Some tests break or change the data on purpose; always leave it clean.
        DbClient.update("IF OBJECT_ID('dbo.ordersummary_hidden', 'U') IS NOT NULL "
                      + "EXEC sp_rename 'dbo.ordersummary_hidden', 'ordersummary'");
        ResetDatabaseTest.reset();
    }

    /** Money the way the page must show it, e.g. $3,449.96 (independent of the page's code). */
    static String money(BigDecimal amount) {
        return String.format("$%,.2f", amount);
    }

    /** The HTML for one order, from <section class="order" id="order-N"> to </section>. */
    static String orderSection(String body, int orderId) {
        Matcher m = Pattern.compile("<section class=\"order\" id=\"order-" + orderId + "\">(.*?)</section>",
                Pattern.DOTALL).matcher(body);
        assertTrue(m.find(), "page should have a section for order " + orderId);
        return m.group(1);
    }

    /** True if the HTML has a table cell (td or th, any attributes) containing exactly this text. */
    static boolean hasCell(String html, String text) {
        return Pattern.compile("<t[dh][^>]*>" + Pattern.quote(text) + "</t[dh]>").matcher(html).find();
    }

    static int countMatches(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    // ---- Basics ---------------------------------------------------------------

    @Test
    void pageLoadsAndIsBranded() throws Exception {
        HttpResponse<String> response = AppClient.get(PAGE);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Ski Store"));
        assertFalse(response.body().contains("YOUR NAME"));
        assertTrue(response.body().contains("<h1>Order List</h1>"));
    }

    @Test
    void usesTheSharedStylesheet() throws Exception {
        assertTrue(AppClient.get(PAGE).body().contains("href=\"css/store.css\""));
    }

    @Test
    void linksBackToTheStore() throws Exception {
        assertTrue(AppClient.get(PAGE).body().contains("href=\"index.jsp\""));
    }

    // ---- Order summaries (2 marks) -----------------------------------------------

    @Test
    void everyOrderHasItsOwnSection() throws Exception {
        String body = AppClient.get(PAGE).body();
        int orders = DbClient.queryInt("SELECT COUNT(*) FROM ordersummary");
        assertEquals(orders, countMatches(body, "<section class=\"order\""));
        assertEquals(orders, countMatches(body, "<table class=\"order-summary\">"));
    }

    @Test
    void summaryShowsIdDateCustomerAndFormattedTotal() throws Exception {
        String body = AppClient.get(PAGE).body();
        try (Connection con = DbClient.connect();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT o.orderId, CONVERT(VARCHAR(19), o.orderDate, 120) AS orderDate, "
                   + "o.customerId, c.firstName, c.lastName, o.totalAmount "
                   + "FROM ordersummary o JOIN customer c ON o.customerId = c.customerId")) {
            int checked = 0;
            while (rs.next()) {
                String section = orderSection(body, rs.getInt("orderId"));
                assertTrue(hasCell(section, String.valueOf(rs.getInt("orderId"))), section);
                assertTrue(section.contains(rs.getString("orderDate")), "date: " + section);
                assertTrue(hasCell(section, String.valueOf(rs.getInt("customerId"))), section);
                assertTrue(section.contains(rs.getString("firstName") + " " + rs.getString("lastName")), section);
                assertTrue(section.contains(money(rs.getBigDecimal("totalAmount"))), "total: " + section);
                checked++;
            }
            assertEquals(5, checked, "sample data has 5 orders");
        }
    }

    @Test
    void summaryHasColumnHeadings() throws Exception {
        String section = orderSection(AppClient.get(PAGE).body(), 1);
        for (String heading : List.of("Order Id", "Order Date", "Customer Id", "Customer Name", "Total Amount")) {
            assertTrue(hasCell(section, heading), "missing heading " + heading);
        }
    }

    @Test
    void ordersAreListedInIdOrder() throws Exception {
        String body = AppClient.get(PAGE).body();
        Matcher m = Pattern.compile("id=\"order-(\\d+)\"").matcher(body);
        List<Integer> ids = new ArrayList<>();
        while (m.find()) ids.add(Integer.parseInt(m.group(1)));
        List<Integer> sorted = new ArrayList<>(ids);
        sorted.sort(null);
        assertEquals(sorted, ids);
    }

    // ---- Items in each order (3 marks) -------------------------------------------

    @Test
    void everyOrderHasAnItemsTable() throws Exception {
        String body = AppClient.get(PAGE).body();
        int orders = DbClient.queryInt("SELECT COUNT(*) FROM ordersummary");
        assertEquals(orders, countMatches(body, "<table class=\"order-items\">"));
    }

    @Test
    void everyItemAppearsUnderItsOrder() throws Exception {
        String body = AppClient.get(PAGE).body();
        try (Connection con = DbClient.connect();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT op.orderId, op.productId, p.productName, op.quantity, op.price "
                   + "FROM orderproduct op JOIN product p ON op.productId = p.productId")) {
            int checked = 0;
            while (rs.next()) {
                String section = orderSection(body, rs.getInt("orderId"));
                assertTrue(hasCell(section, String.valueOf(rs.getInt("productId"))),
                        "product id " + rs.getInt("productId") + " in order " + rs.getInt("orderId"));
                assertTrue(section.contains(rs.getString("productName")), section);
                assertTrue(hasCell(section, String.valueOf(rs.getInt("quantity"))), section);
                assertTrue(section.contains(money(rs.getBigDecimal("price"))), section);
                checked++;
            }
            assertEquals(DbClient.queryInt("SELECT COUNT(*) FROM orderproduct"), checked);
        }
    }

    @Test
    void itemRowCountsMatchTheDatabase() throws Exception {
        String body = AppClient.get(PAGE).body();
        for (int orderId = 1; orderId <= 5; orderId++) {
            String section = orderSection(body, orderId);
            int itemsTable = section.indexOf("<table class=\"order-items\">");
            int rows = countMatches(section.substring(itemsTable), "<tr class=\"item\">");
            assertEquals(DbClient.queryInt("SELECT COUNT(*) FROM orderproduct WHERE orderId = " + orderId),
                    rows, "item rows for order " + orderId);
        }
    }

    @Test
    void itemsTableHasColumnHeadings() throws Exception {
        String section = orderSection(AppClient.get(PAGE).body(), 1);
        for (String heading : List.of("Product Id", "Product Name", "Quantity", "Price")) {
            assertTrue(hasCell(section, heading), "missing heading " + heading);
        }
    }

    @Test
    void itemsStayWithTheirOwnOrder() throws Exception {
        // Order 2 contains only product 5; product 1 must not leak into it from order 1.
        String section = orderSection(AppClient.get(PAGE).body(), 2);
        assertTrue(hasCell(section, "5"));
        assertFalse(section.contains("Atomic Bent 100"));
    }

    // ---- Currency formatting (1 mark) --------------------------------------------

    @Test
    void amountsUseDollarsWithTwoDecimals() throws Exception {
        String body = AppClient.get(PAGE).body();
        assertTrue(body.contains("$3,449.96"), "order 1 total");
        assertTrue(body.contains("$14,949.84"), "order 4 total uses a thousands separator");
        assertTrue(body.contains("$799.99"), "item price");
    }

    @Test
    void noRawUnformattedAmounts() throws Exception {
        String body = AppClient.get(PAGE).body();
        assertFalse(body.contains(">3449.96<"), "totals must be formatted as currency");
        assertFalse(body.contains(">799.99<"), "prices must be formatted as currency");
        assertFalse(body.contains("CA$"), "use $, not CA$");
    }

    // ---- Live data, not hard-coded ---------------------------------------------------

    @Test
    void newOrdersShowUpImmediately() throws Exception {
        DbClient.update("INSERT INTO ordersummary (customerId, orderDate, totalAmount) "
                      + "VALUES (4, '2026-11-01 09:30:00', 1699.98)");
        int id = DbClient.queryInt("SELECT MAX(orderId) FROM ordersummary");
        DbClient.update("INSERT INTO orderproduct (orderId, productId, quantity, price) "
                      + "VALUES (" + id + ", 7, 2, 849.99)");

        String section = orderSection(AppClient.get(PAGE).body(), id);
        assertTrue(section.contains("Darren Doe"));
        assertTrue(section.contains("$1,699.98"));
        assertTrue(section.contains("2026-11-01 09:30:00"));
        assertTrue(section.contains("Zag Harfang 96"));
    }

    @Test
    void orderWithNoItemsStillShows() throws Exception {
        DbClient.update("INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (1, GETDATE(), 0)");
        int id = DbClient.queryInt("SELECT MAX(orderId) FROM ordersummary");
        String section = orderSection(AppClient.get(PAGE).body(), id);
        assertTrue(section.contains("$0.00"));
        assertTrue(section.contains("No items in this order"), section);
    }

    @Test
    void noOrdersShowsAFriendlyMessage() throws Exception {
        DbClient.update("DELETE FROM orderproduct");
        DbClient.update("DELETE FROM ordersummary");
        String body = AppClient.get(PAGE).body();
        assertTrue(body.contains("No orders have been placed yet"), body);
        assertEquals(0, countMatches(body, "<section class=\"order\""));
    }

    // ---- Safety -------------------------------------------------------------------

    @Test
    void customerNamesAreHtmlEscaped() throws Exception {
        DbClient.update("UPDATE customer SET firstName = '<script>alert(1)</script>' WHERE customerId = 1");
        String body = AppClient.get(PAGE).body();
        assertFalse(body.contains("<script>alert(1)</script>"), "names must not be rendered as HTML");
        assertTrue(body.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void productNamesAreHtmlEscaped() throws Exception {
        DbClient.update("UPDATE product SET productName = 'Tom & Jerry''s \"Ski\"' WHERE productId = 1");
        String section = orderSection(AppClient.get(PAGE).body(), 1);
        assertTrue(section.contains("Tom &amp; Jerry&#39;s &quot;Ski&quot;"), section);
    }

    @Test
    void missingProductNameShowsBlankNotNull() throws Exception {
        DbClient.update("UPDATE product SET productName = NULL WHERE productId = 1");
        String section = orderSection(AppClient.get(PAGE).body(), 1);
        assertFalse(section.contains(">null<"), "a missing name should not print 'null'");
        assertTrue(hasCell(section, ""), "the name cell should be empty");
    }

    @Test
    void pageDoesNotChangeAnyData() throws Exception {
        List<String> before = DbClient.queryRows("SELECT * FROM ordersummary ORDER BY orderId");
        List<String> itemsBefore = DbClient.queryRows("SELECT * FROM orderproduct ORDER BY orderId, productId");
        AppClient.get(PAGE);
        AppClient.get(PAGE);
        assertEquals(before, DbClient.queryRows("SELECT * FROM ordersummary ORDER BY orderId"));
        assertEquals(itemsBefore, DbClient.queryRows("SELECT * FROM orderproduct ORDER BY orderId, productId"));
    }

    // ---- Errors (try-catch, 1 mark) ----------------------------------------------

    @Test
    void databaseErrorShowsAFriendlyMessage() throws Exception {
        DbClient.update("EXEC sp_rename 'dbo.ordersummary', 'ordersummary_hidden'");
        HttpResponse<String> response = AppClient.get(PAGE);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Unable to load orders"), response.body());
        assertFalse(response.body().contains("SQLServerException"), "no stack traces for users");
        assertFalse(response.body().contains("ordersummary"), "no SQL details for users");
    }

    @Test
    void connectionsAreClosed() throws Exception {
        // Load the page many times; leaked connections would pile up as sessions on the server.
        int before = openSessionsFromApp();
        for (int i = 0; i < 25; i++) AppClient.get(PAGE);
        Thread.sleep(500);
        int after = openSessionsFromApp();
        assertTrue(after <= before + 2, "connections leaked: " + before + " -> " + after);
    }

    /** Sessions on the orders database other than our own (i.e. from the web app). */
    static int openSessionsFromApp() throws Exception {
        return DbClient.queryInt(
                "SELECT COUNT(*) FROM sys.dm_exec_sessions "
              + "WHERE is_user_process = 1 AND database_id = DB_ID('orders') AND session_id <> @@SPID");
    }
}
