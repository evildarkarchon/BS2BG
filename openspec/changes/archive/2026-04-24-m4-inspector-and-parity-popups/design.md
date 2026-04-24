## Context

M4 ports high-use v1 popups into the new single-window workspace where possible. The SetSliders editor moves into the inspector, while image and no-preset surfaces keep their parity window behavior.

## Goals / Non-Goals

**Goals:**
- Add inspector-based SetSliders editing with batch actions and live preview.
- Add BoS JSON view using Core writer output.
- Add image lookup/viewing and no-preset notifier behavior.
- Preserve v1 modal/window dimensions where a secondary window remains necessary.

**Non-Goals:**
- No final app-wide export menu work; that belongs to M5.
- No advanced visual redesign beyond fitting these parity surfaces into the workspace.

## Decisions

- Implement SetSliders as a reusable inspector control first; create a secondary window wrapper only if layout testing shows the inspector is too cramped.
- Put batch slider mutations on the preset editor ViewModel so undo/redo can wrap them later in M6.
- Keep image discovery relative to current working directory `images/` for v1 compatibility.
- Generate BoS text in Core and expose it as read-only ViewModel state to avoid UI formatting drift.

## Risks / Trade-offs

- Inspector density can become poor at 590x600 parity content -> test minimum window size early and use a secondary window fallback if needed.
- Always-on-top windows can be disruptive -> reserve always-on-top for parity image/no-preset windows only.
- BoS formatting has distinct numeric behavior -> reuse M0 formatter tests and add selected-preset JSON snapshots.

## Migration Plan

No file migration occurs. SetSliders edits mutate existing project data and are saved through M1 persistence.

## Open Questions

- Decide after layout testing whether SetSliders stays inspector-only or also ships a parity secondary window.
