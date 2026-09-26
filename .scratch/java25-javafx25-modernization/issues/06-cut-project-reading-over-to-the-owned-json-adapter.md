# Cut Project reading over to the owned JSON adapter

Status: resolved
Source: [GitHub #86](https://github.com/evildarkarchon/BS2BG/issues/86)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:06:53Z
Updated: 2026-08-29T09:03:05Z
Closed: 2026-08-29T09:03:05Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 05

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Open legacy and current Projects through the owned streaming reader while preserving valid Project meaning, recovery behavior, diagnostics, and atomic session publication.

## Acceptance criteria

- [ ] The reader enforces fixed-schema unknown/duplicate rules, dynamic name identity, legal repeated NPC display names, NPC identity uniqueness, missing-versus-null distinctions, and exact signed-32-bit integer lexemes.
- [ ] Malformed, unsupported, duplicate, ambiguous, trailing, or resource-limit input rejects transactionally with stable source, path, line, and column diagnostics where available.
- [ ] Recoverable missing Slider Preset relationships accumulate complete ordered diagnostics and publish a valid dirty recovered Project only after final aggregate integrity validation.
- [ ] Charset detection and deterministic UTF-8 fallback retain accepted behavior, and failed Open preserves the active Project, file identity, dirty state, and lifecycle.
- [ ] The old Project reader production route is deleted with no runtime switch or dormant fallback.
- [ ] All inherited evidence remains green and the packaged launcher exercises valid legacy Projects, recovery, malformed input, cancellation-safe publication, and save/reopen compatibility.
- [ ] Packaged exit evidence is retained, and the issue is a whole-checkpoint revert boundary.

## Blocked by

- [#85](05-cut-project-writing-over-to-the-owned-json-adapter.md)

## Comments

No comments at migration.
