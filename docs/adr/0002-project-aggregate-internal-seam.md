---
status: accepted
date: 2026-08-27
---

# Project aggregate is an internal seam of ProjectSession

`DefaultProjectSession` grew to 1 886 lines with every edit handler copying, scanning, re-sorting, and republishing three parallel lists, and with Custom Morph Target and NPC Morph Assignment relationship editing and cascades written twice. BS2BG will introduce a package-private, immutable `Project` value inside the `project` package that owns Project content only: the three collections, their canonical case-insensitive order, lookup by name or identity, and referential integrity between Slider Presets and the values that reference them. Operations return either a new `Project` or one structured diagnostic, and return the same instance when nothing changed. The external `ProjectSession` interface and `ProjectSnapshot` do not change; `Project` is an internal seam that only the session and its own package tests cross.

## Considered options

- Owning slider-choice validation and UUNP default rebuilding inside `Project` was rejected because those rules depend on slider configuration, which stays outside Project integrity; `Project` replaces a Slider Preset by name and the caller validates its choices.
- Owning lifecycle state (file identity, dirty, recovered) inside `Project` was rejected because lifecycle transitions are session rules and `ProjectSnapshot` already validates them; `Project` builds a snapshot from content plus lifecycle supplied by the session.
- Reworking the `apply` dispatch chain into per-family modules in the same change was rejected because it would obscure whether a regression came from the aggregate; it may follow once handlers are one-line delegations.
- Having `ProjectFileLoader` assemble through `Project` was rejected for this step because loader diagnostics carry source locations that `Project` results do not; consolidating name rules into value types is a separate decision.
- A mutable working copy frozen per operation was rejected in favour of an immutable value because the package idiom is immutable values swapped under one lock, and "unchanged" then falls out of instance identity.
- A shared interface implemented by the public snapshot types was rejected in favour of an internal generic referrer helper so the public interface of `CustomMorphTargetSnapshot` and `NpcMorphAssignmentSnapshot` does not widen.

## Consequences

- Migration proceeds one edit family per commit — Slider Presets with the import upsert, then Custom Morph Targets, then NPC Morph Assignments, then lifecycle — with `mvn verify` green after each step and the existing session tests unchanged.
- `Project.from(snapshot)` re-validates uniqueness and dangling references and throws, so a loader regression fails loudly instead of publishing a broken Project.
- A small package-private `ProjectTest` covers only the contracts handlers rely on: same instance on no-op, cascades on rename, remove, and clear, canonical order after every operation, and that a dangling reference is not constructible.
- Lookups stay linear behind `Project`; indexing is a private decision to revisit when measured.
