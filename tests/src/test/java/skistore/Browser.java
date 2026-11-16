package skistore;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A shopper with their own session: keeps cookies (so the cart persists between
 * requests) and follows redirects, like a real browser. Use one per test.
 */
public final class Browser {

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** GET a page relative to the shop, e.g. "listprod.jsp?productName=bent". */
    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        AppClient.get("index.jsp");   // waits for Tomcat on first use
        return client.send(HttpRequest.newBuilder(URI.create(AppClient.BASE_URL + path))
                .timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** POST a form, e.g. post("order.jsp", Map.of("customerId", "1")). */
    public HttpResponse<String> post(String path, Map<String, String> form) throws IOException, InterruptedException {
        AppClient.get("index.jsp");
        return client.send(HttpRequest.newBuilder(URI.create(AppClient.BASE_URL + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Add a product to this shopper's cart the way listprod.jsp's links do. */
    public HttpResponse<String> addToCart(int id, String name, String price) throws IOException, InterruptedException {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("id", String.valueOf(id));
        q.put("name", name);
        q.put("price", price);
        return get("addcart.jsp?" + encode(q));
    }

    public static String encode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
