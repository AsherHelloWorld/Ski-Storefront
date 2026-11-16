import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Converts JaCoCo's coverage of Tomcat-generated servlet code into coverage of the
 * actual .jsp source lines, using the SMAP files Jasper writes (dumpSmap=true).
 *
 * Why: the generated Java for every JSP contains ~25 lines of Tomcat boilerplate that
 * no request ever runs, so raw JaCoCo numbers punish pages for code nobody wrote.
 * Here a JSP line counts only if it produced executable code, and it is "covered"
 * when every generated line it maps to ran at least once (in any page that uses it,
 * so an included file like jdbc.jsp is combined across pages).
 *
 * Usage (single-file program, no build needed):
 *   java JspCoverage.java <jacoco.xml> <compiled-jsp-dir> <shop-dir> <min%> <report.txt>
 * Exit code 1 if total JSP line coverage is below min%, 2 on setup problems.
 */
public class JspCoverage {

    /** Generated-Java line -> {missed, covered} instruction counts, per generated source file. */
    static Map<String, Map<Integer, int[]>> readJacocoXml(Path xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = f.newDocumentBuilder().parse(xml.toFile());
        Map<String, Map<Integer, int[]>> result = new HashMap<>();
        NodeList files = doc.getElementsByTagName("sourcefile");
        for (int i = 0; i < files.getLength(); i++) {
            Element sf = (Element) files.item(i);
            Map<Integer, int[]> lines = new HashMap<>();
            NodeList ls = sf.getElementsByTagName("line");
            for (int j = 0; j < ls.getLength(); j++) {
                Element l = (Element) ls.item(j);
                lines.put(Integer.parseInt(l.getAttribute("nr")),
                        new int[] {Integer.parseInt(l.getAttribute("mi")), Integer.parseInt(l.getAttribute("ci"))});
            }
            result.put(sf.getAttribute("name"), lines);
        }
        return result;
    }

    /** One JSP line and the generated-Java lines it became. */
    record Mapping(String jspFile, int jspLine, List<Integer> javaLines) {}

    /** Parse the JSP stratum of a JSR-45 SMAP file. */
    static List<Mapping> readSmap(Path smap) throws IOException {
        List<String> lines = Files.readAllLines(smap);
        Map<Integer, String> fileIds = new HashMap<>();
        List<Mapping> out = new ArrayList<>();
        String section = "";
        boolean jspStratum = false;
        int currentFile = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("*S ")) { jspStratum = line.equals("*S JSP"); section = ""; continue; }
            if (line.startsWith("*")) { section = line; continue; }
            if (!jspStratum) continue;
            if (section.equals("*F")) {
                // "+ id name" is followed by the path line; "id name" has no path line.
                boolean hasPath = line.startsWith("+");
                String[] parts = line.replaceFirst("^\\+\\s*", "").split("\\s+", 2);
                String path = hasPath ? lines.get(++i).trim() : parts[1];
                fileIds.put(Integer.parseInt(parts[0]), path);
            } else if (section.equals("*L")) {
                // InputStartLine[#FileId][,RepeatCount]:OutputStartLine[,OutputLineIncrement]
                String[] io = line.split(":");
                String in = io[0], outPart = io[1];
                int repeat = 1;
                if (in.contains(",")) { repeat = Integer.parseInt(in.substring(in.indexOf(',') + 1)); in = in.substring(0, in.indexOf(',')); }
                if (in.contains("#")) { currentFile = Integer.parseInt(in.substring(in.indexOf('#') + 1)); in = in.substring(0, in.indexOf('#')); }
                int inStart = Integer.parseInt(in);
                int outStart, inc = 1;
                if (outPart.contains(",")) {
                    outStart = Integer.parseInt(outPart.substring(0, outPart.indexOf(',')));
                    inc = Integer.parseInt(outPart.substring(outPart.indexOf(',') + 1));
                } else {
                    outStart = Integer.parseInt(outPart);
                }
                for (int r = 0; r < repeat; r++) {
                    List<Integer> javaLines = new ArrayList<>();
                    if (inc == 0) {
                        javaLines.add(outStart);            // many JSP lines written by one statement
                    } else {
                        for (int k = 0; k < inc; k++) javaLines.add(outStart + r * inc + k);
                    }
                    out.add(new Mapping(fileIds.get(currentFile), inStart + r, javaLines));
                }
            }
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("usage: java JspCoverage.java <jacoco.xml> <compiled-dir> <shop-dir> <min%> <report.txt>");
            System.exit(2);
        }
        Path xml = Path.of(args[0]), compiled = Path.of(args[1]), shop = Path.of(args[2]);
        double min = Double.parseDouble(args[3]);
        Path reportFile = Path.of(args[4]);

        Map<String, Map<Integer, int[]>> jacoco = readJacocoXml(xml);

        // jspFile -> jspLine -> state (true = covered so far / false = executable but missed)
        Map<String, TreeMap<Integer, Boolean>> jsp = new TreeMap<>();
        List<String> noSmap = new ArrayList<>();
        for (String javaFile : jacoco.keySet()) {
            Path smap = compiled.resolve(javaFile.replace(".java", ".class.smap"));
            if (!Files.exists(smap)) { noSmap.add(javaFile); continue; }
            Map<Integer, int[]> javaLines = jacoco.get(javaFile);
            for (Mapping m : readSmap(smap)) {
                if (m.jspFile() == null || !Files.exists(shop.resolve(m.jspFile()))) continue;
                boolean executable = false, allCovered = true;
                for (int jl : m.javaLines()) {
                    int[] c = javaLines.get(jl);
                    if (c == null || c[0] + c[1] == 0) continue;
                    executable = true;
                    if (c[1] == 0) allCovered = false;
                }
                if (!executable) continue;
                // Combine across pages: covered anywhere counts as covered.
                jsp.computeIfAbsent(m.jspFile(), k -> new TreeMap<>())
                   .merge(m.jspLine(), allCovered, Boolean::logicalOr);
            }
        }
        if (!noSmap.isEmpty()) {
            System.err.println("No .smap for " + noSmap + " (compiled before dumpSmap was enabled).");
            System.err.println("Clear Tomcat's compiled JSPs and re-run:");
            System.err.println("  docker compose exec app rm -rf /usr/local/tomcat/work/Catalina");
            System.exit(2);
        }

        StringBuilder summary = new StringBuilder();
        StringBuilder detail = new StringBuilder();
        int totalCovered = 0, totalLines = 0;
        summary.append("================ JSP line coverage ================\n");
        for (var e : jsp.entrySet()) {
            int covered = (int) e.getValue().values().stream().filter(b -> b).count();
            int total = e.getValue().size();
            totalCovered += covered;
            totalLines += total;
            summary.append(String.format("  %-28s %6.1f%%  (%d/%d lines)%n", e.getKey(), pct(covered, total), covered, total));
            TreeSet<Integer> missed = new TreeSet<>();
            e.getValue().forEach((line, ok) -> { if (!ok) missed.add(line); });
            if (!missed.isEmpty()) {
                List<String> src = Files.readAllLines(shop.resolve(e.getKey()));
                detail.append("\nNot covered in ").append(e.getKey()).append(":\n");
                for (int line : missed) {
                    String text = line <= src.size() ? src.get(line - 1).strip() : "";
                    detail.append(String.format("  %4d | %s%n", line, text));
                }
            }
        }
        double total = pct(totalCovered, totalLines);
        summary.append(String.format("  %-28s %6.1f%%  (%d/%d lines)%n", "TOTAL", total, totalCovered, totalLines));

        String report = summary + detail.toString();
        Files.writeString(reportFile, report);
        PrintStream out = System.out;
        out.print(report);
        out.println("  Line-by-line: tests/target/coverage/jsp-lines.txt");
        out.println("===================================================");
        if (total < min) {
            System.err.printf("FAIL: JSP line coverage %.1f%% is below the %.0f%% minimum.%n", total, min);
            System.exit(1);
        }
        out.printf("JSP line coverage %.1f%% (minimum %.0f%%).%n", total, min);
    }

    static double pct(int covered, int total) {
        return total == 0 ? 100.0 : 100.0 * covered / total;
    }
}
