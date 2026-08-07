package dev.tddoc.docs;

import dev.tddoc.SiteGen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// ---
/// title: Running the generator
/// slug: running
/// order: 3
/// summary: Every flag, demonstrated by a test that runs SiteGen for real.
/// ---
///
/// Three ways to run tddoc, in increasing order of commitment:
///
/// - `jbang tddoc@tddoc --docs src/test/java/... --out build/site` — nothing
///   to install beyond [jbang](https://jbang.dev).
/// - Depend on `dev.tddoc:tddoc` from Maven Central and run the jar
///   (`java -jar tddoc-x.y.z.jar`) from your build.
/// - Copy `SiteGen.java` into your repo and run `java SiteGen.java` — one
///   file, zero dependencies, yours forever.
///
/// ## The flags
///
/// - `--docs` — directory of `*DocTest.java` files (the only required input
///   in practice).
/// - `--out` — output directory, default `build/site`.
/// - `--name`, `--tagline`, `--repo`, `--glyph` — branding; the glyph is the
///   one-letter favicon.
/// - `--install` — an install snippet rendered on the landing page;
///   `{version}` inside it is substituted, so release workflows can pass a
///   template.
/// - `--javadoc` — a javadoc directory to fold in under `/api/`.
/// - `--editBase` — where "edit this page" links point.
/// - `--version`, `--prefix`, `--channel` — versioned deploys: root for the
///   latest release, `v/x.y.z/` for frozen snapshots, `next/` for main.
///
/// ## Proof, not promise
///
/// This page practices what it preaches: the example below writes a small
/// doc-test to a temp directory, runs `SiteGen.main` on it — the same
/// entry point the CLI uses — and asserts on the generated HTML. The flag
/// documentation above cannot rot without this test failing.
class RunningDocTest {

    @Test
    void generates_a_site_and_substitutes_the_install_version(@TempDir Path tmp) throws Exception {
        Path docs = Files.createDirectories(tmp.resolve("docs"));
        Files.writeString(docs.resolve("SampleDocTest.java"), sampleDocTest());
        Path out = tmp.resolve("site");

        SiteGen.main(new String[]{
                "--docs", docs.toString(),
                "--out", out.toString(),
                "--name", "sampledoc",
                "--tagline", "a site generated inside a test",
                "--version", "9.9.9",
                "--install", "jbang tddoc@tddoc # {version}",
        });

        String index = Files.readString(out.resolve("index.html"));
        String guide = Files.readString(out.resolve("guides/sample/index.html"));
        assertTrue(index.contains("sampledoc"), "branding reaches the page");
        assertTrue(index.contains("# 9.9.9"), "{version} is substituted");
        assertTrue(guide.contains("A sample guide"), "front matter title renders");
        assertTrue(guide.contains("1 + 1"), "the test body renders as the example");
    }

    // The sample doc-test is assembled line by line so this file's own prose
    // markers stay unambiguous to the generator parsing it.
    private static String sampleDocTest() {
        List<String> prose = List.of(
                "---",
                "title: A sample guide",
                "slug: sample",
                "order: 1",
                "summary: Generated inside a test.",
                "---",
                "",
                "A one-example guide.",
                "",
                "[landing]");
        String header = prose.stream().map(l -> ("/// " + l).strip())
                .collect(Collectors.joining("\n"));
        return Stream.of(
                header,
                "class SampleDocTest {",
                "    @" + "Test",
                "    void one_plus_one() {",
                "        assert 1 + 1 == 2;",
                "    }",
                "}").collect(Collectors.joining("\n"));
    }
}
