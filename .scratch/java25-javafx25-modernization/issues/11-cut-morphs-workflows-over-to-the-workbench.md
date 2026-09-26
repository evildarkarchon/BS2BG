# Cut Morphs workflows over to the Workbench

Status: resolved
Source: [GitHub #91](https://github.com/evildarkarchon/BS2BG/issues/91)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:07:04Z
Updated: 2026-08-29T07:14:31Z
Closed: 2026-08-29T07:14:31Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 10
Resolution: superseded; see imported comments for replacement tickets

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Deliver complete Custom Morph Target and NPC Morph Assignment authoring inside the Workbench Morphs Area, preserving Project identities, visible-set behavior, portrait capability, and output semantics.

## Acceptance criteria

- [ ] Custom Morph Target and NPC Morph Assignment create, edit, remove, clear, filter, sort, selection, relationship, validation, and reporting workflows are complete.
- [ ] The two selection kinds remain explicit and mutually exclusive, track stable logical identity, clear when hidden or removed, and never silently retarget.
- [ ] Fill Empty captures exactly the visible empty NPC Morph Assignments and independently chooses from the eligible selected Slider Presets; assigned-preset editing and feedback preserve referential integrity.
- [ ] The inspector portrait follows selection, uses the accepted legacy filename fallbacks including `.jpeg`, remains responsive, and exposes a dedicated accessible viewer when opened.
- [ ] Changed surfaces meet the accepted keyboard, F6/F7, flyout dismissal, focus restoration, accessible semantics, non-color, High Contrast, DPI, narrow-mode, and 800x600 criteria.
- [ ] Delete every replaced Morphs, Fill Empty, assigned-preset, image, no-preset, notification, and related popup/controller route.
- [ ] All inherited evidence remains green and the packaged launcher exercises pointer-free authoring, filters, visible-set commands, Fill Empty, portraits, validation, and output continuity.
- [ ] Packaged exit evidence is retained, and the issue is a whole-checkpoint revert boundary.

## Blocked by

- [#90](10-cut-output-workflows-over-to-the-workbench.md)

## Comments

### evildarkarchon — 2026-08-29T07:14:31Z

Source: https://github.com/evildarkarchon/BS2BG/issues/91#issuecomment-5461003863
Updated: 2026-08-29T07:14:31Z

Superseded by the approved Morphs slices [#107](23-author-custom-morph-targets-in-morphs.md), [#108](24-author-npc-morph-assignments-in-morphs.md), and [#109](25-complete-morphs-bulk-actions-and-portrait-inspection.md).
