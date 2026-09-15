# Launch the Workbench with complete Project lifecycle

Status: resolved
Source: [GitHub #98](https://github.com/evildarkarchon/BS2BG/issues/98)
GitHub state at migration: closed (completed)
Created: 2026-08-29T07:12:21Z
Updated: 2026-08-29T11:06:20Z
Closed: 2026-08-29T11:06:20Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 07

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Launch the Workbench with complete Project lifecycle.

## Acceptance criteria

- [ ] The Workbench is the sole application entry point and exposes placeholder Templates, Morphs, NPC Database, Output, and Settings Areas over one authoritative Project flow.
- [ ] New, Open, Save, and Save As operate through ProjectSession with correct dirty state, file identity, recovery diagnostics, and failure preservation.
- [ ] Close and shutdown prompts preserve unsaved work and reject late callbacks without introducing a second writable Project route.
- [ ] The replaced legacy root shell and equivalent Project lifecycle routes are removed in this checkpoint.
- [ ] The full Java 25 gate remains green and the packaged Preview launcher exercises startup, New, Open, Save, Save As, failure recovery, and shutdown.

## Blocked by

- [#87](07-prove-the-packaged-codec-cutover-and-remove-minimal-json.md)

## Comments

No comments at migration.
