package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Bonus (+5): remove items and change quantities in the cart (updatecart.jsp).
 * Bonus (+3): improved cart page formatting (showcart.jsp).
 */
class CartBonusTest {

    /** Cart with Bent 100 x2 and K2 Juvy x1 ($1,849.97). */
    static Browser shopper() throws Exception {
        Browser b = new Browser();
        b.addToCart(1, "Atomic Bent 100", "799.99");
        b.addToCart(1, "Atomic Bent 100", "799.99");
        b.addToCart(23, "K2 Juvy", "249.99");
        return b;
    }

    static HttpResponse<String> update(Browser b, String id, String quantity) throws Exception {
        return b.post("updatecart.jsp", Map.of("action", "update", "id", id, "quantity", quantity));
    }

    static HttpResponse<String> remove(Browser b, String id) throws Exception {
        return b.post("updatecart.jsp", Map.of("action", "remove", "id", id));
    }

    /** The cart row for a product, or null if it isn't in the cart. */
    static String row(String body, int productId) {
        Matcher m = Pattern.compile("<tr class=\"item\" id=\"cart-item-" + productId + "\">(.*?)</tr>",
                Pattern.DOTALL).matcher(body);
        return m.find() ? m.group(1) : null;
    }

    static int quantityShown(String body, int productId) {
        Matcher m = Pattern.compile("name=\"quantity\" value=\"(\\d+)\"").matcher(row(body, productId));
        assertTrue(m.find(), "quantity box for product " + productId);
        return Integer.parseInt(m.group(1));
    }

    // ---- Changing quantities --------------------------------------------------------

    @Test
    void everyLineHasAQuantityBoxAndButtons() throws Exception {
        String body = shopper().get("showcart.jsp").body();
        for (int id : List.of(1, 23)) {
            String row = row(body, id);
            assertTrue(row.contains("action=\"updatecart.jsp\""), row);
            assertTrue(row.contains("type=\"number\" name=\"quantity\""), row);
            assertTrue(row.contains("value=\"update\""), "update button");
            assertTrue(row.contains("value=\"remove\""), "remove button");
            assertTrue(row.contains("method=\"post\""), "changes use POST");
        }
        assertEquals(2, quantityShown(body, 1));
        assertEquals(1, quantityShown(body, 23));
    }

    @Test
    void changingAQuantityUpdatesSubtotalTotalAndHeader() throws Exception {
        Browser b = shopper();
        HttpResponse<String> response = update(b, "23", "3");
        assertTrue(response.uri().getPath().endsWith("showcart.jsp"), "redirects back to the cart");
        String body = response.body();
        assertEquals(3, quantityShown(body, 23));
        assertTrue(row(body, 23).contains("$749.97"), "3 x $249.99");
        assertTrue(body.contains("$2,349.95"), "new total 1,599.98 + 749.97");
        assertTrue(HeaderTest.header(body).contains("Cart (5)"));
    }

    @Test
    void quantityCanGoDown() throws Exception {
        Browser b = shopper();
        String body = update(b, "1", "1").body();
        assertEquals(1, quantityShown(body, 1));
        assertTrue(body.contains("$1,049.98"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-2"})
    void zeroOrLessRemovesTheLine(String qty) throws Exception {
        String body = update(shopper(), "1", qty).body();
        assertEquals(null, row(body, 1));
        assertTrue(row(body, 23) != null, "other lines stay");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "1.5", "100", "99999999999"})
    void invalidQuantitiesAreRejectedWithAMessage(String qty) throws Exception {
        Browser b = shopper();
        String body = update(b, "1", qty).body();
        assertTrue(body.contains("Please enter a quantity from 0 to 99"), "'" + qty + "': " + body);
        assertEquals(2, quantityShown(body, 1), "quantity unchanged");
    }

    @Test
    void messagesOnlyShowOnce() throws Exception {
        Browser b = shopper();
        update(b, "1", "abc");
        assertFalse(b.get("showcart.jsp").body().contains("Please enter a quantity"));
    }

    @Test
    void updatedQuantitiesAreWhatGetsOrdered() throws Exception {
        ResetDatabaseTest.reset();
        Browser b = shopper();
        update(b, "1", "3");
        remove(b, "23");
        String receipt = b.post("order.jsp", OrderPageTest.form("1")).body();
        int orderId = OrderPageTest.reportedOrderId(receipt);
        assertEquals(List.of("1|3|799.99"), DbClient.queryRows(
                "SELECT productId, quantity, price FROM orderproduct WHERE orderId = " + orderId));
        ResetDatabaseTest.reset();
    }

    // ---- Removing -----------------------------------------------------------------

    @Test
    void removingALineKeepsTheOthers() throws Exception {
        String body = remove(shopper(), "1").body();
        assertEquals(null, row(body, 1));
        assertTrue(row(body, 23).contains("K2 Juvy"));
        assertTrue(body.contains("$249.99"));
        assertTrue(HeaderTest.header(body).contains("Cart (1)"));
    }

    @Test
    void removingTheLastLineEmptiesTheCart() throws Exception {
        Browser b = shopper();
        remove(b, "1");
        String body = remove(b, "23").body();
        assertTrue(body.contains("Your shopping cart is empty"), body);
    }

    @Test
    void emptyCartButtonClearsEverything() throws Exception {
        Browser b = shopper();
        assertTrue(b.get("showcart.jsp").body().contains("value=\"clear\""));
        String body = b.post("updatecart.jsp", Map.of("action", "clear")).body();
        assertTrue(body.contains("Your shopping cart is empty"), body);
    }

    @Test
    void unknownItemGivesAMessageAndChangesNothing() throws Exception {
        Browser b = shopper();
        String body = remove(b, "999").body();
        assertTrue(body.contains("That ski is no longer in your cart"), body);
        assertEquals(2, quantityShown(body, 1));
        body = b.post("updatecart.jsp", Map.of("action", "update", "quantity", "2")).body();
        assertTrue(body.contains("That ski is no longer in your cart"), "missing id");
    }

    @Test
    void unknownActionChangesNothing() throws Exception {
        Browser b = shopper();
        String body = b.post("updatecart.jsp", Map.of("action", "explode", "id", "1")).body();
        assertEquals(2, quantityShown(body, 1));
    }

    @Test
    void getRequestsNeverChangeTheCart() throws Exception {
        // A link preview or prefetch must not empty someone's cart.
        Browser b = shopper();
        HttpResponse<String> response = b.get("updatecart.jsp?action=clear");
        assertTrue(response.uri().getPath().endsWith("showcart.jsp"));
        assertEquals(2, quantityShown(response.body(), 1));
    }

    @Test
    void updatingAnEmptyCartIsHarmless() throws Exception {
        String body = new Browser().post("updatecart.jsp", Map.of("action", "remove", "id", "1")).body();
        assertTrue(body.contains("Your shopping cart is empty"), body);
    }

    @Test
    void injectedIdsAreEscaped() throws Exception {
        String body = remove(shopper(), "<script>x</script>").body();
        assertFalse(body.contains("<script>x</script>"));
    }

    // ---- Formatting -----------------------------------------------------------------

    @Test
    void cartIsAFormattedTable() throws Exception {
        String body = shopper().get("showcart.jsp").body();
        assertTrue(body.contains("<table class=\"cart\">"));
        for (String heading : List.of("Product", "Price", "Quantity", "Subtotal")) {
            assertTrue(ListOrderPageTest.hasCell(body, heading), "heading " + heading);
        }
        assertTrue(body.contains("href=\"css/store.css\""));
    }

    @Test
    void cartShowsItemCountAndTotal() throws Exception {
        String body = shopper().get("showcart.jsp").body();
        assertTrue(body.contains("3 skis in your cart"), body);
        Matcher total = Pattern.compile("<tr class=\"total\">(.*?)</tr>", Pattern.DOTALL).matcher(body);
        assertTrue(total.find());
        assertTrue(total.group(1).contains("$1,849.97"));
    }

    @Test
    void cartHasCheckoutAndContinueShoppingButtons() throws Exception {
        String body = shopper().get("showcart.jsp").body();
        assertTrue(body.contains("<a class=\"button\" href=\"checkout.jsp\">"), body);
        assertTrue(body.contains("href=\"listprod.jsp\""));
    }

    @Test
    void emptyCartLinksBackToShopping() throws Exception {
        String body = new Browser().get("showcart.jsp").body();
        assertTrue(body.contains("Your shopping cart is empty"));
        assertTrue(body.contains("<a class=\"button\" href=\"listprod.jsp\">"), body);
        assertFalse(body.contains("checkout.jsp"));
    }

    @Test
    void singleItemUsesSingular() throws Exception {
        Browser b = new Browser();
        b.addToCart(23, "K2 Juvy", "249.99");
        assertTrue(b.get("showcart.jsp").body().contains("1 ski in your cart"));
    }
}
