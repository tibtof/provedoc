package dev.tddoc.docs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// ---
/// title: Your first proven article
/// slug: first-article
/// order: 1
/// summary: Prose in /// comments, examples as real tests. An example that breaks stops the build.
/// ---
///
/// This page is generated from an ordinary JUnit test file. The prose you are
/// reading lives in `///` comment blocks; every code example below is the body
/// of a real `@Test` method that ran, and passed, before this page was built.
/// An example that stops being true stops the build, so it can never reach a
/// reader.
///
/// Anything your article needs beyond the examples, mark with `// site:include`
/// and it renders as code too:
class FirstArticleDocTest {

    // site:include
    sealed interface Shipment {
        record InTransit(String location) implements Shipment {}
        record Delivered(String signedBy) implements Shipment {}
    }

    /// ## Examples are tests
    ///
    /// The reader sees a code block; CI sees an assertion. Both see the same
    /// file, so they cannot disagree:
    ///
    /// [landing]
    @Test
    void pattern_matching_reads_the_state() {
        Shipment s = new Shipment.Delivered("T. Reader");
        String status = switch (s) {
            case Shipment.InTransit(String where) -> "via " + where;
            case Shipment.Delivered(String who) -> "signed by " + who;
        };
        assertEquals("signed by T. Reader", status);
    }

    /// ## Prose and code interleave in source order
    ///
    /// Write the article top to bottom: a `///` block, then a test, then more
    /// prose. The generator keeps that order on the page, so the file is the
    /// article. And because examples are executable, they earn sentences prose
    /// alone could not, like this one about text blocks:
    @Test
    void text_blocks_strip_incidental_indentation() {
        String motto = """
                proven,
                not promised""";
        assertEquals("proven,\nnot promised", motto);
    }
}
