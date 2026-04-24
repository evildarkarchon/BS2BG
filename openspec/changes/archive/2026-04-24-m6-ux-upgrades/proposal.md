## Why

After parity is complete, BS2BG can improve the workflows that made the JavaFX app slow or awkward without changing file semantics. M6 adds the v2 UX upgrades promised by the PRD while keeping them behind the already-verified behavior.

## What Changes

- Add global search, drag-and-drop import/open targets, and command palette.
- Add multiselect assignment workflows for the NPC table.
- Add ViewModel-level undo/redo for user mutations.
- Upgrade the basic NPC table filter to a column checklist filter.
- Add theme selection and the final dark/light/system visual polish from the PRD.
- Add actionable preset-count warnings and inline custom-target validation.

## Capabilities

### New Capabilities
- `ux-upgrades`: Defines post-parity workflow improvements including search, DnD, palette, multiselect, undo/redo, filtering, and theme controls.

### Modified Capabilities

## Impact

- Adds cross-cutting UI services for command discovery, undo/redo, drag-and-drop, and theme preferences.
- Adds ViewModel and UI tests for enhanced workflows without altering Core file output.
- Expands manual QA to cover dense workflows and theme variants.
