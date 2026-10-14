---
status: accepted
date: 2026-10-13
---

# Tiers 1-2 run headless; Tier 3 is the only desktop-bound tier

BS2BG verifies in three tiers: Tier 1 is the developer loop (`.\mvnw.cmd test`), Tier 2 is the complete gate
(`tools/java25/verify-java25.ps1`), and Tier 3 is the packaged UI Automation smoke run
(`tools/java25/package-java25.ps1`). Until now Tiers 1 and 2 ran the JavaFX tests on Windows glass, so every test
Stage appeared on the user's desktop, and a click or a focus change during the run could fail a test.

Tiers 1 and 2 now run on JavaFX 27's built-in headless platform with the software pipeline
(`glass.platform=headless`, `prism.order=sw`), which [ADR-0007](0007-javafx-27-is-the-javafx-baseline.md) adopted
JavaFX 27 for. Surefire sets both properties, so both tiers inherit them and no test window reaches the desktop.
Tier 3 is the only desktop-bound tier. It stays opt-in and hands-off: it runs on a clean committed checkout and
nobody touches the desktop while it runs. Its scope is the accessibility behaviours BS2BG decided on, as listed in
[Accessibility behaviours the packaged UIA smoke run must keep proving](https://github.com/evildarkarchon/BS2BG/issues/128).
A developer who needs to watch a Tier 1 run on the desktop uses the explicitly activated `desktop-fx` Maven profile,
which Tier 2 rejects.

## Fidelity trade-off

The headless platform has no OS-active window, a fixed screen, empty platform preferences and no UI Automation
provider. So the tiers prove different things:

- Tier 1 proves the *mechanism* of each workaround and contract as plain logic, without depending on the desktop.
  Focus preservation follows
  [Focus preservation semantics without the OS-active window](https://github.com/evildarkarchon/BS2BG/issues/123):
  production must not depend on `isFocused()` or deferred focus requests, so that Tier 1 can check focus
  headless. The focus fix removes the coupling that is still in production today.
- Tier 3 proves the UI Automation *outcome* on the real desktop, in the packaged image.
- Narrator speech stays human release evidence; neither tier claims it.

## Enforcement

The headless split must not drift back silently. These mechanisms are decided in
[Headless wiring and desktop-dependence guard for Tiers 1-2](https://github.com/evildarkarchon/BS2BG/issues/129):

- **Surefire properties and their pom pin.** `pom.xml` sets the two properties in Surefire `systemPropertyVariables`
  (not the `argLine`, not `.mvn/jvm.config`). `ProductionSourceGateTest` fails if either is removed, or if the
  `desktop-fx` profile stops being an explicit opt-in that removes exactly those two entries.
- **The gate rejects `desktop-fx`.** `verify-java25.ps1` refuses `-Pdesktop-fx` and any `glass.platform`,
  `prism.order` or `bs2bg.test.desktop` argument, the same way it refuses skip flags.
- **The `FxTestToolkit` guard.** Before `Platform.startup`, it asserts both properties unless
  `bs2bg.test.desktop=true`, and fails with an error that names `desktop-fx`. This catches a command-line `-D`,
  which overrides Surefire's entries. It reads public system properties only: no glass-class check and no screen
  fingerprint. An accessibility tripwire after each test (`Platform.isAccessibilityActive()` must stay false
  headless) lands with the test-harness hardening.
- **The class-file production-ban gate.** A gate test reads production class files and bans desktop-dependent
  calls such as `isFocused()` and deferred focus requests. It lands with the focus fix and the appearance-source
  seam.
- **No skipped tests.** A test that does not fit headless is rewritten to be deterministic or deleted from Tier 1.
  It is never skipped, because the gate forbids skipped tests. A Tier 3 counterpart is added only when it proves a
  decision BS2BG made.

A test-side workaround for an upstream headless-platform bug carries its JBS id and fix release and is retired by
a tripwire, as described in `docs/build/workbench-platform.md`.

## Considered options

- **Leaving Tiers 1-2 on the desktop.** Rejected: test windows take over the user's desktop, and a run fails if
  the user touches it.
- **Headless alone, without fixing the production focus coupling.** Rejected: under headless, windows never lose
  focus, so the `isFocused()` dependence would pass vacuously and the bug would be masked rather than fixed.
- **TestFX Monocle, or a self-built Monocle.** Rejected in issue #131 and ADR-0007: version skew on JavaFX 25, an
  unmaintained build, or owning a build of an internal module.
- **A docs-only rule, a source-regex check, or ArchUnit** for the production bans. Rejected: a docs rule is not
  enforced; a regex cannot tell `Stage.toFront` from `Node.toFront` and misses multi-line calls; ArchUnit is a new
  dependency that lags new class-file versions.

## Consequences

- `.\mvnw.cmd test` and `verify-java25.ps1` show no window, so the desktop stays usable during Tiers 1-2.
- `.\mvnw.cmd test -Dglass.platform=win` fails fast with a message that names `desktop-fx`; the desktop is reached
  only through `-Pdesktop-fx`, which prints a one-line warning.
- Behaviour the headless platform cannot express, such as UIA activation and real OS focus, is proven only at
  Tier 3. Each JavaFX bump re-proves the headless harness on Tier 2, as ADR-0007 requires.
