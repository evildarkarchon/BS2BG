# Cut Templates workflows over to the Workbench

Status: resolved
Source: [GitHub #89](https://github.com/evildarkarchon/BS2BG/issues/89)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:07:00Z
Updated: 2026-08-29T07:14:27Z
Closed: 2026-08-29T07:14:27Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 08
Resolution: superseded; see imported comments for replacement tickets

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Deliver every Slider Preset and Settings workflow inside the Workbench Templates Area, with identity-stable filtering and selection, in-place editing, and coordinated import behavior.

## Acceptance criteria

- [ ] Slider Preset create, duplicate, rename, remove, clear, profile switching, slider-choice editing, previews, validation, and reporting work through immutable feature intents and frames.
- [ ] BodySlide import, Settings, Set Sliders, gang controls, All-Min/All-Max, inline F2 rename, and their diagnostics and feedback are complete.
- [ ] Filtering, sorting, selection, type-ahead, and visible-set bulk commands use stable Slider Preset identity and the accepted JavaFX-independent reducer semantics.
- [ ] Long-running import uses the central job path with truthful partial-source outcomes, cancellation safe points, focus preservation, and Activity records.
- [ ] Changed surfaces meet the accepted keyboard, F6, accessible-name/state, non-color, High Contrast, DPI, narrow-mode, and 800x600 criteria.
- [ ] Delete every replaced Templates, Set Sliders, rename, and related popup/controller route; no second writable Templates route remains.
- [ ] All inherited evidence remains green and the packaged launcher exercises complete pointer-free Templates workflows, import, filtering, editing, failure, and cancellation.
- [ ] Packaged exit evidence is retained, and the issue is a whole-checkpoint revert boundary.

## Blocked by

- [#88](08-introduce-the-workbench-shell-and-platform-modules.md)

## Comments

### evildarkarchon — 2026-08-29T07:14:27Z

Source: https://github.com/evildarkarchon/BS2BG/issues/89#issuecomment-5461003597
Updated: 2026-08-29T07:14:27Z

Superseded by the approved Templates slices [#102](18-browse-and-manage-slider-presets-in-templates.md), [#103](19-edit-slider-preset-choices-in-templates.md), and [#104](20-import-bodyslide-presets-and-manage-settings-in-the-workbench.md).
