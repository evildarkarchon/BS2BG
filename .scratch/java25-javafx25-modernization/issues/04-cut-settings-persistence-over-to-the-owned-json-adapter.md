# Cut Settings persistence over to the owned JSON adapter

Status: resolved
Source: [GitHub #84](https://github.com/evildarkarchon/BS2BG/issues/84)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:06:48Z
Updated: 2026-08-29T07:20:48Z
Closed: 2026-08-29T07:20:48Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 03

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Load and save Standard and UUNP Settings through the owned adapter as one validated, atomic unit while preserving all output-affecting behavior and backward tolerance.

## Acceptance criteria

- [ ] Both Settings sources parse completely into one detached immutable candidate before either profile becomes live.
- [ ] Defaults, multipliers, inversion behavior, omitted endpoints, finite numeric conversion, unknown-member warnings, and the Omit Redundant Sliders preference retain their accepted observable meaning.
- [ ] Duplicate fixed fields or dynamic Slider keys, non-finite values, malformed input, and resource-limit failures reject with stable file/member diagnostics and preserve the prior live Settings.
- [ ] Canonical paired writes are staged and published atomically with backup and recovery behavior; partial publication is impossible.
- [ ] The replaced Settings JSON production route is deleted with no dormant fallback.
- [ ] All inherited evidence remains green and the packaged launcher exercises legacy Settings import, editing, persistence, failure recovery, and output consumption.
- [ ] Packaged exit evidence is retained, and the issue is a whole-checkpoint revert boundary.

## Blocked by

- [#83](03-cut-bos-output-over-to-the-owned-json-writer.md)

## Comments

No comments at migration.
