package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Bonus (+3): product page formatting and a category filter on listprod.jsp. */
class ListProdBonusTest {

    @BeforeEach
    @AfterEach
    void freshData() throws Exception {
        ResetDatabaseTest.reset();
    }

    static String page(String productName, String categoryId) throws Exception {
        return AppClient.get("listprod.jsp?productName=" + URLEncoder.encode(productName, StandardCharsets.UTF_8)
                + "&categoryId=" + URLEncoder.encode(categoryId, StandardCharsets.UTF_8)).body();
    }

    static TreeSet<String> namesIn(String where) throws Exception {
        return new TreeSet<>(DbClient.queryRows("SELECT productName FROM product p WHERE " + where));
    }

    static String select(String body) {
        Matcher m = Pattern.compile("<select name=\"categoryId\"[^>]*>(.*?)</select>", Pattern.DOTALL).matcher(body);
        assertTrue(m.find(), "page should have a category dropdown");
        return m.group(1);
    }

    // ---- Category filter ----------------------------------------------------------

    @Test
    void dropdownListsAllCategoriesFromTheDatabase() throws Exception {
        String options = select(AppClient.get("listprod.jsp").body());
        assertTrue(options.contains("<option value=\"\">All categories</option>"), options);
        for (String row : DbClient.queryRows("SELECT categoryId, categoryName FROM category")) {
            String[] parts = row.split("\\|");
            assertTrue(options.contains("<option value=\"" + parts[0] + "\">" + parts[1] + "</option>"),
                    "missing option " + row + " in " + options);
        }
    }

    @Test
    void dropdownIsPartOfTheSearchForm() throws Exception {
        String body = AppClient.get("listprod.jsp").body();
        int form = body.indexOf("<form method=\"get\" action=\"listprod.jsp\"");
        int end = body.indexOf("</form>", form);
        assertTrue(form >= 0 && body.substring(form, end).contains("name=\"categoryId\""));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void filteringByCategoryShowsOnlyThatCategory(int categoryId) throws Exception {
        assertEquals(namesIn("categoryId = " + categoryId),
                new TreeSet<>(ListProdPageTest.listedNames(page("", String.valueOf(categoryId)))));
    }

    @Test
    void categoryAndNameCombine() throws Exception {
        // All-Mountain (1) skis with "Atomic" in the name.
        assertEquals(namesIn("categoryId = 1 AND productName LIKE '%Atomic%'"),
                new TreeSet<>(ListProdPageTest.listedNames(page("Atomic", "1"))));
        assertEquals(2, ListProdPageTest.listedNames(page("Atomic", "1")).size());
    }

    /** The search form's HTML. */
    static String searchForm(String body) {
        Matcher m = Pattern.compile("<form method=\"get\" action=\"listprod\\.jsp\".*?</form>", Pattern.DOTALL)
                .matcher(body);
        assertTrue(m.find(), "search form");
        return m.group();
    }

    @Test
    void choosingACategorySubmitsTheFormStraightAway() throws Exception {
        String form = searchForm(AppClient.get("listprod.jsp").body());
        assertTrue(form.contains("<select name=\"categoryId\" aria-label=\"Category\" onchange=\"this.form.submit()\">"),
                form);
        assertTrue(form.contains("type=\"submit\""), "Submit button stays for the text box and no-JavaScript browsers");
    }

    @Test
    void resetClearsTheSearchAndCategoryAndShowsEverySki() throws Exception {
        String form = searchForm(page("Bent", "2"));
        // A plain <input type="reset"> would only restore the submitted values, i.e. do nothing.
        assertFalse(form.contains("type=\"reset\""), "browser reset does not clear a submitted search");
        Matcher reset = Pattern.compile("<a class=\"button secondary\" href=\"([^\"]*)\">Reset</a>").matcher(form);
        assertTrue(reset.find(), "Reset button in the search form:\n" + form);
        assertEquals("listprod.jsp", reset.group(1), "reset loads the page with no search and no category");

        String body = AppClient.get(reset.group(1)).body();
        assertEquals(30, ListProdPageTest.listedNames(body).size());
        assertTrue(body.contains("value=\"\""), "search box is empty");
        assertFalse(select(body).contains("selected"), "no category selected");
    }

    @Test
    void chosenCategoryStaysSelected() throws Exception {
        assertTrue(select(page("", "2")).contains("<option value=\"2\" selected>Powder</option>"));
    }

    @Test
    void headingNamesTheCategory() throws Exception {
        assertTrue(page("", "2").contains("Powder skis"), "heading should name the category");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1.5", "-3"})
    void invalidOrBlankCategoryShowsAllSkis(String categoryId) throws Exception {
        assertEquals(30, ListProdPageTest.listedNames(page("", categoryId)).size());
    }

    @Test
    void unknownCategoryShowsNoSkis() throws Exception {
        String body = page("", "999");
        assertTrue(ListProdPageTest.listedNames(body).isEmpty());
        assertTrue(body.contains("No skis match"));
    }

    @Test
    void categoryNamesAreEscapedInTheDropdown() throws Exception {
        DbClient.update("UPDATE category SET categoryName = '<Park & Pipe>' WHERE categoryId = 4");
        String options = select(AppClient.get("listprod.jsp").body());
        assertTrue(options.contains("&lt;Park &amp; Pipe&gt;"), options);
    }

    @Test
    void pageSurvivesWhenCategoriesCannotLoad() throws Exception {
        DbClient.update("EXEC sp_rename 'dbo.category', 'category_hidden'");
        try {
            String body = AppClient.get("listprod.jsp").body();
            assertEquals("<option value=\"\">All categories</option>", select(body).trim(),
                    "dropdown falls back to just 'All'");
            assertTrue(body.contains("Unable to load products"), body);
        } finally {
            DbClient.update("EXEC sp_rename 'dbo.category_hidden', 'category'");
        }
    }

    // ---- Formatting -------------------------------------------------------------------

    @Test
    void everySkiShowsItsImage() throws Exception {
        String body = AppClient.get("listprod.jsp").body();
        int images = ListOrderPageTest.countMatches(body, "<img src=\"img/placeholder\\.svg\" alt=\"[^\"]+\"");
        assertEquals(30, images);
    }

    @Test
    void everySkiShowsItsSpecs() throws Exception {
        String body = AppClient.get("listprod.jsp?productName=Mantra").body();
        assertTrue(body.contains("Lengths: 170/177/184/191 cm"), body);
        assertTrue(body.contains("Waist: 102 mm"));
    }

    @Test
    void specsAreHtmlEscaped() throws Exception {
        DbClient.update("UPDATE product SET productDesc = 'Lengths: 1 cm | Waist: 1 mm <script>x</script>' "
                      + "WHERE productId = 1");
        String body = AppClient.get("listprod.jsp").body();
        assertFalse(body.contains("<script>x</script>"));
    }

    @Test
    void missingImageOrSpecsDoNotBreakTheRow() throws Exception {
        DbClient.update("UPDATE product SET productImageURL = NULL, productDesc = NULL WHERE productId = 1");
        String body = AppClient.get("listprod.jsp").body();
        assertEquals(30, ListProdPageTest.listedNames(body).size());
        assertFalse(body.contains(">null<"));
        assertFalse(body.contains("src=\"null\""));
    }

    @Test
    void resultCountIsShown() throws Exception {
        assertTrue(AppClient.get("listprod.jsp").body().contains("30 skis"));
        assertTrue(page("Bent", "").contains("3 skis"));
        assertTrue(page("Mantra", "").contains("1 ski<"), "singular for one result");
    }

    @Test
    void addToCartIsAButtonStyledLink() throws Exception {
        String body = AppClient.get("listprod.jsp").body();
        assertEquals(30, ListOrderPageTest.countMatches(body, "<a class=\"add button\" href=\"addcart\\.jsp"));
    }

    @Test
    void cartLinksStillWorkWithAFilter() throws Exception {
        List<java.util.Map<String, String>> links = ListProdPageTest.cartLinks(page("", "6"));
        assertEquals(4, links.size(), "4 kids skis");
    }
}
