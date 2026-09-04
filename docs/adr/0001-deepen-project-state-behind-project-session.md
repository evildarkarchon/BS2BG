---
status: accepted; superseded by ADR-0003 wherever it conflicts with the Java 25 baseline (today: the "preserving Java 8" clause)
date: 2026-08-26
amended: 2026-08-28
---

# Deepen Project state behind ProjectSession

> This ADR predates the Java 25 modernization. Wherever it conflicts with the Java 25 baseline it is superseded by
> [ADR-0003](0003-java-25-baseline-for-the-packaged-application.md): the application baseline is stable, pinned
> Java 25 LTS with JavaFX 25 and no preview or incubator use, delivered as a self-contained Windows app-image.
> Today the only such conflict is the "preserving Java 8" clause below; no other sentence here names a Java or
> JavaFX version, a toolchain, or a delivery format, and the rejected options and consequences (JavaFX kept out of
> the `ProjectSession` interface, a Maven and JUnit 5 foundation) point the same way as the Java 25 work. Every
> other decision — the `ProjectSession` interface, immutable snapshots, `.jbs2bg` semantic compatibility, and the
> rejected options — remains accepted. [ADR-0002](0002-project-aggregate-internal-seam.md) builds on this decision
> by placing the immutable `Project` aggregate behind the same seam; it does not replace it.

BS2BG will replace the shallow, publicly mutable `Data` state and controller-owned lifecycle rules with one JavaFX-independent `ProjectSession` module. Its small interface exposes immutable snapshots, explicit lifecycle operations, and one `apply(ProjectEdit)` entry; typed outcomes distinguish changed, unchanged, rejected, and failed operations and carry structured diagnostics. The implementation owns Project mutations, referential-integrity cascades, dirty and file identity transitions, atomic new/open/save behavior, BodySlide XML imports, canonical ordering, and atomic bulk edits, preserving Java 8 and semantic compatibility with existing `.jbs2bg` files.

## Considered options

- Separate Project state and file workflows behind two external seams was rejected because their invariants and lifecycle transitions change together.
- Retaining JavaFX collections in the interface was rejected because it would keep mutation leakage and make JavaFX part of the test surface.
- Exposing a filesystem port was rejected because the filesystem is local-substitutable and can be tested through the external interface with temporary directories; another production adapter is not justified.
- Moving random selection, the NPC Database, user preferences, slider configuration, or generated outputs into `ProjectSession` was rejected because those concerns do not belong to Project integrity or lifecycle.

## Consequences

- Presentation code schedules synchronous, thread-safe operations and renders the snapshot returned with each outcome.
- Templates, Morphs, BoS output, and exports capture one immutable Project snapshot per workflow; generation caches, scheduling, clipboard behavior, and artifact writing remain outside `ProjectSession`.
- Failed open or save operations preserve the current Project, file identity, and dirty state. Recoverable missing Slider Preset assignments produce diagnostics and leave the recovered Project dirty.
- The NPC Database remains independent; adding an NPC creates an NPC Morph Assignment rather than sharing mutable state.
- Migration begins with a repeatable Maven and JUnit 5 test foundation, then replaces behavior incrementally through the `ProjectSession` interface instead of layering pass-through modules over `Data`.
