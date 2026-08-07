package dev.tddoc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * tddoc: doc-tests in, article site out. Single file, zero dependencies, run with
 * `java SiteGen.java` (Java 21+), so any repo can copy this file and own it forever.
 * The nested classes below are organization only — the single-file contract is
 * exactly one class per concern, all in this file.
 *
 * <p>Content comes from "doc-tests" (*DocTest.java files in the --docs directory):
 * prose lives in /// markdown comment blocks, examples are the bodies of real @Test
 * methods (so CI proves every example compiles and behaves), and members marked with
 * a preceding `// site:include` line are rendered as code too. The generator
 * interleaves prose and code in source order and renders a static site into
 * build/site, folding the Javadoc output in under /api/.
 *
 * <p>Format summary:
 * <ul>
 *   <li>First /// block starts with a `---` ... `---` front matter: title, slug,
 *       order, summary.</li>
 *   <li>A prose line that is exactly `[landing]` flags the next example as the
 *       homepage snippet (the line itself is dropped).</li>
 *   <li>Brace counting ignores string contents only heuristically, so don't put
 *       unbalanced braces inside string literals in doc-tests.</li>
 * </ul>
 */
public class SiteGen {

    record Segment(String kind, String text) {} // kind: "md" | "code"

    record Article(String title, String slug, int order, String summary,
                   String sourceFile, List<Segment> segments) {}

    record Landing(Article article, String code) {}

    public static void main(String[] args) throws IOException, InterruptedException {
        var config = Config.from(args);
        generate(config);
        if (config.watch()) {
            Watcher.run(config);
        }
    }

    static void generate(Config config) throws IOException {
        var parsed = Parser.parse(config.docs());
        var html = new Html(config, parsed.landing());
        var out = config.out();

        Files.createDirectories(out);
        Files.writeString(out.resolve(".nojekyll"), "");
        Files.writeString(out.resolve("index.html"), html.landingPage(parsed.articles()));
        for (var article : parsed.articles()) {
            var dir = out.resolve("guides").resolve(article.slug());
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("index.html"), html.articlePage(article, parsed.articles()));
        }
        if (Files.isDirectory(config.javadoc())) {
            copyTree(config.javadoc(), out.resolve("api"));
        } else {
            System.err.println("warning: no javadoc at " + config.javadoc() + "; /api/ not generated");
        }
        // style.css goes last: the watch-mode reload script polls its
        // Last-Modified, so it must move only after all pages are complete.
        Files.writeString(out.resolve("style.css"), Html.CSS);
        System.out.println("site: " + out.toAbsolutePath()
                + " (" + parsed.articles().size() + " guides, version " + config.version() + ")");
    }

    static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            walk.forEach(src -> {
                try {
                    var dst = to.resolve(from.relativize(src).toString());
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Parses a tddoc.yml: a deliberately flat subset of YAML, hand-rolled to
     * keep the zero-dependency contract. Supported: {@code key: value} pairs,
     * {@code #} comments, blank lines, optional single/double quotes around
     * values, and {@code |} literal blocks for multiline values (the install
     * snippet). Not supported, by design: nesting, lists, anchors — anything
     * else that would need a real YAML library.
     */
    public static Map<String, String> readConfig(Path file) throws IOException {
        var config = new LinkedHashMap<String, String>();
        var lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            var s = lines.get(i).strip();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            int colon = s.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException(file + ":" + (i + 1) + ": expected 'key: value'");
            }
            var key = s.substring(0, colon).strip();
            var value = s.substring(colon + 1).strip();
            if (value.equals("|")) {
                var block = new ArrayList<String>();
                int blockIndent = -1;
                while (i + 1 < lines.size()) {
                    var next = lines.get(i + 1);
                    if (!next.isBlank() && next.strip().length() == next.length()) {
                        break; // back at column zero: block over
                    }
                    if (blockIndent < 0 && !next.isBlank()) {
                        blockIndent = next.length() - next.stripLeading().length();
                    }
                    block.add(next.isBlank() ? "" : next.substring(Math.min(blockIndent, next.length())));
                    i++;
                }
                while (!block.isEmpty() && block.getLast().isEmpty()) {
                    block.removeLast();
                }
                value = String.join("\n", block);
            } else if (value.length() >= 2
                    && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            config.put(key, value);
        }
        return config;
    }

    // ------------------------------------------------------------------
    // Configuration: flags + tddoc.yml -> one immutable value
    // ------------------------------------------------------------------

    /**
     * Every setting the generator knows, resolved once at startup. Branding
     * arrives here rather than living in mutable statics, so the generator
     * itself stays project-neutral.
     */
    record Config(Path docs, Path out, Path javadoc, String version, String prefix,
                  String channel, String name, String tagline, String repo, String glyph,
                  String editBase, String install, boolean watch) {

        /**
         * Precedence: CLI flag > config file > built-in default. The config
         * file is tddoc.yml next to the code (or --config); paths inside it
         * resolve against the file's own directory, so builds work from any
         * working directory.
         */
        static Config from(String[] args) throws IOException {
            var argList = new ArrayList<>(List.of(args));
            boolean watch = argList.remove("--watch"); // bare flag; the loop below reads pairs
            var opts = new LinkedHashMap<String, String>();
            for (int i = 0; i + 1 < argList.size(); i += 2) {
                opts.put(argList.get(i).replaceFirst("^--", ""), argList.get(i + 1));
            }
            var configPath = opts.containsKey("config") ? Path.of(opts.get("config"))
                    : Files.exists(Path.of("tddoc.yml")) ? Path.of("tddoc.yml") : null;
            if (configPath != null) {
                if (!Files.isRegularFile(configPath)) {
                    throw new IllegalArgumentException("config file not found: " + configPath);
                }
                var base = configPath.toAbsolutePath().getParent();
                readConfig(configPath).forEach((key, value) -> {
                    if (!opts.containsKey(key)) {
                        opts.put(key, Set.of("docs", "out", "javadoc").contains(key)
                                ? base.resolve(value).toString() : value);
                    }
                });
            }
            var version = opts.getOrDefault("version", "0.1.0");
            var docs = Path.of(opts.getOrDefault("docs", "src/test/java/dev/fforj/docs"));
            var name = opts.getOrDefault("name", "tddoc");
            var repo = opts.getOrDefault("repo", "https://github.com/tddoc/tddoc");
            return new Config(
                    docs,
                    Path.of(opts.getOrDefault("out", "build/site")),
                    Path.of(opts.getOrDefault("javadoc", "build/docs/javadoc")),
                    version,
                    // Versioned deployment (ADR-5 addendum): prefix is this build's
                    // path under the site root ("" for the latest release at root,
                    // "v/0.1.0/" for a frozen snapshot, "next/" for main); channel is
                    // the label the version selector shows for this build. The
                    // selector loads <site-root>/versions.json at page load, so
                    // frozen snapshots list versions released after them.
                    opts.getOrDefault("prefix", ""),
                    opts.getOrDefault("channel", version),
                    name,
                    opts.getOrDefault("tagline", "test-driven documentation: proven, not promised"),
                    repo,
                    opts.getOrDefault("glyph", name.substring(0, 1)),
                    // Where "edit this page" links point: the doc-test sources.
                    opts.getOrDefault("editBase", repo + "/blob/main/" + docs + "/"),
                    opts.getOrDefault("install", ""),
                    watch);
        }
    }

    // ------------------------------------------------------------------
    // Parsing doc-tests
    // ------------------------------------------------------------------

    static class Parser {

        record ParseResult(List<Article> articles, Landing landing) {}

        record Parsed(Article article, String landingCode) {}

        static ParseResult parse(Path docsDir) throws IOException {
            var articles = new ArrayList<Article>();
            Landing landing = null;
            List<Path> files;
            try (Stream<Path> walk = Files.walk(docsDir)) {
                files = walk.filter(p -> p.getFileName().toString().endsWith("DocTest.java")).toList();
            }
            for (var file : files) {
                var parsed = parseArticle(file);
                articles.add(parsed.article());
                if (parsed.landingCode() != null) {
                    landing = new Landing(parsed.article(), parsed.landingCode());
                }
            }
            articles.sort(Comparator.comparingInt(Article::order));
            if (landing == null) {
                throw new IllegalStateException("no [landing] example found in any doc-test");
            }
            return new ParseResult(articles, landing);
        }

        static Parsed parseArticle(Path file) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            var meta = new LinkedHashMap<String, String>();
            var segments = new ArrayList<Segment>();
            var prose = new ArrayList<String>();
            boolean metaDone = false;
            boolean landingNext = false;
            String pendingLandingCode = null;

            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                var stripped = line.strip();

                if (stripped.startsWith("///")) {
                    var text = stripped.substring(3);
                    if (text.startsWith(" ")) {
                        text = text.substring(1);
                    }
                    prose.add(text);
                    continue;
                }

                if (stripped.equals("// site:include")) {
                    landingNext |= flushProse(prose, segments, meta, metaDone);
                    metaDone = true;
                    i = captureMember(lines, i + 1, segments);
                    continue;
                }

                if (stripped.startsWith("@Test")) {
                    landingNext |= flushProse(prose, segments, meta, metaDone);
                    metaDone = true;
                    i = captureTestBody(lines, i + 1, segments);
                    if (landingNext) {
                        pendingLandingCode = segments.getLast().text();
                        landingNext = false;
                    }
                }
            }
            flushProse(prose, segments, meta, metaDone);

            var article = new Article(
                    meta.getOrDefault("title", file.getFileName().toString()),
                    meta.getOrDefault("slug", file.getFileName().toString().toLowerCase()),
                    Integer.parseInt(meta.getOrDefault("order", "99")),
                    meta.getOrDefault("summary", ""),
                    file.getFileName().toString(),
                    segments);
            return new Parsed(article, pendingLandingCode);
        }

        /** Returns true if the flushed prose carried the [landing] marker. */
        static boolean flushProse(List<String> prose, List<Segment> segments,
                                  Map<String, String> meta, boolean metaDone) {
            if (prose.isEmpty()) {
                return false;
            }
            var lines = new ArrayList<>(prose);
            prose.clear();
            if (!metaDone && !lines.isEmpty() && lines.getFirst().strip().equals("---")) {
                int end = lines.subList(1, lines.size()).indexOf("---") + 1;
                for (var kv : lines.subList(1, end)) {
                    int colon = kv.indexOf(':');
                    if (colon > 0) {
                        meta.put(kv.substring(0, colon).strip(), kv.substring(colon + 1).strip());
                    }
                }
                lines.subList(0, end + 1).clear();
            }
            boolean landingMarker = lines.removeIf(l -> l.strip().equals("[landing]"));
            var text = String.join("\n", lines).strip();
            if (!text.isEmpty()) {
                segments.add(new Segment("md", text));
            }
            return landingMarker;
        }

        /** Captures a member (record/method/interface) after a site:include marker. */
        static int captureMember(List<String> lines, int start, List<Segment> segments) {
            var captured = new ArrayList<String>();
            int depth = 0;
            boolean sawBrace = false;
            int i = start;
            for (; i < lines.size(); i++) {
                var line = lines.get(i);
                captured.add(line);
                for (char c : line.toCharArray()) {
                    if (c == '{') { depth++; sawBrace = true; }
                    if (c == '}') { depth--; }
                }
                if (sawBrace && depth == 0) {
                    break;
                }
                if (!sawBrace && line.strip().endsWith(";")) {
                    break;
                }
            }
            segments.add(new Segment("code", dedent(captured)));
            return i;
        }

        /** Captures the body of a @Test method, excluding the signature and closing brace. */
        static int captureTestBody(List<String> lines, int start, List<Segment> segments) {
            int i = start;
            while (i < lines.size() && !lines.get(i).contains("(")) {
                i++;
            }
            // Signature line: assume it opens the body brace.
            int depth = 0;
            for (char c : lines.get(i).toCharArray()) {
                if (c == '{') { depth++; }
                if (c == '}') { depth--; }
            }
            var body = new ArrayList<String>();
            for (i++; i < lines.size(); i++) {
                var line = lines.get(i);
                int before = depth;
                for (char c : line.toCharArray()) {
                    if (c == '{') { depth++; }
                    if (c == '}') { depth--; }
                }
                if (depth == 0 && before == 1) {
                    break; // the method's closing brace
                }
                body.add(line);
            }
            segments.add(new Segment("code", dedent(body)));
            return i;
        }

        static String dedent(List<String> lines) {
            while (!lines.isEmpty() && lines.getFirst().isBlank()) {
                lines.removeFirst();
            }
            while (!lines.isEmpty() && lines.getLast().isBlank()) {
                lines.removeLast();
            }
            int indent = lines.stream()
                    .filter(l -> !l.isBlank())
                    .mapToInt(l -> l.length() - l.stripLeading().length())
                    .min().orElse(0);
            return lines.stream()
                    .map(l -> l.isBlank() ? "" : l.substring(Math.min(indent, l.length())))
                    .reduce((a, b) -> a + "\n" + b).orElse("");
        }
    }

    // ------------------------------------------------------------------
    // Markdown subset -> HTML
    // ------------------------------------------------------------------

    static class Markdown {

        // Renderer state: open block elements that must be closed before a
        // different block kind starts.
        static class MdState {
            final StringBuilder html = new StringBuilder();
            final List<String> para = new ArrayList<>();
            final List<String> quote = new ArrayList<>();
            final List<String> table = new ArrayList<>();
            boolean inUl, inOl;
        }

        static String md(String text) {
            var st = new MdState();
            boolean inFence = false;
            String fenceLang = "";
            var fence = new ArrayList<String>();
            for (var line : (text + "\n").split("\n", -1)) {
                var s = line.strip();
                if (inFence) {
                    if (s.startsWith("```")) {
                        var lang = fenceLang.isEmpty() ? "" : " data-lang=\"" + escape(fenceLang) + "\"";
                        st.html.append("<pre class=\"code\"").append(lang).append("><code>")
                                .append(escape(String.join("\n", fence))).append("</code></pre>\n");
                        fence.clear();
                        inFence = false;
                    } else {
                        fence.add(line);
                    }
                    continue;
                }
                if (s.startsWith("```")) {
                    closeBlocks(st);
                    fenceLang = s.substring(3).strip();
                    inFence = true;
                } else if (s.startsWith("### ")) {
                    closeBlocks(st);
                    st.html.append("<h3 id=\"").append(slugify(s.substring(4))).append("\">")
                            .append(inline(s.substring(4))).append("</h3>\n");
                } else if (s.startsWith("## ")) {
                    closeBlocks(st);
                    st.html.append("<h2 id=\"").append(slugify(s.substring(3))).append("\">")
                            .append(inline(s.substring(3))).append("</h2>\n");
                } else if (s.equals("---")) {
                    closeBlocks(st);
                    st.html.append("<hr>\n");
                } else if (s.startsWith("|")) {
                    closePara(st);
                    st.table.add(s);
                } else if (s.startsWith("> ") || s.equals(">")) {
                    closePara(st);
                    st.quote.add(s.equals(">") ? "" : s.substring(2));
                } else if (s.startsWith("- ")) {
                    closePara(st);
                    if (!st.inUl) {
                        closeBlocks(st);
                        st.html.append("<ul>\n");
                        st.inUl = true;
                    }
                    st.html.append("<li>").append(inline(s.substring(2))).append("</li>\n");
                } else if (s.matches("\\d+\\. .*")) {
                    closePara(st);
                    if (!st.inOl) {
                        closeBlocks(st);
                        st.html.append("<ol>\n");
                        st.inOl = true;
                    }
                    st.html.append("<li>").append(inline(s.substring(s.indexOf(' ') + 1)))
                            .append("</li>\n");
                } else if (s.isEmpty()) {
                    closeBlocks(st);
                } else {
                    st.para.add(s);
                }
            }
            closeBlocks(st);
            return st.html.toString();
        }

        static void closePara(MdState st) {
            if (!st.para.isEmpty()) {
                st.html.append("<p>").append(inline(String.join(" ", st.para))).append("</p>\n");
                st.para.clear();
            }
        }

        static void closeBlocks(MdState st) {
            closePara(st);
            if (st.inUl) {
                st.html.append("</ul>\n");
                st.inUl = false;
            }
            if (st.inOl) {
                st.html.append("</ol>\n");
                st.inOl = false;
            }
            if (!st.quote.isEmpty()) {
                st.html.append("<blockquote>\n");
                var qp = new ArrayList<String>();
                for (var q : st.quote) {
                    if (q.isEmpty()) {
                        flushQuotePara(st, qp);
                    } else {
                        qp.add(q);
                    }
                }
                flushQuotePara(st, qp);
                st.html.append("</blockquote>\n");
                st.quote.clear();
            }
            if (!st.table.isEmpty()) {
                st.html.append("<div class=\"tablewrap\">\n<table>\n");
                boolean headerDone = false;
                for (var row : st.table) {
                    if (row.matches("\\|[\\s|:-]+")) { // separator row: |---|---|
                        continue;
                    }
                    var cells = row.substring(1, row.endsWith("|") ? row.length() - 1 : row.length())
                            .split("\\|", -1);
                    var tag = headerDone ? "td" : "th";
                    st.html.append("<tr>");
                    for (var cell : cells) {
                        st.html.append("<").append(tag).append(">").append(inline(cell.strip()))
                                .append("</").append(tag).append(">");
                    }
                    st.html.append("</tr>\n");
                    headerDone = true;
                }
                st.html.append("</table>\n</div>\n");
                st.table.clear();
            }
        }

        static void flushQuotePara(MdState st, List<String> qp) {
            if (!qp.isEmpty()) {
                st.html.append("<p>").append(inline(String.join(" ", qp))).append("</p>\n");
                qp.clear();
            }
        }

        static String inline(String s) {
            s = escape(s);
            // Protect inline code spans from further formatting ("\0" sentinels
            // cannot occur in doc-test prose).
            var codes = new ArrayList<String>();
            var m = java.util.regex.Pattern.compile("`([^`]+)`").matcher(s);
            var sb = new StringBuilder();
            while (m.find()) {
                codes.add("<code>" + m.group(1) + "</code>");
                m.appendReplacement(sb, "\0" + (codes.size() - 1) + "\0");
            }
            m.appendTail(sb);
            s = sb.toString();
            s = s.replaceAll("!\\[([^]]*)]\\(([^)]+)\\)", "<img alt=\"$1\" src=\"$2\">");
            s = s.replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
            s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
            s = s.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");
            for (int i = 0; i < codes.size(); i++) {
                s = s.replace("\0" + i + "\0", codes.get(i));
            }
            return s;
        }

        static String slugify(String s) {
            return s.toLowerCase().replaceAll("`", "").replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
        }

        static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    // ------------------------------------------------------------------
    // Java syntax highlighting (comments, strings, annotations, keywords)
    // ------------------------------------------------------------------

    static class Highlighter {

        static final Set<String> KEYWORDS = Set.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "continue", "default", "do", "double", "else", "enum", "extends",
                "final", "finally", "float", "for", "if", "implements", "import",
                "instanceof", "int", "interface", "long", "new", "package", "private",
                "protected", "public", "record", "return", "sealed", "short", "static",
                "super", "switch", "synchronized", "this", "throw", "throws", "try", "var",
                "void", "volatile", "while", "yield", "permits", "non-sealed");

        static String highlight(String code) {
            var out = new StringBuilder();
            int i = 0;
            int n = code.length();
            while (i < n) {
                char c = code.charAt(i);
                if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                    int end = code.indexOf('\n', i);
                    if (end < 0) end = n;
                    out.append("<span class=\"c-com\">").append(Markdown.escape(code.substring(i, end)))
                            .append("</span>");
                    i = end;
                } else if (c == '"') {
                    int end = i + 1;
                    while (end < n && (code.charAt(end) != '"' || code.charAt(end - 1) == '\\')) {
                        end++;
                    }
                    end = Math.min(end + 1, n);
                    out.append("<span class=\"c-str\">").append(Markdown.escape(code.substring(i, end)))
                            .append("</span>");
                    i = end;
                } else if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(code.charAt(i + 1))) {
                    int end = i + 1;
                    while (end < n && Character.isJavaIdentifierPart(code.charAt(end))) {
                        end++;
                    }
                    out.append("<span class=\"c-ann\">").append(code, i, end).append("</span>");
                    i = end;
                } else if (Character.isJavaIdentifierStart(c)) {
                    int end = i;
                    while (end < n && Character.isJavaIdentifierPart(code.charAt(end))) {
                        end++;
                    }
                    var word = code.substring(i, end);
                    if (KEYWORDS.contains(word)) {
                        out.append("<span class=\"c-kw\">").append(word).append("</span>");
                    } else {
                        out.append(word);
                    }
                    i = end;
                } else {
                    out.append(Markdown.escape(String.valueOf(c)));
                    i++;
                }
            }
            return out.toString();
        }
    }

    // ------------------------------------------------------------------
    // Watch mode
    // ------------------------------------------------------------------

    static class Watcher {

        /**
         * Watches the doc-test tree and regenerates on every change, forever.
         * Pairs with the JDK's own server for a live preview:
         * {@code jwebserver -d build/site}. A parse error mid-edit prints and
         * keeps watching — a half-typed brace must not kill the session.
         */
        static void run(Config config) throws IOException, InterruptedException {
            var docsDir = config.docs();
            try (var watcher = docsDir.getFileSystem().newWatchService()) {
                try (Stream<Path> dirs = Files.walk(docsDir)) {
                    for (var dir : dirs.filter(Files::isDirectory).toList()) {
                        dir.register(watcher,
                                java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                                java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                                java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);
                    }
                }
                System.out.println("watching " + docsDir
                        + " — serve the site with: jwebserver -d " + config.out());
                while (true) {
                    var key = watcher.take();
                    Thread.sleep(150); // debounce editor save bursts
                    key.pollEvents();
                    var pending = watcher.poll();
                    while (pending != null) {
                        pending.pollEvents();
                        pending.reset();
                        pending = watcher.poll();
                    }
                    try {
                        generate(config);
                    } catch (Exception e) {
                        System.err.println("regen failed (still watching): " + e.getMessage());
                    }
                    if (!key.reset()) {
                        return;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    static class Html {

        final Config cfg;
        final Landing landing;

        Html(Config cfg, Landing landing) {
            this.cfg = cfg;
            this.landing = landing;
        }

        String landingPage(List<Article> articles) {
            var body = new StringBuilder();
            body.append("""
                    <main class="landing">
                    <section class="hero">
                      <div class="specimen">
                        <div class="glyph">%s</div>
                      </div>
                      <div class="thesis">
                        <h1>%s</h1>
                        <p class="sub">%s</p>
                      </div>
                    </section>

                    <section class="proof">
                      <div class="eyebrow">PROVEN, NOT PROMISED</div>
                      <p>This example is a test. It ran, and passed, before this page was built:</p>
                    """.formatted(cfg.glyph(), Markdown.escape(cfg.name()), Markdown.escape(cfg.tagline())));
            body.append(codeBlock(landing.code()));
            body.append("<p class=\"more\"><a href=\"guides/").append(landing.article().slug())
                    .append("/index.html\">Read the ").append(Markdown.escape(landing.article().title()))
                    .append(" guide &rarr;</a></p>\n</section>\n");

            body.append("<section id=\"guides\" class=\"guides\">\n<div class=\"eyebrow\">GUIDES</div>\n<div class=\"cards\">\n");
            for (var a : articles) {
                body.append("<a class=\"card\" href=\"guides/").append(a.slug()).append("/index.html\">")
                        .append("<h3>").append(Markdown.inline(a.title())).append("</h3>")
                        .append("<p>").append(Markdown.inline(a.summary())).append("</p>")
                        .append("</a>\n");
            }
            body.append("</div>\n</section>\n");

            // The install section only renders when the project passes an --install
            // snippet ("{version}" inside it is replaced with the released version).
            if (!cfg.install().isEmpty()) {
                body.append("""
                        <section class="install">
                          <div class="eyebrow">GET IT</div>
                        """);
                body.append(codeBlock(cfg.install().replace("{version}", cfg.version())));
                body.append("""
                          <p>Or copy the source into your project: it behaves identically either
                          way. The <a href="api/index.html">API reference</a> is the full Javadoc.</p>
                        </section>
                        """);
            }
            body.append("</main>\n");
            return shell("", "index.html", cfg.name() + " · " + cfg.tagline(), cfg.tagline(), body.toString());
        }

        String articlePage(Article article, List<Article> all) {
            var body = new StringBuilder();
            body.append("<div class=\"layout\">\n<aside class=\"sidenav\">\n<div class=\"eyebrow\">GUIDES</div>\n<ul>\n");
            for (var a : all) {
                body.append("<li").append(a.slug().equals(article.slug()) ? " class=\"here\"" : "")
                        .append("><a href=\"../").append(a.slug()).append("/index.html\">")
                        .append(Markdown.inline(a.title())).append("</a></li>\n");
            }
            body.append("</ul>\n</aside>\n<main class=\"article\">\n");
            body.append("<h1>").append(Markdown.inline(article.title())).append("</h1>\n");
            for (var seg : article.segments()) {
                body.append(seg.kind().equals("md") ? Markdown.md(seg.text()) : codeBlock(seg.text()));
            }
            body.append("<p class=\"edit\"><a href=\"").append(cfg.editBase())
                    .append(article.sourceFile())
                    .append("\">This page is generated from a test file. Read or improve it on GitHub.</a></p>\n");
            body.append("</main>\n</div>\n");
            return shell("../../", "guides/" + article.slug() + "/index.html",
                    article.title() + " · " + cfg.name(), article.summary(), body.toString());
        }

        String codeBlock(String code) {
            return "<pre class=\"code\"><code>" + Highlighter.highlight(code) + "</code></pre>\n";
        }

        String shell(String root, String page, String title, String description, String body) {
            return """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%%22http://www.w3.org/2000/svg%%22 viewBox=%%220 0 100 100%%22><text y=%%22.9em%%22 font-size=%%2290%%22 font-family=%%22Georgia,serif%%22 fill=%%22%%23177245%%22>%s</text></svg>">
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                    <link href="https://fonts.googleapis.com/css2?family=Newsreader:ital,opsz,wght@0,6..72,400..700;1,6..72,400..700&family=JetBrains+Mono:wght@400;500;700&display=swap" rel="stylesheet">
                    <link rel="stylesheet" href="%sstyle.css">
                    </head>
                    <body>
                    <header class="top">
                      <div class="brand">
                        <a class="wordmark" href="%sindex.html">%s</a>
                        <select id="vsel" aria-label="Documentation version"
                                data-prefix="%s" data-page="%s"><option>%s</option></select>
                      </div>
                      <nav>
                        <a href="%sindex.html#guides">Guides</a>
                        <a href="%sapi/index.html">API</a>
                        <a href="%s">GitHub</a>
                      </nav>
                    </header>
                    %s
                    <footer class="foot">
                      <p>Every example on this site is a test in the
                      <a href="%s">repository</a>;
                      the suite ran green before this page was built.</p>
                    </footer>
                    <script>
                    (function () {
                      var sel = document.getElementById("vsel");
                      var prefix = sel.dataset.prefix, page = sel.dataset.page;
                      var path = location.pathname;
                      // Site root = current path minus this build's prefix and page path,
                      // tolerating servers that serve directories without "index.html".
                      var root = null;
                      var suffix = prefix + page;
                      if (path.endsWith(suffix)) {
                        root = path.slice(0, path.length - suffix.length);
                      } else if (suffix.endsWith("index.html")) {
                        var dir = suffix.slice(0, suffix.length - "index.html".length);
                        if (path.endsWith(dir)) root = path.slice(0, path.length - dir.length);
                      }
                      if (root === null) return;
                      fetch(root + "versions.json").then(function (r) { return r.json(); })
                        .then(function (v) {
                          sel.innerHTML = "";
                          v.entries.forEach(function (e) {
                            var o = document.createElement("option");
                            o.value = e.path;
                            o.textContent = e.label;
                            if (e.path === prefix) o.selected = true;
                            sel.appendChild(o);
                          });
                          sel.onchange = function () {
                            var target = root + sel.value + page;
                            // Same page may not exist in the chosen version; fall back to
                            // that version's landing page.
                            fetch(target, { method: "HEAD" })
                              .then(function (r) { location.href = r.ok ? target : root + sel.value; })
                              .catch(function () { location.href = root + sel.value; });
                          };
                        }).catch(function () {});
                    })();
                    (function () {
                      document.querySelectorAll("pre.code").forEach(function (pre) {
                        var wrap = document.createElement("div");
                        wrap.className = "codewrap";
                        pre.parentNode.insertBefore(wrap, pre);
                        wrap.appendChild(pre);
                        var btn = document.createElement("button");
                        btn.className = "copybtn";
                        btn.type = "button";
                        btn.textContent = "copy";
                        btn.onclick = function () {
                          var code = pre.querySelector("code") || pre;
                          navigator.clipboard.writeText(code.innerText).then(function () {
                            btn.textContent = "copied";
                            setTimeout(function () { btn.textContent = "copy"; }, 1500);
                          });
                        };
                        wrap.appendChild(btn);
                      });
                    })();
                    </script>
                    </body>
                    </html>
                    """.formatted(Markdown.escape(title), Markdown.escape(description), cfg.glyph(), root, root,
                    Markdown.escape(cfg.name()), cfg.prefix(), page, Markdown.escape(cfg.channel()), root, root,
                    cfg.repo(), body, cfg.repo())
                    .replace("</body>", cfg.watch() ? watchJs(root) + "</body>" : "</body>");
        }

        /** Watch-mode only: reload the page when style.css (written last) moves. */
        static String watchJs(String root) {
            return """
                    <script>
                    (function () {
                      var last = null;
                      setInterval(function () {
                        fetch("%sstyle.css", { method: "HEAD", cache: "no-store" }).then(function (r) {
                          var m = r.headers.get("last-modified");
                          if (last && m && m !== last) location.reload();
                          last = m;
                        }).catch(function () {});
                      }, 1000);
                    })();
                    </script>
                    """.formatted(root);
        }

        // --------------------------------------------------------------
        // Styles: rubrication on cool paper. Newsreader for prose, JetBrains
        // Mono (ligatures on, the theme demands it) for code.
        // --------------------------------------------------------------

        static final String CSS = """
                :root {
                  --paper: #FCFBF8; --ink: #1C1A16; --rubric: #177245; --muted: #6F6A5F;
                  --rule: #E6E2D8; --code-bg: #F5F3EC; --card: #FFFFFF;
                }
                @media (prefers-color-scheme: dark) {
                  :root {
                    --paper: #181715; --ink: #E8E4DB; --rubric: #3FAE72; --muted: #9A947F;
                    --rule: #2E2B26; --code-bg: #211F1B; --card: #1E1C19;
                  }
                }
                * { box-sizing: border-box; margin: 0; }
                html { -webkit-text-size-adjust: 100%; }
                body {
                  background: var(--paper); color: var(--ink);
                  font-family: "Newsreader", Georgia, serif;
                  font-optical-sizing: auto; font-size: 1.125rem; line-height: 1.65;
                }
                a { color: inherit; text-decoration-color: var(--rubric); text-decoration-thickness: 1px; text-underline-offset: 3px; }
                a:hover { color: var(--rubric); }
                code, pre {
                  font-family: "JetBrains Mono", ui-monospace, monospace;
                  font-variant-ligatures: contextual;
                }
                p > code, li > code, h1 code, h2 code, h3 code {
                  font-size: 0.82em; background: var(--code-bg);
                  padding: 0.08em 0.35em; border-radius: 4px;
                }
                .rub { color: var(--rubric); }
                .lig { color: var(--rubric); }
                .eyebrow {
                  font-family: "JetBrains Mono", monospace; font-size: 0.72rem;
                  letter-spacing: 0.18em; color: var(--rubric); margin-bottom: 0.9rem;
                }

                .top {
                  display: flex; justify-content: space-between; align-items: baseline;
                  padding: 1.4rem clamp(1.2rem, 5vw, 4rem);
                  border-bottom: 1px solid var(--rule);
                }
                .wordmark { font-size: 1.5rem; font-weight: 600; text-decoration: none; }
                .brand { display: flex; align-items: baseline; gap: 0.9rem; }
                #vsel {
                  font-family: "JetBrains Mono", monospace; font-size: 0.72rem;
                  color: var(--muted); background: var(--code-bg);
                  border: 1px solid var(--rule); border-radius: 6px; padding: 0.2rem 0.4rem;
                }
                #vsel:hover { border-color: var(--rubric); color: var(--ink); }
                .top nav { display: flex; gap: 1.6rem; }
                .top nav a { text-decoration: none; font-size: 1rem; color: var(--muted); }
                .top nav a:hover { color: var(--rubric); }

                .landing { max-width: 68rem; margin: 0 auto; padding: 0 clamp(1.2rem, 5vw, 4rem); }
                .hero {
                  display: grid; grid-template-columns: minmax(12rem, 1fr) 2fr;
                  gap: clamp(1.5rem, 5vw, 4rem); align-items: center;
                  padding: clamp(2.5rem, 8vh, 6rem) 0 3rem;
                }
                .specimen { text-align: center; }
                .glyph {
                  font-size: clamp(9rem, 22vw, 16rem); line-height: 0.9;
                  color: var(--rubric); font-weight: 500;
                  font-feature-settings: "liga", "dlig";
                }
                .caption {
                  font-family: "JetBrains Mono", monospace; font-size: 0.68rem;
                  letter-spacing: 0.14em; color: var(--muted); margin-top: 1rem;
                }
                .thesis h1 {
                  font-size: clamp(2.2rem, 5.5vw, 3.6rem); font-weight: 500;
                  line-height: 1.08; letter-spacing: -0.01em; margin-bottom: 1.1rem;
                }
                .sub { font-size: 1.15rem; color: var(--muted); max-width: 34rem; }

                section { padding: 2.6rem 0; border-top: 1px solid var(--rule); }
                .hero { border-top: none; }

                .code {
                  background: var(--code-bg); border: 1px solid var(--rule); border-radius: 8px;
                  padding: 1.1rem 1.3rem; overflow-x: auto;
                  font-size: 0.86rem; line-height: 1.6; margin: 1.3rem 0;
                }
                .codewrap { position: relative; }
                .codewrap .code { margin: 1.3rem 0; }
                .code[data-lang]::before {
                  content: attr(data-lang);
                  float: right; margin-left: 1rem;
                  font-family: "JetBrains Mono", monospace; font-size: 0.68rem;
                  letter-spacing: 0.14em; text-transform: uppercase; color: var(--muted);
                  transition: opacity 0.15s;
                }
                .codewrap:hover .code[data-lang]::before { opacity: 0; }
                .copybtn {
                  position: absolute; top: 0.55rem; right: 0.6rem;
                  font-family: "JetBrains Mono", monospace; font-size: 0.68rem;
                  letter-spacing: 0.08em; color: var(--muted);
                  background: var(--paper); border: 1px solid var(--rule);
                  border-radius: 6px; padding: 0.2rem 0.5rem; cursor: pointer;
                  opacity: 0; transition: opacity 0.15s;
                }
                .codewrap:hover .copybtn, .copybtn:focus-visible { opacity: 1; }
                .copybtn:hover { border-color: var(--rubric); color: var(--ink); }
                blockquote {
                  margin: 1.3rem 0; padding: 0.2rem 0 0.2rem 1.2rem;
                  border-left: 3px solid var(--rubric); color: var(--muted);
                }
                blockquote p { margin: 0.4rem 0; }
                hr { border: 0; border-top: 1px solid var(--rule); margin: 2.2rem 0; }
                .tablewrap { overflow-x: auto; margin: 1.3rem 0; }
                table { border-collapse: collapse; width: 100%; font-size: 0.95em; }
                th, td { text-align: left; padding: 0.5rem 0.9rem 0.5rem 0; border-bottom: 1px solid var(--rule); }
                th {
                  font-family: "JetBrains Mono", monospace; font-size: 0.68rem;
                  letter-spacing: 0.14em; text-transform: uppercase; color: var(--muted);
                }
                article img { max-width: 100%; }
                .c-kw { font-weight: 700; }
                .c-str { color: var(--rubric); }
                .c-com { color: var(--muted); font-style: italic; }
                .c-ann { color: var(--muted); }

                .cards {
                  display: grid; grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
                  gap: 1rem;
                }
                .card {
                  display: block; background: var(--card); border: 1px solid var(--rule);
                  border-radius: 10px; padding: 1.2rem 1.3rem; text-decoration: none;
                }
                .card:hover { border-color: var(--rubric); color: inherit; }
                .card h3 { font-weight: 600; margin-bottom: 0.4rem; }
                .card p { color: var(--muted); font-size: 0.98rem; }

                .ethos ul { padding-left: 1.2rem; margin: 0.6rem 0 1rem; }
                .ethos li { margin: 0.35rem 0; }
                .more a { text-decoration: none; color: var(--rubric); }

                .layout {
                  display: grid; grid-template-columns: 15rem minmax(0, 1fr);
                  gap: clamp(1.5rem, 4vw, 4rem);
                  max-width: 72rem; margin: 0 auto; padding: 2.5rem clamp(1.2rem, 5vw, 4rem);
                }
                .sidenav ul { list-style: none; padding: 0; }
                .sidenav li { margin: 0.45rem 0; font-size: 1rem; }
                .sidenav a { text-decoration: none; color: var(--muted); }
                .sidenav a:hover { color: var(--rubric); }
                .sidenav .here a { color: var(--ink); font-weight: 600; }
                .sidenav .here::before { content: "\\00B6\\00A0"; color: var(--rubric); }

                .article { max-width: 44rem; }
                .article h1 { font-size: 2.3rem; font-weight: 500; line-height: 1.15; margin-bottom: 1rem; }
                .article h2 {
                  font-size: 1.45rem; font-weight: 600; margin: 2.4rem 0 0.7rem;
                }
                .article h2::before { content: "\\00B6\\00A0"; color: var(--rubric); font-weight: 400; }
                .article h3 { font-size: 1.15rem; margin: 1.8rem 0 0.5rem; }
                .article p { margin: 0.8rem 0; }
                .article ul { padding-left: 1.3rem; margin: 0.8rem 0; }
                .edit { margin-top: 3rem; font-size: 0.95rem; }
                .edit a { color: var(--muted); }

                .foot {
                  border-top: 1px solid var(--rule); margin-top: 3rem;
                  padding: 1.6rem clamp(1.2rem, 5vw, 4rem);
                  color: var(--muted); font-size: 0.98rem;
                }

                @media (max-width: 760px) {
                  .hero { grid-template-columns: 1fr; text-align: left; }
                  .specimen { text-align: left; }
                  .layout { grid-template-columns: 1fr; }
                  .sidenav { border-bottom: 1px solid var(--rule); padding-bottom: 1rem; }
                }
                @media (prefers-reduced-motion: no-preference) {
                  .glyph { transition: transform 0.4s ease; }
                  .specimen:hover .glyph { transform: translateY(-4px); }
                }
                """;
    }
}
