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
