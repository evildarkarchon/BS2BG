---
status: accepted
date: 2026-10-13
supersedes: ADR-0003 only for its JavaFX version clause ("with JavaFX 25.0.4")
---

# JavaFX 27 is the JavaFX baseline

ADR-0003 fixed the application baseline as pinned Java 25 LTS (Eclipse Temurin 25.0.4.1+1) "with JavaFX 25.0.4".
BS2BG now builds, tests, and ships on JavaFX 27: `javafx.version` is `27` in `pom.xml`, and
`tools/java25/toolchain-lock.json` pins the Gluon `openjfx-27_windows-x64_bin-jmods.zip` by SHA-256, with the
OpenJFX `27+30` source tag and revision, so the packaged runtime is linked from the JavaFX 27 JMODs. This decision
supersedes only ADR-0003's JavaFX version clause. Everything else in ADR-0003 stays accepted: Temurin 25 LTS
remains the toolchain and bundled runtime, every build input is an explicit reviewed lock and never "latest", and
preview and incubator APIs stay banned. JavaFX 27's own preview features, such as `StageStyle.EXTENDED`, stay
off, and the `org.openjfx:javafx-incubator-*` enforcer ban and the guard tests are unchanged.

The reason is the headless test platform. JavaFX 27 ships a built-in headless glass platform
(`-Dglass.platform=headless -Dprism.order=sw`), so Tiers 1 and 2 can run without putting a window on the user's
desktop and without a third-party Monocle build. The probe in issue #132 ran the whole suite (568 tests) on
JavaFX 27 under both Windows glass and the built-in headless platform: everything passed, compilation stayed clean
under `-Xlint:all -Werror`, and every enforcer rule held, with no source change. The cost is the support window.
Open-source JavaFX feature releases get public updates only until the next feature release, about six months,
whereas Gluon's JavaFX 25 line is an LTS. The project accepts that cost in exchange for the built-in headless
platform.

## The JavaFX version is independent of the JDK

The JavaFX version is versioned independently of the JDK (Temurin 25 LTS). The two are separate pins: nothing ties
`javafx.version` in `pom.xml`, or the `javafx` block of `toolchain-lock.json`, to the JDK version, and no check that
keeps them in step should be added. A JavaFX bump does not touch the JDK pin, and a JDK update does not touch the
JavaFX pin. The only consistency that matters for JavaFX is between `javafx.version` in `pom.xml`, the lock's
`javafx` block and the Pester version assert, which move together in one reviewed bump (below).

## Policy for later JavaFX feature releases

- Each JavaFX feature release (28, 29, ...) is adopted as an explicit, reviewed lock bump: `javafx.version` in
  `pom.xml`, the `javafx` block of `toolchain-lock.json` (URL, SHA-256, source URL and revision, archive root),
  and the Pester version assert move together in one change.
- A bump is accepted only when Tiers 1–3 are green on it: `.\mvnw.cmd test`, `tools/java25/verify-java25.ps1`,
  and `tools/java25/package-java25.ps1` with its UI Automation smoke run.
- Each bump re-proves the headless harness on Tier 2, because the headless platform is new and its behaviour
  (fixed screen geometry, empty platform preferences, focus) may change between feature releases.

## Considered options

- Staying on Gluon's JavaFX 25 LTS line with TestFX Monocle 21.0.2 was rejected (issue #131). That Monocle is
  built for JavaFX 21 and unmaintained since 2024-02, and it shows version skew on 25: D3D throws
  `AbstractMethodError`, so it needs `prism.order=sw`, and it left `BufferOverflowException`s on the FX thread in
  the fractional render-scale tests at its default geometry (issue #121). It would also leave the project on a
  stopgap that a future 25.0.x update could break silently.
- Monocle built from `jfx25u` sources was rejected because it would mean owning a build of an internal module and
  re-pinning it on every JavaFX update.
- JavaFX 26 was rejected because it is already superseded by JavaFX 27; 26.0.2 is its last update. Both require
  only JDK 25, so 27 costs nothing extra.

## Consequences

- Tier 1 and Tier 2 can move to the built-in headless platform without extra jars or `--add-exports`.
- JavaFX feature releases must be adopted on roughly a six-month cadence to stay on a publicly updated line; each
  is a reviewed lock change gated by Tiers 1–3, as above.
- The JavaFX 25 UI Automation workarounds stay in place: the shared ones in `UiaListHelper`, the single-Area ones
  (the rename edit lifecycle, slider-row reuse, the NPC Database HelpText mirror) in their Areas, as
  `docs/build/workbench-platform.md` lists, and the smoke-side ones in `tools/java25`. The Windows UIA provider did
  not change through JavaFX 27; removing any of them needs a Tier 3 check first.
