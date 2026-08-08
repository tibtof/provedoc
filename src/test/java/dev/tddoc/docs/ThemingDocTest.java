package dev.tddoc.docs;

import dev.tddoc.SiteGen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// ---
/// title: Theming
/// slug: theming
/// order: 4
/// summary: Built-in themes, and how ~15 lines of CSS variables rebrand the whole site.
/// ---
///
/// The stylesheet is built on CSS custom properties — colors, font stacks,
/// radii — declared once on `:root`. Everything below is just different
/// values for those tokens.
///
/// ## Built-in themes
///
/// Pick with `--theme` (or `theme:` in tddoc.yml). Each ships light and
/// dark palettes; the header toggle and the OS preference both work in all
/// of them.
///
/// | Theme | Look |
/// | --- | --- |
/// | `rubric` | The default: early-print serifs, green accent |
/// | `manuscript` | rubric with its original rubrication red |
/// | `terminal` | Mono everywhere, phosphor green, square corners |
/// | `academic` | Black on white, one dark-red accent, paper-like |
/// | `plain` | System fonts, neutral blue, no webfont requests |
/// | `slate` | Cool grays, restrained blue, corporate-neutral |
///
/// ## Roll your own with --css
///
/// `--css <file>` appends after the built-in sheet, so a handful of
/// variable overrides is a complete rebrand. Some starting points — copy
/// one into `brand.css` and pass `--css brand.css` (or `css: brand.css`
/// in tddoc.yml):
///
/// Solarized:
///
/// ```css
/// :root {
///   --paper: #FDF6E3; --ink: #073642; --rubric: #268BD2; --muted: #93A1A1;
///   --rule: #EEE8D5; --code-bg: #EEE8D5; --card: #FDFDF6;
/// }
/// :root[data-theme="dark"] {
///   --paper: #002B36; --ink: #93A1A1; --rubric: #2AA198; --muted: #586E75;
///   --rule: #073642; --code-bg: #073642; --card: #03303C;
/// }
/// ```
///
/// Nord:
///
/// ```css
/// :root {
///   --paper: #ECEFF4; --ink: #2E3440; --rubric: #5E81AC; --muted: #6B7386;
///   --rule: #D8DEE9; --code-bg: #E5E9F0; --card: #FFFFFF;
/// }
/// :root[data-theme="dark"] {
///   --paper: #2E3440; --ink: #D8DEE9; --rubric: #88C0D0; --muted: #7B88A1;
///   --rule: #3B4252; --code-bg: #3B4252; --card: #353C4A;
/// }
/// ```
///
/// Dracula (dark-first — pair with `--css` and the dark default):
///
/// ```css
/// :root, :root[data-theme="light"] {
///   --paper: #282A36; --ink: #F8F8F2; --rubric: #BD93F9; --muted: #9BA2C0;
///   --rule: #44475A; --code-bg: #21222C; --card: #2E3040;
/// }
/// ```
///
/// Gruvbox:
///
/// ```css
/// :root {
///   --paper: #FBF1C7; --ink: #3C3836; --rubric: #B57614; --muted: #7C6F64;
///   --rule: #EBDBB2; --code-bg: #F2E5BC; --card: #FFFBEB;
/// }
/// :root[data-theme="dark"] {
///   --paper: #282828; --ink: #EBDBB2; --rubric: #FABD2F; --muted: #A89984;
///   --rule: #3C3836; --code-bg: #32302F; --card: #2E2C2B;
/// }
/// ```
///
/// Sepia (long-form reading warmth):
///
/// ```css
/// :root {
///   --paper: #F7F1E3; --ink: #43382C; --rubric: #A0522D; --muted: #8A7B68;
///   --rule: #E8DCC6; --code-bg: #EFE6D4; --card: #FCF8EE;
/// }
/// ```
///
/// Ink (brutalist black and white):
///
/// ```css
/// :root {
///   --paper: #FFFFFF; --ink: #000000; --rubric: #000000; --muted: #444444;
///   --rule: #000000; --code-bg: #F0F0F0; --card: #FFFFFF;
///   --radius: 0; --radius-card: 0; --radius-chip: 0; --radius-inline: 0;
/// }
/// ```
///
/// ## Total control with --style
///
/// `--style <file>` replaces the sheet entirely — the generated class names
/// become your contract, and updates to the built-in design no longer reach
/// you. Prefer `--css` unless you truly want to own the whole look.
///
/// ## Proof
///
/// This test builds a site with every built-in theme and fails if any of
/// them stops generating — the table above cannot list a theme that does
/// not exist:
class ThemingDocTest {

    @Test
    void every_built_in_theme_generates_a_site(@TempDir Path tmp) throws Exception {
        Path docs = Files.createDirectories(tmp.resolve("docs"));
        Files.writeString(docs.resolve("SampleDocTest.java"), sampleDocTest());

        for (var theme : List.of("rubric", "manuscript", "terminal", "academic", "plain", "slate")) {
            Path out = tmp.resolve(theme);
            SiteGen.main(new String[]{
                    "--docs", docs.toString(),
                    "--out", out.toString(),
                    "--name", "themed",
                    "--theme", theme,
            });
            assertTrue(Files.exists(out.resolve("style.css")), theme + " generates");
            if (!theme.equals("rubric")) {
                assertTrue(Files.readString(out.resolve("style.css")).contains("theme: " + theme),
                        theme + " overrides are in the sheet");
            }
        }
    }

    private static String sampleDocTest() {
        List<String> prose = List.of(
                "---",
                "title: Sample",
                "slug: sample",
                "order: 1",
                "summary: Minimal.",
                "---",
                "",
                "Hello.",
                "",
                "[landing]");
        String header = prose.stream().map(l -> ("/// " + l).strip())
                .collect(Collectors.joining("\n"));
        return String.join("\n",
                header,
                "class SampleDocTest {",
                "    @" + "Test",
                "    void truth() {",
                "        assert true;",
                "    }",
                "}");
    }
}
