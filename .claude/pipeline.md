# cobots pipeline

tier: light
verify: ./gradlew check
changelog: none
adr: none
language: java

## Deviations

- Zero runtime dependencies is a locked decision (see CLAUDE.md) — any new
  dependency proposal is an automatic full-tier issue with an explicit human
  sign-off, regardless of size.
- `SiteGen.java` single-file contract: refactors touching it must keep it
  runnable via `java SiteGen.java`; the doc-test suite is the proof.
- Design history lives in CLAUDE.md's locked-decisions table and issue threads;
  no separate ADR log at this size (hence `adr: none`).
