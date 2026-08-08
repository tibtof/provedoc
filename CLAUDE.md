# tddoc — project constitution

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
| Config parsing | Hand-rolled, stdlib only — no YAML/JSON libs | Zero-dep rule; see issue #2 for the flat tddoc.yml subset |
| Test framework | JUnit Jupiter only, no mocking, no assertion libs | fforj house rules |
| Coordinates | `dev.tddoc:tddoc` | `tddoc.dev` registered 2026-08-06 (owned) — Central Portal namespace verification (DNS TXT) still pending before first publish |

## The format (what doc-tests look like)

- Prose in `///` line-comment markdown blocks; first block carries front matter
  (`title`, `slug`, `order`, `summary`) between `---` fences.
- Examples are bodies of real `@Test` methods; `// site:include` before a member
  renders it as code too; a prose line exactly `[landing]` marks the homepage
  example (required: SiteGen throws without one).
- Brace counting is heuristic: no unbalanced braces inside string literals.
- `src/test/java/dev/tddoc/docs/FirstArticleDocTest.java` is the living
  reference — it renders the README screenshot and runs in the suite.

## Gotchas (learned the hard way)

- SiteGen.java used to contain four raw NUL bytes (code-span sentinels);
  they are `"\0"` escapes since the nested-class refactor, so plain grep
  works now. Don't reintroduce raw control bytes — use escapes.
- SiteGen.java is one FILE but not one flat class: nested statics `Config`,
  `Parser`, `Markdown`, `Highlighter`, `Html`, `Watcher`. The single-file
  contract is unaffected; keep new code inside the matching nested class.
- Regenerating the README screenshot: build, run SiteGen with
  `--docs src/test/java/dev/tddoc/docs --install '...'`, serve `build/example-site`,
  capture at 1200px CSS width. On a display with fractional scaling (dpr 1.25),
  Chrome's full-page captures come out cropped — emulate dpr 1 and take a
  viewport (not fullPage) shot sized to the content.
- The `site:include`/front-matter parsing has no unit tests yet; the doc-test
  plus the fforj site are the only coverage. Issue #2's parser work should
  start `SiteGenTest.java`.

## Current state and roadmap

- Released: 0.1.0 (core), 0.2.x (Gradle/Maven plugins as thin wrappers over
  SiteGen.main, tddoc.yml as primary config, --watch, recursive discovery),
  0.3.0 (design tokens + six built-in themes + --css/--style, multi-language
  fence highlighting, dark/light toggle, neutral defaults). jbang catalog:
  `jbang tddoc@tddoc`. Docs site auto-deploys to GitHub Pages on push to main.
- Config: tddoc.yml (flat subset, parsed by SiteGen.readConfig — public,
  plugins reuse it). Precedence: flag/explicit plugin config > yml > default.
  Yml-relative paths resolve against the yml's directory.
- Open issues: #13 Kotlin autodetect, #25 tddoc.dev DNS (user action), #8/#20
  parked.
- Still fforj-shaped: the versioned-deploy workflow templates and
  Kotlin/Groovy source support.
- The fforj switchover (its `site` task consuming this tool) is the proof of
  generalization; record it in fforj's `decisions.md` when it happens.

## Conventions

Conventional commits. Test names `snake_case_describing_behavior`. One public
type per file. Javadoc with an intent paragraph on public members. No `null`
returns. Branch naming `docs/…`, `feat/…`, `fix/…`; PRs, never direct pushes
to main.
