package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/** The course-provided cart pages (addcart.jsp, showcart.jsp) work with our skis. */
class CartPageTest {

    @Test
    void emptyCartSaysSo() throws Exception {
        String body = new Browser().get("showcart.jsp").body();
        assertTrue(body.contains("Your shopping cart is empty"), body);
        assertFalse(body.contains("checkout.jsp"), "no checkout link for an empty cart");
    }

    @Test
    void addingASkiRedirectsToTheCart() throws Exception {
        HttpResponse<String> response = new Browser().addToCart(1, "Atomic Bent 100", "799.99");
        assertEquals(200, response.statusCode());
        assertTrue(response.uri().getPath().endsWith("showcart.jsp"));
        assertTrue(response.body().contains("Atomic Bent 100"));
        assertTrue(response.body().contains("checkout.jsp"), "checkout link once the cart has items");
    }

    @Test
    void addingTheSameSkiTwiceIncreasesTheQuantity() throws Exception {
        Browser shopper = new Browser();
        shopper.addToCart(1, "Atomic Bent 100", "799.99");
        String body = shopper.addToCart(1, "Atomic Bent 100", "799.99").body();
        assertTrue(body.contains("$1,599.98"), "2 x $799.99 subtotal: " + body);
    }

    @Test
    void totalAddsUpEveryLine() throws Exception {
        Browser shopper = new Browser();
        shopper.addToCart(1, "Atomic Bent 100", "799.99");
        shopper.addToCart(23, "K2 Juvy", "249.99");
        String body = shopper.addToCart(23, "K2 Juvy", "249.99").body();
        assertTrue(body.contains("$1,299.97"), "799.99 + 2 x 249.99: " + body);
    }

    @Test
    void cartsBelongToOneShopper() throws Exception {
        new Browser().addToCart(1, "Atomic Bent 100", "799.99");
        assertTrue(new Browser().get("showcart.jsp").body().contains("Your shopping cart is empty"));
    }

    @Test
    void missingProductDetailsAreRejected() throws Exception {
        assertEquals(400, new Browser().get("addcart.jsp?id=1").statusCode());
        assertEquals(400, new Browser().get("addcart.jsp?name=x&price=1").statusCode());
    }

    @Test
    void invalidPriceIsReportedNotCrashing() throws Exception {
        String body = new Browser().addToCart(1, "Atomic Bent 100", "abc").body();
        assertTrue(body.contains("Invalid price"), body);
    }

    @Test
    void cartNamesAreHtmlEscaped() throws Exception {
        String body = new Browser().addToCart(1, "<i>Ski</i>", "1.00").body();
        assertFalse(body.contains("<i>Ski</i>"));
        assertTrue(body.contains("&lt;i&gt;Ski&lt;/i&gt;"));
    }
}
