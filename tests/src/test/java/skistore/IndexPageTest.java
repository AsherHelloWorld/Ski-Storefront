package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/** Step 1: the site is up and branded as the Ski Store. */
class IndexPageTest {

    @Test
    void homePageLoads() throws Exception {
        HttpResponse<String> response = AppClient.get("index.jsp");
        assertEquals(200, response.statusCode());
    }

    @Test
    void homePageIsBrandedSkiStore() throws Exception {
        String body = AppClient.get("index.jsp").body();
        assertTrue(body.contains("Ski Store"), "page should mention 'Ski Store'");
        assertFalse(body.contains("YOUR NAME"), "starter placeholder text should be gone");
    }

    @Test
    void unknownPageReturns404() throws Exception {
        HttpResponse<String> response = AppClient.get("no-such-page.jsp");
        assertEquals(404, response.statusCode());
    }
}
