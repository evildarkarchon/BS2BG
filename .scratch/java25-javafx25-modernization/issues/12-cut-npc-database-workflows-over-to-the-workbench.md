# Cut NPC Database workflows over to the Workbench

Status: resolved
Source: [GitHub #92](https://github.com/evildarkarchon/BS2BG/issues/92)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:07:07Z
Updated: 2026-08-29T07:14:33Z
Closed: 2026-08-29T07:14:33Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 11
Resolution: superseded; see imported comments for replacement tickets

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Deliver the independent, session-scoped NPC Database Area with transactional source imports, inspection, filtering, promotion into the Project, and semantic return to Morphs.

## Acceptance criteria

- [ ] Sources, catalog table, filters, sorting, identity-stable selection, inspector, portrait, import diagnostics, clear, Add, and Add All workflows are complete.
- [ ] Each selected NPC source file parses transactionally with accepted trimming, field tolerance, race handling, fallback display names, Form ID normalization, deduplication, import order, and deterministic UTF-8 fallback.
- [ ] Cancellation publishes none of the current source file, preserves prior committed sources truthfully, and uses the central job, Activity, progress, retry, and shutdown contracts.
- [ ] Adding an NPC creates an NPC Morph Assignment without sharing mutable database state; Add/Add All and Back to Morphs preserve semantic launcher and return context.
- [ ] Upgrade or replace charset detection in the owning parser workflow and verify the resulting dependency and packaged runtime closure.
- [ ] Changed surfaces meet the accepted keyboard, F6, focus, UI Automation identity, non-color, High Contrast, DPI, narrow-mode, and 800x600 criteria.
- [ ] Delete the final legacy NPC Database route, mutable compatibility adapter, and related popup/controller paths.
- [ ] All inherited evidence remains green and the packaged launcher exercises source success, malformed and cancelled imports, filtering, inspection, Add/Add All, and return-to-Morphs.
- [ ] Packaged exit evidence is retained, and the issue is a whole-checkpoint revert boundary.

## Blocked by

- [#91](11-cut-morphs-workflows-over-to-the-workbench.md)

## Comments

### evildarkarchon — 2026-08-29T07:14:32Z

Source: https://github.com/evildarkarchon/BS2BG/issues/92#issuecomment-5461003985
Updated: 2026-08-29T07:14:32Z

Superseded by the approved NPC Database slices [#110](26-import-and-inspect-npc-database-sources.md) and [#111](27-promote-npc-database-entries-into-the-project.md).
