package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Bonus (+2): a page header with links to the products, order list and cart on every page. */
class HeaderTest {

    static String header(String body) {
        Matcher m = Pattern.compile("<header class=\"site-header\">(.*?)</header>", Pattern.DOTALL).matcher(body);
        assertTrue(m.find(), "page should have the site header:\n" + body);
        return m.group(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"index.jsp", "listprod.jsp", "listorder.jsp", "showcart.jsp",
                            "checkout.jsp", "order.jsp", "loaddata.jsp"})
    void everyPageHasTheHeaderWithAllLinks(String page) throws Exception {
        String header = header(new Browser().get(page).body());
        assertTrue(header.contains("href=\"index.jsp\""), "home link on " + page);
        assertTrue(header.contains("href=\"listprod.jsp\""), "products link on " + page);
        assertTrue(header.contains("href=\"listorder.jsp\""), "orders link on " + page);
        assertTrue(header.contains("href=\"showcart.jsp\""), "cart link on " + page);
    }

    @ParameterizedTest
    @ValueSource(strings = {"listprod.jsp", "listorder.jsp", "showcart.jsp"})
    void currentPageIsHighlighted(String page) throws Exception {
        String header = header(new Browser().get(page).body());
        assertTrue(header.contains("href=\"" + page + "\" aria-current=\"page\""), header);
        assertEquals(1, ListOrderPageTest.countMatches(header, "aria-current"));
    }

    @Test
    void cartLinkShowsHowManySkisAreInTheCart() throws Exception {
        Browser shopper = new Browser();
        assertTrue(header(shopper.get("index.jsp").body()).contains("Cart (0)"));
        shopper.addToCart(1, "Atomic Bent 100", "799.99");
        shopper.addToCart(1, "Atomic Bent 100", "799.99");
        shopper.addToCart(23, "K2 Juvy", "249.99");
        assertTrue(header(shopper.get("listprod.jsp").body()).contains("Cart (3)"));
    }

    @Test
    void headerShowsTheStoreName() throws Exception {
        assertTrue(header(new Browser().get("listorder.jsp").body()).contains("Ski Store"));
    }
}
