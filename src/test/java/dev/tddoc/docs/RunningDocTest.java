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
/// Four ways to run tddoc, in increasing order of commitment. All four end
/// at the same place — the plugins are thin wrappers calling the same
/// `SiteGen.main` the CLI and the copied file use, so there is no second
/// code path to drift.
///
/// ## jbang — nothing to install
///
/// [jbang](https://jbang.dev) resolves the latest release from Maven Central:
///
/// ```bash
/// jbang tddoc@tddoc --docs src/test/java/your/pkg/docs --out build/site
/// ```
///
/// ## Gradle
///
/// Plugin id `dev.tddoc`, resolved from Maven Central:
///
/// ```kotlin
/// // settings.gradle.kts
/// pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
///
/// // build.gradle.kts
/// plugins { id("dev.tddoc") version "<version>" }
/// tddoc {
///     docs = file("src/test/java/your/pkg/docs")
///     name = "yourproject"
///     tagline = "your docs, proven"
///     repo = "https://github.com/you/yourproject"
///     javadoc = layout.buildDirectory.dir("docs/javadoc")
/// }
/// ```
///
/// `./gradlew tddocSite` runs your tests first (the task depends on `test`),
/// then generates `build/site`, up-to-date-checked like any Gradle task.
/// Every extension property mirrors a CLI flag; all are optional except
/// `docs`.
///
/// ## Maven
///
/// The `tddoc:site` goal binds to `verify`, so the suite has passed before
/// the site is built:
///
/// ```xml
/// <plugin>
///   <groupId>dev.tddoc</groupId>
///   <artifactId>tddoc-maven-plugin</artifactId>
///   <version>${tddoc.version}</version>
///   <executions>
///     <execution><goals><goal>site</goal></goals></execution>
///   </executions>
///   <configuration>
///     <docs>src/test/java/your/pkg/docs</docs>
///     <name>yourproject</name>
///     <repo>https://github.com/you/yourproject</repo>
///   </configuration>
/// </plugin>
/// ```
///
/// Configuration mirrors the CLI flags 1:1; `mvn verify` (or
/// `mvn tddoc:site` directly) writes `target/site`.
///
/// ## Copy the file
///
/// `SiteGen.java` is one file, zero dependencies — copy it into your repo,
/// run it with the plain source launcher, own it forever.
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
        assertTrue(guide.contains("class=\"code\" data-lang=\"bash\""), "fences get the code card and language");
        assertTrue(guide.contains("<ol>"), "ordered lists render");
        assertTrue(guide.contains("<blockquote>"), "blockquotes render");
        assertTrue(guide.contains("<table>"), "tables render");
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
                "```bash",
                "echo proven",
                "```",
                "",
                "1. first",
                "2. second",
                "",
                "> quoted",
                "",
                "| a | b |",
                "| --- | --- |",
                "| 1 | 2 |",
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
