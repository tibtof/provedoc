# provedoc — project constitution

## What this is

Doctest-first article tooling for the JVM: JUnit test files in, static article
site out. Every code example on a generated page is the body of a real `@Test`
that passed before the page was built, so examples cannot rot.

This is layer 1 of a three-layer plan (tool → protocol → aggregator site); the
full roadmap is in `~/notes/livingexamples/plan.md`. The engine was extracted
from [fforj](https://github.com/fforj/fforj), whose live site
([fforj.dev](https://fforj.dev)) it generates; fforj is meant to become
consumer #1 of this tool once generalization is complete (its ADR-5 has the
original design rationale).

## Locked decisions

| Decision | Choice | Reason |
|---|---|---|
| Language | Java 21 baseline, compiled `--release 21`, toolchain 25 in dev | Same reach reasoning as fforj |
| Runtime deps | **Zero.** `java.base` only | The copy-paste contract below |
| Single-file contract | `SiteGen.java` MUST stay one file, runnable via `java SiteGen.java` | Any repo can copy the file and own it forever; both the Maven artifact and the pasted file must always work |
| Config parsing | Hand-rolled, stdlib only — no YAML/JSON libs | Zero-dep rule; see issue #2 for the flat provedoc.yml subset |
| Test framework | JUnit Jupiter only, no mocking, no assertion libs | fforj house rules |
| Coordinates | `dev.provedoc:provedoc` | `provedoc.dev` was available 2026-08-06 — register it before publishing |

## The format (what doc-tests look like)

- Prose in `///` line-comment markdown blocks; first block carries front matter
  (`title`, `slug`, `order`, `summary`) between `---` fences.
- Examples are bodies of real `@Test` methods; `// site:include` before a member
  renders it as code too; a prose line exactly `[landing]` marks the homepage
  example (required: SiteGen throws without one).
- Brace counting is heuristic: no unbalanced braces inside string literals.
- `src/test/java/dev/provedoc/docs/FirstArticleDocTest.java` is the living
  reference — it renders the README screenshot and runs in the suite.

## Gotchas (learned the hard way)

- **SiteGen.java contains four raw NUL bytes** (highlighter placeholder
  sentinels in string literals). BSD grep therefore treats it as binary and
  silently matches nothing: always `grep -a` on this file. `file` calls it
  "data"; that's expected, not corruption.
- Regenerating the README screenshot: build, run SiteGen with
  `--docs src/test/java/dev/provedoc/docs --install '...'`, serve `build/example-site`,
  capture at 1200px CSS width. On a display with fractional scaling (dpr 1.25),
  Chrome's full-page captures come out cropped — emulate dpr 1 and take a
  viewport (not fullPage) shot sized to the content.
- The `site:include`/front-matter parsing has no unit tests yet; the doc-test
  plus the fforj site are the only coverage. Issue #2's parser work should
  start `SiteGenTest.java`.

## Current state and roadmap

- Branding is generalized behind flags: `--name`, `--tagline`, `--repo`,
  `--glyph`, `--install` (renders only when given; `{version}` substituted),
  `--editBase`, plus the original `--docs/--out/--javadoc/--version/--prefix/--channel`.
- Roadmap issues, in recommended order: #3 design-token CSS refactor (do
  first, enables the rest), #4 dark/light toggle (depends on #3), #2 flat
  provedoc.yml config (independent, any time).
- Still fforj-shaped: the CSS itself (rubrication design), the versioned-deploy
  workflow templates, and Kotlin/Groovy source support.
- The fforj switchover (its `site` task consuming this tool) is the proof of
  generalization; record it in fforj's `decisions.md` when it happens.

## Conventions

Conventional commits. Test names `snake_case_describing_behavior`. One public
type per file. Javadoc with an intent paragraph on public members. No `null`
returns. Branch naming `docs/…`, `feat/…`, `fix/…`; PRs, never direct pushes
to main.
