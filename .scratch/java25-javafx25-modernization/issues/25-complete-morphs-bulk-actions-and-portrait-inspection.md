# Complete Morphs bulk actions and portrait inspection

Status: resolved
Source: [GitHub #109](https://github.com/evildarkarchon/BS2BG/issues/109)
GitHub state at migration: open (no closure reason)
Created: 2026-08-29T07:12:57Z
Updated: 2026-08-29T07:12:57Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 24

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Complete Morphs bulk actions and portrait inspection.

## Acceptance criteria

- [x] Fill Empty captures exactly the visible empty NPC Morph Assignments and independently selects from eligible chosen Slider Presets.
- [x] The Fill Empty flyout supports accepted keyboard dismissal, focus restoration, cancellation, validation, and feedback behavior.
- [x] The inspector portrait follows stable selection, supports accepted filename fallbacks including jpeg, remains responsive, and opens an accessible dedicated viewer.
- [x] Every replaced Morphs, Fill Empty, assigned-preset, image, no-preset, notification, and related popup/controller route is removed.
- [x] Packaged tests verify visible-set bulk behavior, portraits, accessibility, focus, themes, DPI, narrow mode, and output continuity.

## Blocked by

- [#108](24-author-npc-morph-assignments-in-morphs.md)

## Comments

No comments at migration.

### Completion — 2026-09-22

Implemented Fill Empty as a light-dismiss Morphs flyout with a frozen visible-empty NPC scope, independent draws
from selected eligible Slider Presets, no second confirmation, and Workbench validation and Activity feedback. The
selection-following inspector portrait uses the accepted filename fallbacks (including `.jpeg`), loads the image in
the background, and opens an accessible dedicated viewer. Removed the unmounted legacy popup/controller,
notification, image, no-preset, and dark-style routes.

The clean checkpoint from `83e5a1c08417b3a0ae0b5880299ef0a9ecb26471` passed 481 Java tests with no skips, 133
PowerShell tests, and all 23 packaged workflows at 100%, 125%, and 150% display scale. Each launcher exited with
code 0, and the original 100% scale was restored. [Retained evidence](../../../docs/build/evidence/windows-app-image-2026-09-22-morphs-bulk-dpi-matrix/README.md)
includes the image and archive hashes, matrix reports, UIA trees, and screenshots.
