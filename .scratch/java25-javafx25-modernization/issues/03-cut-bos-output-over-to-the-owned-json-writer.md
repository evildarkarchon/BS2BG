# Cut BoS output over to the owned JSON writer

Status: resolved
Source: [GitHub #83](https://github.com/evildarkarchon/BS2BG/issues/83)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:06:45Z
Updated: 2026-08-29T05:08:07Z
Closed: 2026-08-29T05:08:07Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 02

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Generate, preview, copy, and export BoS JSON from the repository-owned canonical writer while preserving every accepted output byte and filename behavior.

## Acceptance criteria

- [ ] BoS output preserves UTF-8, whitespace, newline, member ordering, escaping, numeric spelling, omission, and one-artifact-per-Slider-Preset semantics against the permanent goldens.
- [ ] Preview and export consume the same defensively owned canonical bytes so displayed and written output cannot diverge.
- [ ] Non-finite values, unsafe filenames, and case-insensitive filename collisions are rejected before any artifact publishes, with complete mappings and diagnostics.
- [ ] The old BoS production route is deleted after cutover; no dormant runtime switch or second production writer remains.
- [ ] All inherited compatibility evidence remains green and the packaged launcher exercises BoS generation, preview, copy, and export.
- [ ] Packaged exit evidence is retained, and reverting this issue restores the preceding verified writer route as one checkpoint.

## Blocked by

- [#82](02-establish-permanent-json-compatibility-oracles-and-the-jackson-adapter.md)

## Comments

No comments at migration.
