# Author NPC Morph Assignments in Morphs

Status: resolved
Source: [GitHub #108](https://github.com/evildarkarchon/BS2BG/issues/108)
GitHub state at migration: open (no closure reason)
Created: 2026-08-29T07:12:53Z
Updated: 2026-08-29T07:12:53Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 23

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Author NPC Morph Assignments in Morphs.

## Acceptance criteria

- [x] Users can create, edit, remove, clear, filter, sort, select, validate, and report NPC Morph Assignments.
- [x] Custom Morph Target and NPC Morph Assignment selections remain explicit, mutually exclusive, and stable by logical identity.
- [x] Assigned Slider Preset editing preserves referential integrity and handles rename, removal, hidden rows, and rejected edits without silent retargeting.
- [x] The Workbench exposes accepted NPC identity, race, Form ID, display-name, and Slider Preset assignment semantics.
- [x] Packaged tests verify complete keyboard and pointer authoring, validation, filtering, relationship changes, and generated-output continuity.

## Blocked by

- [#107](23-author-custom-morph-targets-in-morphs.md)

## Comments

No comments at migration.

### Completion — 2026-09-22

Implemented NPC Morph Assignment creation and Slider Preset relationship editing in the Morphs Workbench through
`ProjectSession`, with separate identity-stable NPC and Custom Morph Target catalogs. Manual authoring normalizes
Form IDs before publication, rejects output-breaking plugin names, and reports required, malformed, and duplicate
values without changing the Project. Selection, filtering, sorting, confirmation, and Project refreshes preserve
plugin/editor identity without retargeting.

The clean checkpoint from `6bfed46336717bfeffca91ec68335c1d3a6025eb` passed 474 Java tests without skips,
132 PowerShell tests, and all 21 packaged workflows at 100% display scale; the launcher exited with code 0.
[Retained evidence](../../../docs/build/evidence/windows-app-image-2026-09-22-npc-morphs-100-percent/README.md)
includes the exact image and archive hashes, generated-output and save/reopen checks, UIA trees, and screenshots.
The Standards and Spec review findings were addressed; a low-risk duplicate type-ahead loop remains a reviewer
judgement call, not an acceptance gap.
