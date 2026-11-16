package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * Safety net: the suite resets the database constantly, so it must only ever run
 * against the throwaway test stack (test-app + ski-test-sqlserver on test-network),
 * never the real site (ski-store + ski-sqlserver with the ski-db volume).
 */
class IsolationTest {

    @Test
    void testsTalkToTheTestApp() {
        assertEquals("test-app", URI.create(AppClient.BASE_URL).getHost());
    }

    @Test
    void testsTalkToTheTestDatabaseServer() throws Exception {
        try (Connection con = DbClient.connect();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT CAST(SERVERPROPERTY('MachineName') AS VARCHAR(100))")) {
            rs.next();
            assertEquals("ski-test-sqlserver", rs.getString(1),
                    "tests must never run against the real ski-sqlserver");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"app", "ski-store", "ski-sqlserver"})
    void realSiteIsNotReachableFromTheTestNetwork(String host) {
        assumeTrue(System.getenv("JACOCO_HOST") != null, "only meaningful inside Docker");
        assertThrows(UnknownHostException.class, () -> InetAddress.getByName(host),
                host + " should not be visible to the tests");
    }
}
