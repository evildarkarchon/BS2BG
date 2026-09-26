# Cut Project writing over to the owned JSON adapter

Status: resolved
Source: [GitHub #85](https://github.com/evildarkarchon/BS2BG/issues/85)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:06:50Z
Updated: 2026-08-29T08:35:59Z
Closed: 2026-08-29T08:35:59Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 04

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Save Projects through the owned canonical JSON writer without changing valid `.jbs2bg` meaning or weakening atomic file publication.

## Acceptance criteria

- [ ] Project writing preserves the accepted schemas, canonical domain order, display casing, legal repeated NPC display-name members, nullable endpoints, and omission of unchanged synthesized defaults.
- [ ] Semantic read/write/read compatibility passes the permanent corpus; Project whitespace and member order are not elevated into compatibility contracts.
- [ ] Writing stages and flushes complete bytes before forced atomic replacement, and every failure preserves the pre-command destination and current Project lifecycle state.
- [ ] Only ProjectSession publishes lifecycle outcomes; the package-private Project remains the final integrity authority.
- [ ] The old Project writer production route is deleted with no runtime switch or dormant fallback.
- [ ] All inherited evidence remains green and the packaged launcher exercises New, Save, Save As, reopen, overwrite failure, and recovery scenarios.
- [ ] Packaged exit evidence is retained, and the issue is a whole-checkpoint revert boundary.

## Blocked by

- [#84](04-cut-settings-persistence-over-to-the-owned-json-adapter.md)

## Comments

No comments at migration.
