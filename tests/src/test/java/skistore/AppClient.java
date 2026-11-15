package skistore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Small HTTP helper for calling the running shop. */
public final class AppClient {

    /** Base URL of the shop, e.g. http://app:8080/shop/ inside Docker. */
    public static final String BASE_URL =
            System.getenv().getOrDefault("APP_URL", "http://localhost/shop/");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile boolean ready = false;

    private AppClient() {}

    /** GET a page relative to the shop, e.g. get("index.jsp"). */
    public static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        waitForTomcat();
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Tomcat can take a while to start; retry for up to ~60 seconds. */
    private static void waitForTomcat() throws InterruptedException {
        if (ready) return;
        HttpRequest ping = HttpRequest.newBuilder(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                CLIENT.send(ping, HttpResponse.BodyHandlers.discarding());
                ready = true;
                return;
            } catch (IOException e) {
                Thread.sleep(2_000);
            }
        }
        throw new IllegalStateException("Shop did not respond at " + BASE_URL + " within 60s");
    }
}
