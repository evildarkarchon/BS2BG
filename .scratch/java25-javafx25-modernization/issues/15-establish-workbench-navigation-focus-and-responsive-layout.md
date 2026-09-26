# Establish Workbench navigation, focus, and responsive layout

Status: resolved
Source: [GitHub #99](https://github.com/evildarkarchon/BS2BG/issues/99)
GitHub state at migration: closed (completed)
Created: 2026-08-29T07:12:24Z
Updated: 2026-08-29T21:52:17Z
Closed: 2026-08-29T21:52:17Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 14

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Establish Workbench navigation, focus, and responsive layout.

## Acceptance criteria

- [ ] Rail navigation reaches Templates, Morphs, NPC Database, Output, and Settings through typed semantic navigation.
- [ ] Focus enters each Area predictably, F6 traversal is coherent, and closing overlays or secondary surfaces returns focus to their semantic launcher.
- [ ] The Output drawer preserves accepted reveal, resize, and focus behavior without changing the active Area unexpectedly.
- [ ] At the accepted narrow breakpoint, side content becomes an overlay while the Workbench remains usable at 800x600 and 100%, 125%, and 150% DPI.
- [ ] Packaged pointer-free tests verify navigation, focus restoration, drawer behavior, narrow mode, and minimum geometry.

## Blocked by

- [#98](14-launch-the-workbench-with-complete-project-lifecycle.md)

## Comments

No comments at migration.
