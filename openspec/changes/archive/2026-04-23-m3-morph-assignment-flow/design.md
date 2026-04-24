## Context

M3 adds the Morphs workflow for NPC and custom target assignments. It relies on M1 project state and M2 preset data but does not yet implement all advanced column checklist filtering promised for v2.

## Goals / Non-Goals

**Goals:**
- Add Core NPC import, custom target validation, assignment, and morph writer services.
- Add Morphs ViewModels/UI for NPC table, custom targets, assignment panels, count badge, and basic search filter.
- Implement Fill Empty and visible-row scoped Clear Assignments.
- Generate morph text compatible with v1.

**Non-Goals:**
- No advanced per-column checklist filter; M3 ships search-only filtering.
- No multiselect assignment or undo/redo; those are M6 upgrades.

## Decisions

- Parse NPC files in Core with a small encoding detection layer: BOM, UTF-8, then Windows default fallback.
- Keep NPC de-dupe as a service operation keyed by lowercased `(mod, editorId)` to match Java semantics.
- Represent visible filter state in the Morphs ViewModel and pass visible NPCs explicitly to bulk operations.
- Keep random assignment behind an injectable random provider so tests can be deterministic.

## Risks / Trade-offs

- Encoding fallback can produce surprising characters -> expose a non-blocking diagnostic when fallback decoding is used.
- Large NPC lists may stress filtering -> begin with Avalonia DataGrid virtualization and measure before adding caching.
- Random assignment is hard to test -> seed or substitute the random provider in tests.

## Migration Plan

Existing projects gain no schema change. Imported NPCs and target assignments are persisted through the M1 project model.

## Open Questions

- Confirm whether search-only filtering should match all displayed columns or just name/mod/editor ID/form ID for the initial M3 slice.
