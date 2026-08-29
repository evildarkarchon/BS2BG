---
status: accepted
date: 2026-08-28
supersedes: the "preserving Java 8" clause of ADR-0001 only
---

# Java 25 LTS is the application baseline

ADR-0001 committed the `ProjectSession` design to "preserving Java 8 and semantic compatibility with existing `.jbs2bg` files". BS2BG now builds, tests, and ships on stable, pinned Java 25 LTS (Eclipse Temurin 25.0.4.1+1) with JavaFX 25.0.4, compiled with `--release 25` and full lint enforcement, with preview features and JavaFX incubator modules disabled; the application is delivered as a self-contained, non-modular Windows x64 `jpackage` app-image whose launcher is a plain `main` class that starts the one existing `Main` application. This decision replaces only ADR-0001's Java 8 baseline clause: the `ProjectSession` interface, immutable snapshots, `.jbs2bg` semantic compatibility, and every rejected option in ADR-0001 remain accepted, and ADR-0002 (the internal `Project` aggregate seam) is unchanged.

## Considered options

- Staying on Java 8 was rejected because the JavaFX 8 build depended on private skin, Modena, and toolkit internals that no longer exist as public API, and because no supported JavaFX runtime for Java 8 remains available as a pinned, checksum-verifiable input.
- Java 26 (or later non-LTS releases) as the bundled runtime was rejected because the destination is an LTS baseline; a newer JDK may bootstrap Maven, but compilation, tests, `jlink`, and `jpackage` always run on the pinned Temurin 25.
- A modular (`module-info`) application with `jlink`-only delivery was rejected because the application and its dependencies are classpath artifacts today; the runtime is linked from the pinned JMODs and the application stays non-modular on the launcher classpath.
- Enabling preview or incubator APIs to reach newer JavaFX controls was rejected because the baseline is the stable surface only; the build fails closed if either appears.

## Consequences

- Every toolchain input is an explicit, reviewed lock (`tools/java25/toolchain-lock.json`, the Maven Wrapper pins, `pom.xml` plugin and dependency versions); "latest" is never an input.
- `tools/java25/verify-java25.ps1` is the complete application gate and `tools/java25/package-java25.ps1` is the packaging checkpoint on top of it; a source-filtered build or a partially smoke-tested image is not a result.
- Java 25 language and API features are available to production code, but JavaFX is used only through its public API (ADR-0001's JavaFX-independent `ProjectSession` seam is what made the toolkit migration mechanical).
