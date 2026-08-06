# provedoc

**Docs that are proven, not promised.**

provedoc turns executable tests into published articles. Prose and code live in
the same test file; every example on the resulting site is the body of a real
test that ran in CI. Examples cannot rot: if one breaks, the build breaks, and
the broken version never reaches your readers.

This is more than API documentation. The format is built for *articles* —
guides, tutorials, release walkthroughs — where the narrative interleaves with
code that provably works, version by version.

## Status

Early extraction. The generator was proven end to end as the documentation
engine of [fforj](https://github.com/fforj/fforj) (live at
[fforj.dev](https://fforj.dev)): doc-tests, versioned snapshots per release with
real behavioral diffs between them, and a GitHub Pages deploy where a failing
example can never ship. This repo is that engine becoming a general tool.

Current milestone: generalize the fforj-specific parts (branding, source
locations, theming) behind configuration while keeping the copy-paste contract
below.

## The format

Articles are ordinary JUnit test files (doc-tests):

- Prose lives in `///` markdown comment blocks — plain line comments, so they
  compile on Java 21 and stay invisible to javadoc.
- Every example is the body of a real `@Test` method, run by your normal suite.
- A member preceded by `// site:include` is rendered as code too.
- The first `///` block carries front matter (`title`, `slug`, `order`,
  `summary`); a `[landing]` marker picks the homepage example.

The generator parses the doc-tests, interleaves prose and code in source order,
and renders a static site — markdown subset, small Java highlighter, javadoc
folded in under `/api/`, and per-release version snapshots with a live version
selector.

## The contract

One file, zero dependencies. `SiteGen.java` runs via the plain source launcher:

```bash
java SiteGen.java --version 1.2.3 --javadoc build/docs/javadoc --out build/site
```

You can depend on the artifact, or copy the single file into your repo and own
it forever. Both must always work; anything that breaks the copy-paste story is
out of scope.

## The bigger picture

provedoc is layer one of a three-layer idea:

1. **This tool** — doctest-first article tooling for the JVM.
2. **A protocol** — a language-agnostic format contract plus a provenance
   attestation (repo, commit, CI run, suite result), so "verified" is
   independently checkable by anyone.
3. **A front page** — an aggregator of living articles from repos that adopt
   the format, each with its verification badge and version selector.

Each layer is useful even if the next never happens.

## License

MIT
