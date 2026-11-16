package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End to end, the way a shopper clicks through the store: only links and forms from the pages. */
class PurchaseFlowTest {

    @BeforeEach
    @AfterEach
    void freshData() throws Exception {
        ResetDatabaseTest.reset();
    }

    /** Follow the first link on the page whose href matches the regex. */
    static HttpResponse<String> click(Browser shopper, String body, String hrefRegex) throws Exception {
        Matcher m = Pattern.compile("href=\"(" + hrefRegex + ")\"").matcher(body);
        assertTrue(m.find(), "no link matching " + hrefRegex + " in:\n" + body);
        return shopper.get(ListProdPageTest.unescape(m.group(1)));
    }

    @Test
    void searchAddToCartCheckOutAndSeeTheOrder() throws Exception {
        Browser shopper = new Browser();

        // Home -> Begin Shopping
        String home = shopper.get("index.jsp").body();
        String products = click(shopper, home, "listprod\\.jsp").body();
        assertEquals(30, ListProdPageTest.listedNames(products).size());

        // Search, then add a ski twice and another once
        String powder = shopper.get("listprod.jsp?productName=Slap").body();
        click(shopper, powder, "addcart\\.jsp\\?[^\"]*");
        click(shopper, powder, "addcart\\.jsp\\?[^\"]*");
        String kids = shopper.get("listprod.jsp?productName=Bantam").body();
        String cart = click(shopper, kids, "addcart\\.jsp\\?[^\"]*").body();
        assertTrue(cart.contains("Zag Slap 112"));
        assertTrue(cart.contains("Armada Bantam"));
        assertTrue(cart.contains("$2,199.97"), "cart total 2 x 949.99 + 299.99:\n" + cart);

        // Cart -> Check Out -> place the order as customer 4
        String checkout = click(shopper, cart, "checkout\\.jsp").body();
        assertTrue(checkout.contains("action=\"order.jsp\""));
        String receipt = shopper.post("order.jsp", Map.of("customerId", "4")).body();
        assertTrue(receipt.contains("Order completed"), receipt);
        assertTrue(receipt.contains("$2,199.97"));
        int orderId = OrderPageTest.reportedOrderId(receipt);

        // The cart is now empty, and the order is on the order list
        assertTrue(OrderPageTest.cartIsEmpty(shopper));
        String orders = click(shopper, home, "listorder\\.jsp").body();
        String section = ListOrderPageTest.orderSection(orders, orderId);
        assertTrue(section.contains("Darren Doe"));
        assertTrue(section.contains("$2,199.97"));
        assertTrue(section.contains("Zag Slap 112"));
        assertTrue(section.contains("Armada Bantam"));
    }
}
