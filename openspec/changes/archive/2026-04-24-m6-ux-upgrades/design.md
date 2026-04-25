## Context

M6 comes after parity. It improves workflow speed and discoverability without changing Core file semantics or generated output.

## Goals / Non-Goals

**Goals:**
- Add global search, DnD, command palette, multiselect assignment, undo/redo, column checklist filtering, theme selection, inline validation, and preset-count warnings.
- Keep output formats and project compatibility unchanged.
- Add tests around ViewModel behavior and smoke coverage for new UI paths.

**Non-Goals:**
- No new body-morph math, built-in xEdit parsing, 3D preview, cloud features, or auto-backup history.
- No release packaging work; that is M7.

## Decisions

- Use command metadata from existing ReactiveCommands to populate the command palette rather than maintaining a separate action list.
- Implement undo/redo as ViewModel-level mutation records with inverse actions, not as serialized project history.
- Extend the M3 filter implementation into a column predicate collection that composes search and checklist filters.
- Persist theme preference through the existing preferences service.

## Risks / Trade-offs

- Undo/redo can become inconsistent if mutations bypass commands -> keep user-initiated changes behind command helpers.
- Drag-and-drop may overlap with file picker import logic -> share the same import/open services and diagnostics.
- Column checklist filtering can be expensive on large NPC lists -> measure with 5k rows and cache distinct values if needed.

## Migration Plan

No file format migration. Preferences may gain theme and UI options with safe defaults when missing.

## Open Questions

- Decide whether undo history clears after Save or only after project replacement.
