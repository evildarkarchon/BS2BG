---
status: superseded by ADR-0002
date: 2026-08-26
superseded: 2026-08-28
---

# Deepen Project state behind ProjectSession

> Superseded by [ADR-0002](0002-project-aggregate-internal-seam.md). This decision predates the Java 25 / JavaFX 25
> modernization; its "preserving Java 8" clause is no longer a live constraint — the build targets `--release 25`
> (issue #94). The `ProjectSession` interface, immutable snapshots, and the rejected options below still hold and are
> carried forward by ADR-0002, which places the immutable `Project` aggregate behind that same seam.

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
