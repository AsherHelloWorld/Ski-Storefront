package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the coverage measurement honest:
 *  - the JaCoCo agent is running inside Tomcat,
 *  - every page in shop/ gets loaded at least once (so it's compiled and appears
 *    in the report instead of silently missing), and none of them crash.
 */
class CoverageSetupTest {

    @Test
    void coverageAgentIsListeningInsideTomcat() throws IOException {
        String host = System.getenv("JACOCO_HOST");
        assumeTrue(host != null, "coverage only runs inside Docker");
        int port = Integer.parseInt(System.getenv().getOrDefault("JACOCO_PORT", "6300"));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5_000);
            assertTrue(socket.isConnected());
        }
    }

    @Test
    void everyPageLoadsWithoutAServerError() throws Exception {
        List<String> pages;
        try (Stream<Path> files = Files.walk(DdlSourceTest.SHOP)) {
            pages = files.filter(p -> p.toString().endsWith(".jsp"))
                         .map(p -> DdlSourceTest.SHOP.relativize(p).toString().replace('\\', '/'))
                         .sorted()
                         .toList();
        }
        assertFalse(pages.isEmpty());
        for (String page : pages) {
            HttpResponse<String> response = AppClient.get(page);
            assertTrue(response.statusCode() < 500,
                    page + " returned " + response.statusCode() + ":\n" + response.body());
        }
        // Afterwards Tomcat has compiled every page, so the coverage report can include them all.
        Path compiled = DdlSourceTest.ROOT.resolve("work/Catalina/localhost/shop/org/apache/jsp");
        if (Files.isDirectory(compiled)) {
            for (String page : pages) {
                String cls = page.replace(".jsp", "_jsp.class");
                assertTrue(Files.exists(compiled.resolve(cls)), "not compiled: " + page);
            }
        }
    }

    @Test
    void jdbcIncludeOutputsNothingOnItsOwn() throws Exception {
        // jdbc.jsp only declares connection helpers; loading it directly must not leak anything.
        HttpResponse<String> response = AppClient.get("jdbc.jsp");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().isBlank(), "jdbc.jsp should render nothing");
    }
}
