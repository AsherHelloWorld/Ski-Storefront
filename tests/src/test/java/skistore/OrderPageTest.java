package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Step 6: order.jsp validates the customer and cart, saves the order, shows a receipt (20 marks). */
class OrderPageTest {

    static final String PAGE = "order.jsp";
    private int ordersBefore;
    private int linesBefore;

    @BeforeEach
    void freshData() throws Exception {
        ResetDatabaseTest.reset();
        ordersBefore = DbClient.queryInt("SELECT COUNT(*) FROM ordersummary");
        linesBefore = DbClient.queryInt("SELECT COUNT(*) FROM orderproduct");
    }

    @AfterEach
    void cleanUp() throws Exception {
        DbClient.update("IF OBJECT_ID('dbo.orderproduct_hidden', 'U') IS NOT NULL "
                      + "EXEC sp_rename 'dbo.orderproduct_hidden', 'orderproduct'");
        ResetDatabaseTest.reset();
    }

    /** A shopper with two Bent 100s and one K2 Juvy in the cart ($1,849.97). */
    static Browser shopperWithCart() throws Exception {
        Browser shopper = new Browser();
        shopper.addToCart(1, "Atomic Bent 100", "799.99");
        shopper.addToCart(1, "Atomic Bent 100", "799.99");
        shopper.addToCart(23, "K2 Juvy", "249.99");
        return shopper;
    }

    static HttpResponse<String> placeOrder(Browser shopper, String customerId) throws Exception {
        return shopper.post(PAGE, form(customerId));
    }

    /** The checkout form for a customer id. */
    static Map<String, String> form(String customerId) {
        return Map.of("customerId", customerId);
    }

    void assertNothingSaved() throws Exception {
        assertEquals(ordersBefore, DbClient.queryInt("SELECT COUNT(*) FROM ordersummary"), "no order row");
        assertEquals(linesBefore, DbClient.queryInt("SELECT COUNT(*) FROM orderproduct"), "no item rows");
    }

    static boolean cartIsEmpty(Browser shopper) throws Exception {
        return shopper.get("showcart.jsp").body().contains("Your shopping cart is empty");
    }

    static int reportedOrderId(String body) {
        Matcher m = Pattern.compile("Order reference number:\\s*(?:<[^>]+>)*\\s*(\\d+)").matcher(body);
        assertTrue(m.find(), "receipt should show the order reference number:\n" + body);
        return Integer.parseInt(m.group(1));
    }

    // ---- Basics -----------------------------------------------------------------

    @Test
    void pageIsBranded() throws Exception {
        String body = placeOrder(shopperWithCart(), "1").body();
        assertTrue(body.contains("Ski Store"));
        assertFalse(body.contains("YOUR NAME"));
        assertTrue(body.contains("href=\"css/store.css\""));
    }

    @Test
    void checkoutPageAsksForACustomerId() throws Exception {
        String body = new Browser().get("checkout.jsp").body();
        assertTrue(body.contains("action=\"order.jsp\""), body);
        assertTrue(body.contains("name=\"customerId\""));
        assertTrue(body.contains("Ski Store"));
        assertFalse(body.contains("Grocery"));
    }

    // ---- Customer id validation (3 marks) ---------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "1.5", "1e3", "12abc", "99999999999"})
    void nonNumericCustomerIdIsRejected(String id) throws Exception {
        Browser shopper = shopperWithCart();
        String body = placeOrder(shopper, id).body();
        assertTrue(body.contains("Invalid customer id"), "'" + id + "': " + body);
        assertNothingSaved();
        assertFalse(cartIsEmpty(shopper), "cart is kept so the shopper can try again");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "6", "999"})
    void unknownCustomerIdIsRejected(String id) throws Exception {
        Browser shopper = shopperWithCart();
        String body = placeOrder(shopper, id).body();
        assertTrue(body.contains("Invalid customer id"), "'" + id + "': " + body);
        assertNothingSaved();
        assertFalse(cartIsEmpty(shopper));
    }

    @Test
    void missingCustomerIdIsRejected() throws Exception {
        Browser shopper = shopperWithCart();
        String body = shopper.post(PAGE, Map.of()).body();
        assertTrue(body.contains("Invalid customer id"), body);
        assertNothingSaved();
    }

    @Test
    void customerIdIsEscapedInTheErrorMessage() throws Exception {
        String body = placeOrder(shopperWithCart(), "<b>x</b>").body();
        assertFalse(body.contains("<b>x</b>"));
    }

    @Test
    void surroundingSpacesInTheIdAreIgnored() throws Exception {
        String body = placeOrder(shopperWithCart(), "  2 ").body();
        assertTrue(body.contains("Order completed"), body);
    }

    // ---- Empty cart (1 mark) -------------------------------------------------------

    @Test
    void emptyCartIsRejected() throws Exception {
        String body = placeOrder(new Browser(), "1").body();
        assertTrue(body.contains("Your shopping cart is empty"), body);
        assertNothingSaved();
    }

    @Test
    void emptyCartAfterCheckoutIsRejected() throws Exception {
        Browser shopper = shopperWithCart();
        placeOrder(shopper, "1");
        int afterFirst = DbClient.queryInt("SELECT COUNT(*) FROM ordersummary");
        String body = placeOrder(shopper, "1").body();   // e.g. refreshing the receipt
        assertTrue(body.contains("Your shopping cart is empty"), body);
        assertEquals(afterFirst, DbClient.queryInt("SELECT COUNT(*) FROM ordersummary"), "no duplicate order");
    }

    // ---- Saving the order (3 + 6 + 2 marks) ------------------------------------------

    @Test
    void orderSummaryRowIsSavedWithItsGeneratedId() throws Exception {
        String body = placeOrder(shopperWithCart(), "3").body();
        int id = reportedOrderId(body);
        assertEquals(ordersBefore + 1, DbClient.queryInt("SELECT COUNT(*) FROM ordersummary"));
        assertEquals(DbClient.queryInt("SELECT MAX(orderId) FROM ordersummary"), id, "reported id = generated id");
        assertEquals(3, DbClient.queryInt("SELECT customerId FROM ordersummary WHERE orderId = " + id));
    }

    @Test
    void orderDateIsNow() throws Exception {
        int id = reportedOrderId(placeOrder(shopperWithCart(), "1").body());
        int secondsAgo = DbClient.queryInt(
                "SELECT ABS(DATEDIFF(SECOND, orderDate, GETDATE())) FROM ordersummary WHERE orderId = " + id);
        assertTrue(secondsAgo < 120, "order date should be the time of ordering, was " + secondsAgo + "s off");
    }

    @Test
    void everyCartItemIsSavedWithQuantityAndPrice() throws Exception {
        int id = reportedOrderId(placeOrder(shopperWithCart(), "1").body());
        assertEquals(List.of("1|2|799.99", "23|1|249.99"), DbClient.queryRows(
                "SELECT productId, quantity, price FROM orderproduct WHERE orderId = " + id + " ORDER BY productId"));
    }

    @Test
    void totalAmountIsUpdated() throws Exception {
        int id = reportedOrderId(placeOrder(shopperWithCart(), "1").body());
        assertEquals(List.of("1849.97"), DbClient.queryRows(
                "SELECT totalAmount FROM ordersummary WHERE orderId = " + id));
        assertEquals(0, DbClient.queryInt(
                "SELECT COUNT(*) FROM ordersummary o WHERE orderId = " + id + " AND totalAmount <> "
              + "(SELECT SUM(quantity * price) FROM orderproduct op WHERE op.orderId = o.orderId)"));
    }

    @Test
    void shippingAddressComesFromTheCustomer() throws Exception {
        int id = reportedOrderId(placeOrder(shopperWithCart(), "4").body());
        assertEquals(List.of("444 Dover Lane|Kelowna|BC|V1V 2X9|Canada"), DbClient.queryRows(
                "SELECT shiptoAddress, shiptoCity, shiptoState, shiptoPostalCode, shiptoCountry "
              + "FROM ordersummary WHERE orderId = " + id));
    }

    @Test
    void pricesComeFromTheDatabaseNotTheLink() throws Exception {
        // addcart.jsp trusts the price in the URL; order.jsp must not.
        Browser shopper = new Browser();
        shopper.addToCart(1, "Atomic Bent 100", "0.01");
        int id = reportedOrderId(placeOrder(shopper, "1").body());
        assertEquals(List.of("799.99"), DbClient.queryRows("SELECT price FROM orderproduct WHERE orderId = " + id));
        assertEquals(List.of("799.99"), DbClient.queryRows("SELECT totalAmount FROM ordersummary WHERE orderId = " + id));
    }

    @Test
    void consecutiveOrdersGetNewIds() throws Exception {
        int first = reportedOrderId(placeOrder(shopperWithCart(), "1").body());
        int second = reportedOrderId(placeOrder(shopperWithCart(), "2").body());
        assertEquals(first + 1, second);
    }

    @Test
    void newOrderAppearsOnTheOrderList() throws Exception {
        int id = reportedOrderId(placeOrder(shopperWithCart(), "5").body());
        String section = ListOrderPageTest.orderSection(AppClient.get("listorder.jsp").body(), id);
        assertTrue(section.contains("Elizabeth Elliott"));
        assertTrue(section.contains("$1,849.97"));
        assertTrue(section.contains("K2 Juvy"));
    }

    // ---- Receipt (2 marks) ----------------------------------------------------------

    @Test
    void receiptShowsEveryItemAndTheTotal() throws Exception {
        String body = placeOrder(shopperWithCart(), "1").body();
        assertTrue(body.contains("Order completed"), body);
        assertTrue(body.contains("Atomic Bent 100"));
        assertTrue(body.contains("K2 Juvy"));
        assertTrue(ListOrderPageTest.hasCell(body, "2"), "quantity 2");
        assertTrue(body.contains("$1,599.98"), "Bent subtotal 2 x $799.99");
        assertTrue(body.contains("$249.99"));
        assertTrue(body.contains("$1,849.97"), "order total");
        assertEquals(2, ListOrderPageTest.countMatches(body, "<tr class=\"item\">"));
    }

    @Test
    void receiptNamesTheCustomer() throws Exception {
        String body = placeOrder(shopperWithCart(), "2").body();
        assertTrue(body.contains("Bobby Brown"), body);
        assertTrue(body.contains("Customer Id: 2") || ListOrderPageTest.hasCell(body, "2"));
    }

    @Test
    void receiptEscapesNamesFromTheDatabase() throws Exception {
        DbClient.update("UPDATE customer SET firstName = '<i>Arnold</i>' WHERE customerId = 1");
        DbClient.update("UPDATE product SET productName = '<b>Bent</b>' WHERE productId = 1");
        String body = placeOrder(shopperWithCart(), "1").body();
        assertFalse(body.contains("<i>Arnold</i>"));
        assertFalse(body.contains("<b>Bent</b>"));
        assertTrue(body.contains("&lt;b&gt;Bent&lt;/b&gt;"));
    }

    // ---- Clearing the cart (1 mark) ----------------------------------------------------

    @Test
    void cartIsClearedAfterASuccessfulOrder() throws Exception {
        Browser shopper = shopperWithCart();
        placeOrder(shopper, "1");
        assertTrue(cartIsEmpty(shopper));
    }

    @Test
    void cartIsKeptWhenTheOrderFails() throws Exception {
        Browser shopper = shopperWithCart();
        placeOrder(shopper, "999");
        assertFalse(cartIsEmpty(shopper));
    }

    // ---- All-or-nothing ----------------------------------------------------------------

    @Test
    void unknownProductRollsBackTheWholeOrder() throws Exception {
        Browser shopper = new Browser();
        shopper.addToCart(1, "Atomic Bent 100", "799.99");
        shopper.addToCart(9999, "Ghost Ski", "1.00");
        String body = placeOrder(shopper, "1").body();
        assertTrue(body.contains("could not be placed"), body);
        assertNothingSaved();
        assertFalse(cartIsEmpty(shopper));
    }

    @Test
    void nonNumericProductIdRollsBack() throws Exception {
        Browser shopper = new Browser();
        shopper.addToCart(1, "Atomic Bent 100", "799.99");
        shopper.get("addcart.jsp?id=abc&name=Bad&price=1.00");
        String body = placeOrder(shopper, "1").body();
        assertTrue(body.contains("could not be placed"), body);
        assertNothingSaved();
    }

    @Test
    void databaseFailureMidOrderRollsBack() throws Exception {
        // The summary insert works but the item insert fails: nothing may be left behind.
        DbClient.update("EXEC sp_rename 'dbo.orderproduct', 'orderproduct_hidden'");
        Browser shopper = shopperWithCart();
        String body = placeOrder(shopper, "1").body();
        assertTrue(body.contains("could not be placed"), body);
        assertFalse(body.contains("SQLServerException"));
        assertEquals(ordersBefore, DbClient.queryInt("SELECT COUNT(*) FROM ordersummary"), "summary rolled back");
        assertFalse(cartIsEmpty(shopper));
    }

    @Test
    void connectionsAreClosed() throws Exception {
        int before = ListOrderPageTest.openSessionsFromApp();
        for (int i = 0; i < 10; i++) placeOrder(shopperWithCart(), "1");
        for (int i = 0; i < 10; i++) placeOrder(shopperWithCart(), "999");
        Thread.sleep(500);
        int after = ListOrderPageTest.openSessionsFromApp();
        assertTrue(after <= before + 2, "connections leaked: " + before + " -> " + after);
    }
}
