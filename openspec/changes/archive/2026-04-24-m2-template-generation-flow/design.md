## Context

M2 makes Flow A usable after M0 formatting and M1 project persistence exist. The workflow spans Core XML parsing/generation and App ViewModels for preset management and live preview.

## Goals / Non-Goals

**Goals:**
- Parse BodySlide XML files using `XDocument` and Java-compatible selection rules.
- Add preset list ViewModels and UI for import, selection, profile, omit redundant, preview, and generation.
- Generate template text through Core services only.
- Cover real-world XML observations from PRD section 9a.

**Non-Goals:**
- No SetSliders full editor; M2 can rely on existing imported values and basic state.
- No BodyGen INI file export command; M2 produces/copies generated text.

## Decisions

- Treat XML parsing as a Core service returning domain models and import diagnostics; UI commands handle file picking and busy state.
- Keep sparse slider values sparse on import and resolve defaults only when rendering through profile-aware formatter code.
- Sort preset names case-insensitively in the ViewModel collection while preserving stored names verbatim.
- Drive preview from reactive derived state so profile and omit toggles invalidate output consistently.

## Risks / Trade-offs

- Real BodySlide XMLs contain optional declarations and unknown attributes -> tests must include both minimal and sampled real-world forms.
- Duplicate names update existing presets in v1 -> implement update-in-place carefully to preserve references.
- Clipboard APIs changed in Avalonia 12 -> isolate clipboard interaction behind an app service so tests can stub it.

## Migration Plan

M2 uses the M1 project model. Imported presets become normal project data and are saved by the existing project-file path.

## Open Questions

- Confirm whether duplicate XML preset import should preserve original collection position or re-sort immediately after update.
