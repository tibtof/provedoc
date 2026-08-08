package dev.tddoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteGenTest {

    @Test
    void config_parses_flat_pairs_comments_and_quotes(@TempDir Path tmp) throws Exception {
        Path yml = tmp.resolve("tddoc.yml");
        Files.writeString(yml, """
                # branding
                name: myproject
                tagline: "docs, proven"
                glyph: 'm'

                repo: https://example.com/repo
                """);
        var config = SiteGen.readConfig(yml);
        assertEquals("myproject", config.get("name"));
        assertEquals("docs, proven", config.get("tagline"));
        assertEquals("m", config.get("glyph"));
        assertEquals("https://example.com/repo", config.get("repo"));
        assertFalse(config.containsKey("# branding"));
    }

    @Test
    void config_supports_literal_blocks_for_multiline_values(@TempDir Path tmp) throws Exception {
        Path yml = tmp.resolve("tddoc.yml");
        Files.writeString(yml, """
                install: |
                  implementation("dev.tddoc:tddoc:{version}")
                  // or copy the file
                name: after
                """);
        var config = SiteGen.readConfig(yml);
        assertEquals("implementation(\"dev.tddoc:tddoc:{version}\")\n// or copy the file",
                config.get("install"));
        assertEquals("after", config.get("name"));
    }

    @Test
    void config_rejects_lines_without_a_key(@TempDir Path tmp) throws Exception {
        Path yml = tmp.resolve("tddoc.yml");
        Files.writeString(yml, "just some text\n");
        assertThrows(IllegalArgumentException.class, () -> SiteGen.readConfig(yml));
    }

    @Test
    void themes_and_css_overrides_layer_onto_the_token_sheet(@TempDir Path tmp) throws Exception {
        Path docs = docsWithOneArticle(tmp);
        Path override = tmp.resolve("brand.css");
        Files.writeString(override, ":root { --rubric: #C0341D; }\n");

        SiteGen.main(new String[]{"--docs", docs.toString(),
                "--out", tmp.resolve("plain").toString(), "--theme", "plain"});
        String plainCss = Files.readString(tmp.resolve("plain/style.css"));
        String plainHtml = Files.readString(tmp.resolve("plain/index.html"));
        assertTrue(plainCss.contains("theme: plain"), "plain overrides appended");
        assertFalse(plainHtml.contains("googleapis"), "plain theme drops webfont links");

        SiteGen.main(new String[]{"--docs", docs.toString(),
                "--out", tmp.resolve("branded").toString(), "--css", override.toString()});
        String brandedCss = Files.readString(tmp.resolve("branded/style.css"));
        assertTrue(brandedCss.contains("--rubric: #C0341D"), "--css appends after the sheet");

        SiteGen.main(new String[]{"--docs", docs.toString(),
                "--out", tmp.resolve("styled").toString(), "--style", override.toString()});
        assertEquals(Files.readString(override), Files.readString(tmp.resolve("styled/style.css")),
                "--style replaces the sheet entirely");

        var thrown = assertThrows(IllegalArgumentException.class, () ->
                SiteGen.main(new String[]{"--docs", docs.toString(),
                        "--out", tmp.resolve("bad").toString(), "--theme", "nope"}));
        assertTrue(thrown.getMessage().contains("unknown theme"));
    }

    private static Path docsWithOneArticle(Path tmp) throws Exception {
        Path docs = Files.createDirectories(tmp.resolve("docs-" + tmp.getFileName()));
        Files.writeString(docs.resolve("MiniDocTest.java"), """
                /// ---
                /// title: Mini
                /// slug: mini
                /// order: 1
                /// summary: Minimal.
                /// ---
                ///
                /// Hello.
                ///
                /// [landing]
                class MiniDocTest {
                    @""" + """
                Test
                    void truth() {
                        assert true;
                    }
                }
                """);
        return docs;
    }

    @Test
    void fences_highlight_per_language() {
        assertTrue(SiteGen.Highlighter.highlight("fun main() {}", "kotlin")
                .contains("<span class=\"c-kw\">fun</span>"), "kotlin keywords");
        assertTrue(SiteGen.Highlighter.highlight("# comment\necho hi", "bash")
                .contains("<span class=\"c-com\"># comment</span>"), "bash hash comments");
        assertTrue(SiteGen.Highlighter.highlight("<plugin id=\"x\">", "xml")
                .contains("<span class=\"c-kw\">plugin</span>"), "xml tag names");
        assertTrue(SiteGen.Highlighter.highlight("name: tddoc", "yaml")
                .contains("<span class=\"c-kw\">name</span>"), "yaml keys");
        assertEquals("plain &amp; safe", SiteGen.Highlighter.highlight("plain & safe", "unknown-lang"),
                "unknown languages escape without highlighting");
    }

    @Test
    void cli_flags_win_over_config_and_paths_resolve_against_the_config_file(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectories(tmp.resolve("project"));
        Path docs = Files.createDirectories(project.resolve("docs"));
        Files.writeString(docs.resolve("CfgDocTest.java"), """
                /// ---
                /// title: Configured
                /// slug: configured
                /// order: 1
                /// summary: From config.
                /// ---
                ///
                /// Hello.
                ///
                /// [landing]
                class CfgDocTest {
                    @""" + """
                Test
                    void truth() {
                        assert true;
                    }
                }
                """);
        Files.writeString(project.resolve("tddoc.yml"), """
                name: fromconfig
                docs: docs
                out: generated
                """);

        SiteGen.main(new String[]{
                "--config", project.resolve("tddoc.yml").toString(),
                "--name", "fromflag",
        });

        Path site = project.resolve("generated");
        assertTrue(Files.exists(site.resolve("index.html")), "out path resolves against the yml");
        assertTrue(Files.readString(site.resolve("index.html")).contains("fromflag"),
                "CLI flag beats config value");
    }
}
