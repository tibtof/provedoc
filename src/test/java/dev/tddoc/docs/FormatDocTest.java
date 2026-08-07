package dev.tddoc.docs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ---
/// title: The format, feature by feature
/// slug: format
/// order: 2
/// summary: Front matter, prose blocks, included members, and the helpers that stay backstage.
/// ---
///
/// A doc-test is an ordinary JUnit file named `*DocTest.java`. Three kinds of
/// lines matter to the generator; everything else is just Java.
///
/// ## Front matter
///
/// The first `///` block opens with `---` fences carrying four keys:
/// `title`, `slug` (the page's file name), `order` (position in the guide
/// list), and `summary` (shown on the landing page). You are reading the
/// result of this file's front matter right now.
///
/// ## Prose is a markdown subset
///
/// `///` blocks render as markdown: `##` and `###` headings, `-` bullet
/// lists, **bold**, *italic*, `inline code`, and
/// [links](https://github.com/tddoc/tddoc). That is the whole subset — it
/// covers articles without dragging a markdown library into the single file.
///
/// ## Members join the article with site:include
///
/// A test body alone rarely tells the story. Any member preceded by a
/// `// site:include` line renders as a code block at that point in the
/// article — records, interfaces, whole helper classes:
class FormatDocTest {

    // site:include
    record Invoice(String customer, List<Long> cents) {
        long total() {
            return cents.stream().mapToLong(Long::longValue).sum();
        }
    }

    /// The example that uses it is a real test — if `Invoice` drifts, the
    /// build breaks before the page does:
    @Test
    void an_included_record_backs_the_example() {
        var invoice = new Invoice("ACME", List.of(1200L, 99L));
        assertEquals(1299L, invoice.total());
    }

    /// ## Helpers stay backstage
    ///
    /// Everything *without* the marker is invisible to readers but still
    /// compiles and runs. Use plain private methods for setup you do not want
    /// cluttering the article — the next example calls one you cannot see on
    /// this page (`fibonacci`, look in the
    /// [source](https://github.com/tddoc/tddoc/blob/main/src/test/java/dev/tddoc/docs/FormatDocTest.java)):
    @Test
    void hidden_helpers_keep_examples_short() {
        assertTrue(fibonacci(10) == 55);
    }

    private static long fibonacci(int n) {
        long a = 0, b = 1;
        for (int i = 0; i < n; i++) {
            long next = a + b;
            a = b;
            b = next;
        }
        return a;
    }

    /// ## One page is the landing example
    ///
    /// Exactly one prose line across your docs must be `[landing]` (in square
    /// brackets, alone on its line): the test that follows becomes the code
    /// sample on the homepage. This site's lives in the
    /// [first article](first-article.html). Forget it and the build fails —
    /// deliberately, so a site cannot ship without a front door.
    ///
    /// ## Limits worth knowing
    ///
    /// - Example extraction counts braces, so avoid unbalanced `{` or `}`
    ///   inside string literals in test bodies.
    /// - Prose lives in *line* comments, so javadoc never sees it; your API
    ///   docs stay clean.
    @Test
    void the_generator_is_honest_about_its_limits() {
        String balanced = "{ok}";
        assertEquals(4, balanced.length());
    }
}
