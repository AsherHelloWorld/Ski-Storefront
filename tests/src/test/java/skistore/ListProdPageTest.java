package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Step 5: listprod.jsp searches skis by name and links each to addcart.jsp (10 marks). */
class ListProdPageTest {

    static final String PAGE = "listprod.jsp";

    @BeforeEach
    void freshData() throws Exception {
        ResetDatabaseTest.reset();
    }

    @AfterEach
    void cleanUp() throws Exception {
        DbClient.update("IF OBJECT_ID('dbo.product_hidden', 'U') IS NOT NULL "
                      + "EXEC sp_rename 'dbo.product_hidden', 'product'");
        ResetDatabaseTest.reset();
    }

    static String search(String term) throws Exception {
        return AppClient.get(PAGE + "?productName=" + URLEncoder.encode(term, StandardCharsets.UTF_8)).body();
    }

    /** Product names listed on the page, in page order (one <tr class="product"> per product). */
    static List<String> listedNames(String body) {
        Matcher m = Pattern.compile("<tr class=\"product\"[^>]*>.*?<td class=\"name\">(.*?)</td>",
                Pattern.DOTALL).matcher(body);
        List<String> names = new ArrayList<>();
        while (m.find()) names.add(unescape(m.group(1).trim()));
        return names;
    }

    /** Names the database says should match, using the same rule as the assignment. */
    static TreeSet<String> expectedMatches(String term) throws Exception {
        TreeSet<String> names = new TreeSet<>();
        try (Connection con = DbClient.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT productName FROM product WHERE productName LIKE ?")) {
            ps.setString(1, "%" + term + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        return names;
    }

    /** All add-to-cart links on the page, decoded into their query parameters. */
    static List<Map<String, String>> cartLinks(String body) {
        Matcher m = Pattern.compile("href=\"(addcart\\.jsp\\?[^\"]*)\"").matcher(body);
        List<Map<String, String>> links = new ArrayList<>();
        while (m.find()) {
            String query = unescape(m.group(1)).substring("addcart.jsp?".length());
            Map<String, String> params = new HashMap<>();
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
            links.add(params);
        }
        return links;
    }

    static String unescape(String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                   .replace("&#39;", "'").replace("&amp;", "&");
    }

    // ---- Basics ---------------------------------------------------------------

    @Test
    void pageLoadsAndIsBranded() throws Exception {
        HttpResponse<String> response = AppClient.get(PAGE);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Ski Store"));
        assertFalse(response.body().contains("YOUR NAME"));
        assertTrue(response.body().contains("href=\"css/store.css\""));
    }

    @Test
    void hasASearchFormThatSubmitsToItself() throws Exception {
        String body = AppClient.get(PAGE).body();
        assertTrue(body.contains("<form method=\"get\" action=\"listprod.jsp\""), body);
        assertTrue(body.contains("name=\"productName\""));
    }

    @Test
    void linksToTheCartAndHome() throws Exception {
        String body = AppClient.get(PAGE).body();
        assertTrue(body.contains("href=\"showcart.jsp\""));
        assertTrue(body.contains("href=\"index.jsp\""));
    }

    // ---- Filtering by name (2 marks) ----------------------------------------------

    @Test
    void noSearchListsEveryProduct() throws Exception {
        List<String> names = listedNames(AppClient.get(PAGE).body());
        assertEquals(DbClient.queryInt("SELECT COUNT(*) FROM product"), names.size());
        assertEquals(expectedMatches(""), new TreeSet<>(names));
    }

    @Test
    void blankSearchListsEveryProduct() throws Exception {
        assertEquals(30, listedNames(search("")).size());
        assertEquals(30, listedNames(search("   ")).size(), "spaces only counts as blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bent", "bent", "ARV", "Zag", "Rustler", "9", "Ti"})
    void searchShowsExactlyTheMatchingProducts(String term) throws Exception {
        assertEquals(expectedMatches(term), new TreeSet<>(listedNames(search(term))));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        assertEquals(listedNames(search("BLIZZARD")), listedNames(search("blizzard")));
        assertEquals(5, listedNames(search("blizzard")).size());
    }

    @Test
    void surroundingSpacesAreIgnored() throws Exception {
        assertEquals(listedNames(search("Bent")), listedNames(search("  Bent  ")));
    }

    @Test
    void resultsAreSortedByName() throws Exception {
        List<String> names = listedNames(AppClient.get(PAGE).body());
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        assertEquals(sorted, names);
    }

    @Test
    void noMatchesShowsAFriendlyMessage() throws Exception {
        String body = search("snowboard");
        assertTrue(listedNames(body).isEmpty());
        assertTrue(body.contains("No skis match"), body);
    }

    @Test
    void searchTermIsShownBackToTheShopper() throws Exception {
        String body = search("Bent");
        assertTrue(body.contains("value=\"Bent\""), "search box keeps the term");
        assertTrue(body.contains("&quot;Bent&quot;"), "results heading names the term");
    }

    @Test
    void newProductsAppearWithoutCodeChanges() throws Exception {
        DbClient.update("INSERT INTO product (productName, productPrice, categoryId, productDesc) "
                      + "VALUES ('Faction Prodigy 3', 799.00, 4, 'Lengths: 172 cm | Waist: 98 mm')");
        assertTrue(listedNames(search("Prodigy")).contains("Faction Prodigy 3"));
    }

    // ---- PreparedStatement / injection safety (1 mark) ----------------------------

    @ParameterizedTest
    @ValueSource(strings = {"' OR 1=1 --", "'; DROP TABLE product; --", "Bent' OR 'a'='a"})
    void sqlInjectionIsTreatedAsPlainText(String attack) throws Exception {
        HttpResponse<String> response = AppClient.get(PAGE + "?productName="
                + URLEncoder.encode(attack, StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertTrue(listedNames(response.body()).isEmpty(), "attack text matches no names");
        assertEquals(30, DbClient.queryInt("SELECT COUNT(*) FROM product"), "product table intact");
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "[a-z]"})
    void likeWildcardsAreTreatedAsPlainText(String term) throws Exception {
        // No ski name contains these characters, so a plain-text search finds nothing.
        assertTrue(listedNames(search(term)).isEmpty(), "'" + term + "' should not act as a wildcard");
    }

    @Test
    void searchTermIsHtmlEscaped() throws Exception {
        String body = search("<script>alert(1)</script>");
        assertFalse(body.contains("<script>alert(1)</script>"));
        assertTrue(body.contains("&lt;script&gt;"));
    }

    // ---- Table of products (2 marks) ----------------------------------------------

    @Test
    void productsAreShownInATableWithHeadings() throws Exception {
        String body = AppClient.get(PAGE).body();
        assertTrue(body.contains("<table class=\"products\">"));
        for (String heading : List.of("Product Name", "Category", "Price")) {
            assertTrue(ListOrderPageTest.hasCell(body, heading), "missing heading " + heading);
        }
    }

    @Test
    void pricesAndCategoriesMatchTheDatabase() throws Exception {
        String body = AppClient.get(PAGE).body();
        try (Connection con = DbClient.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT p.productId, p.productPrice, c.categoryName FROM product p "
                   + "JOIN category c ON p.categoryId = c.categoryId");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Matcher row = Pattern.compile("<tr class=\"product\" id=\"product-" + rs.getInt(1)
                        + "\">(.*?)</tr>", Pattern.DOTALL).matcher(body);
                assertTrue(row.find(), "row for product " + rs.getInt(1));
                assertTrue(row.group(1).contains(ListOrderPageTest.money(rs.getBigDecimal(2))), row.group(1));
                assertTrue(row.group(1).contains(rs.getString(3)), row.group(1));
            }
        }
    }

    @Test
    void productNamesAreHtmlEscaped() throws Exception {
        DbClient.update("UPDATE product SET productName = '<b>Bold</b> Ski' WHERE productId = 1");
        String body = AppClient.get(PAGE).body();
        assertFalse(body.contains("<b>Bold</b> Ski"));
        assertTrue(body.contains("&lt;b&gt;Bold&lt;/b&gt; Ski"));
    }

    // ---- Add-to-cart links (3 marks) ----------------------------------------------

    @Test
    void everyProductHasAnAddToCartLink() throws Exception {
        String body = AppClient.get(PAGE).body();
        assertEquals(listedNames(body).size(), cartLinks(body).size());
    }

    @Test
    void cartLinksCarryIdNameAndPriceFromTheDatabase() throws Exception {
        List<Map<String, String>> links = cartLinks(AppClient.get(PAGE).body());
        try (Connection con = DbClient.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT productName, productPrice FROM product WHERE productId = ?")) {
            for (Map<String, String> link : links) {
                ps.setInt(1, Integer.parseInt(link.get("id")));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "link to unknown product " + link);
                    assertEquals(rs.getString(1), link.get("name"));
                    assertEquals(0, rs.getBigDecimal(2).compareTo(new BigDecimal(link.get("price"))), link.toString());
                }
            }
        }
    }

    @Test
    void cartLinksUrlEncodeNames() throws Exception {
        DbClient.update("UPDATE product SET productName = 'Tom & Jerry''s Ski+ 100%' WHERE productId = 1");
        String body = AppClient.get(PAGE).body();
        // Raw link text: spaces, &, ', + and % must be percent-encoded by URLEncoder.
        assertTrue(body.contains("name=Tom+%26+Jerry%27s+Ski%2B+100%25"), body);
        Map<String, String> link = cartLinks(body).stream()
                .filter(l -> l.get("id").equals("1")).findFirst().orElseThrow();
        assertEquals("Tom & Jerry's Ski+ 100%", link.get("name"));
    }

    @Test
    void cartLinkPricesArePlainNumbers() throws Exception {
        // addcart.jsp parses the price, so it must not contain $ or commas.
        for (Map<String, String> link : cartLinks(AppClient.get(PAGE).body())) {
            assertTrue(link.get("price").matches("\\d+\\.\\d{2}"), link.toString());
        }
    }

    @Test
    void followingALinkPutsTheSkiInTheCart() throws Exception {
        Browser shopper = new Browser();
        String body = shopper.get(PAGE + "?productName=Mantra").body();
        Matcher href = Pattern.compile("href=\"(addcart\\.jsp\\?[^\"]*)\"").matcher(body);
        assertTrue(href.find());

        HttpResponse<String> cart = shopper.get(unescape(href.group(1)));
        assertEquals(200, cart.statusCode());
        assertEquals("showcart.jsp", URI.create(cart.uri().toString()).getPath().replaceAll(".*/", ""));
        assertTrue(cart.body().contains("Volkl Mantra 102"), cart.body());
        assertTrue(cart.body().contains("$1,049.99"));
    }

    // ---- Errors, connections, read-only -------------------------------------------

    @Test
    void databaseErrorShowsAFriendlyMessage() throws Exception {
        DbClient.update("EXEC sp_rename 'dbo.product', 'product_hidden'");
        HttpResponse<String> response = AppClient.get(PAGE);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Unable to load products"), response.body());
        assertFalse(response.body().contains("SQLServerException"));
    }

    @Test
    void connectionsAreClosed() throws Exception {
        int before = ListOrderPageTest.openSessionsFromApp();
        for (int i = 0; i < 25; i++) search(i % 2 == 0 ? "Bent" : "");
        Thread.sleep(500);
        int after = ListOrderPageTest.openSessionsFromApp();
        assertTrue(after <= before + 2, "connections leaked: " + before + " -> " + after);
    }

    @Test
    void pageDoesNotChangeAnyData() throws Exception {
        List<String> before = DbClient.queryRows(ResetDatabaseTest.CATALOG_SQL);
        search("Bent");
        AppClient.get(PAGE);
        assertEquals(before, DbClient.queryRows(ResetDatabaseTest.CATALOG_SQL));
    }
}
