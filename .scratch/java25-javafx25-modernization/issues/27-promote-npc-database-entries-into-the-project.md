# Promote NPC Database entries into the Project

Status: ready-for-agent
Source: [GitHub #111](https://github.com/evildarkarchon/BS2BG/issues/111)
GitHub state at migration: open (no closure reason)
Created: 2026-08-29T07:13:03Z
Updated: 2026-08-29T07:13:03Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 26

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Promote NPC Database entries into the Project.

## Acceptance criteria

- [ ] Add and Add All create independent NPC Morph Assignments without sharing mutable NPC Database state.
- [ ] Promotion preserves Project identity and validation rules, reports duplicates or rejected entries completely, and keeps database selection stable.
- [ ] Back to Morphs preserves semantic launcher and return context, including focus and the promoted NPC Morph Assignment when appropriate.
- [ ] The final legacy NPC Database route, mutable compatibility adapter, and related popup/controller paths are removed.
- [ ] Packaged tests verify Add, Add All, duplicate handling, Project save/reopen, and semantic return to Morphs.

## Blocked by

- [#110](26-import-and-inspect-npc-database-sources.md)

## Comments

No comments at migration.
