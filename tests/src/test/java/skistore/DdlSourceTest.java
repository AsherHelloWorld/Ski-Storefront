package skistore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The DDL file is the single source of truth for the catalog: every product and
 * category lives there, and no page in shop/ creates, edits or deletes them.
 * That's what makes Reset Database safe for the site.
 */
class DdlSourceTest {

    /** Project root: /project inside Docker (read-only mount), the repo root when run locally. */
    static final Path ROOT = Path.of(System.getenv().getOrDefault("PROJECT_DIR", ".."));
    static final Path DDL = ROOT.resolve("ddl/SQLServer_orderdb.ddl");
    static final Path SHOP = ROOT.resolve("shop");

    private static String ddl;

    @BeforeAll
    static void loadDdlIntoDatabase() throws Exception {
        ddl = Files.readString(DDL);
        DbClient.connect().close();
        ResetDatabaseTest.reset();
    }

    @Test
    void everyProductInTheDatabaseComesFromTheDdl() throws Exception {
        TreeSet<String> inDdl = new TreeSet<>(quotedFirstValues("INSERT\\s+(?:INTO\\s+)?product\\s*\\("));
        TreeSet<String> inDb = new TreeSet<>(DbClient.queryRows("SELECT productName FROM product"));
        assertEquals(inDdl, inDb);
    }

    @Test
    void everyCategoryInTheDatabaseComesFromTheDdl() throws Exception {
        TreeSet<String> inDdl = new TreeSet<>(quotedFirstValues("INSERT\\s+(?:INTO\\s+)?category\\s*\\("));
        TreeSet<String> inDb = new TreeSet<>(DbClient.queryRows("SELECT categoryName FROM category"));
        assertEquals(inDdl, inDb);
    }

    @Test
    void ddlRecreatesEveryTableFromScratch() {
        // Reset only restores the catalog if the script drops and recreates the tables.
        for (String table : List.of("customer", "ordersummary", "orderproduct", "product", "category",
                                    "productinventory", "incart", "review", "shipment")) {
            assertTrue(Pattern.compile("DROP TABLE IF EXISTS " + table + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(ddl).find(), "DDL should drop " + table);
            assertTrue(Pattern.compile("CREATE TABLE " + table + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(ddl).find(), "DDL should create " + table);
        }
    }

    @Test
    void noPageChangesTheCatalog() throws IOException {
        // Pages may read products, but only the DDL may write them.
        Pattern writesCatalog = Pattern.compile(
                "INSERT\\s+(INTO\\s+)?(product|category)\\b"
              + "|UPDATE\\s+(product|category)\\b"
              + "|DELETE\\s+FROM\\s+(product|category)\\b",
                Pattern.CASE_INSENSITIVE);
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SHOP)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".jsp")).toList()) {
                if (writesCatalog.matcher(Files.readString(file)).find()) {
                    offenders.add(SHOP.relativize(file).toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "these pages modify products/categories: " + offenders);
    }

    @Test
    void everyImageTheDdlReferencesExists() {
        Matcher m = Pattern.compile("productImageURL\\s*=\\s*'([^']+)'").matcher(ddl);
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(Files.isRegularFile(SHOP.resolve(m.group(1))), "missing image file: shop/" + m.group(1));
        }
        assertTrue(found > 0, "DDL should set product image URLs");
    }

    /** First quoted value of every matching INSERT, e.g. the product name. */
    private static List<String> quotedFirstValues(String insertPrefix) {
        Matcher m = Pattern.compile(insertPrefix + "[^)]*\\)\\s*VALUES\\s*\\(\\s*'((?:[^']|'')*)'",
                Pattern.CASE_INSENSITIVE).matcher(ddl);
        List<String> values = new ArrayList<>();
        while (m.find()) values.add(m.group(1).replace("''", "'"));
        assertTrue(!values.isEmpty(), "no rows found for " + insertPrefix);
        return values;
    }
}
