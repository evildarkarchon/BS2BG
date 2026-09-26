# Copy and transactionally export Output artifacts

Status: resolved
Source: [GitHub #106](https://github.com/evildarkarchon/BS2BG/issues/106)
GitHub state at migration: closed (completed)
Created: 2026-08-29T07:12:47Z
Updated: 2026-09-01T08:49:26Z
Closed: 2026-09-01T08:49:26Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 21

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Copy and transactionally export Output artifacts.

## Acceptance criteria

- [ ] Copy and export consume the same defensively owned accepted bytes displayed by the Output drawer.
- [ ] Batch export preflights complete mappings, unsafe names, case-insensitive collisions, and target conflicts before publishing any artifact.
- [ ] Export stages and publishes the complete batch transactionally, preserving prior destinations on cancellation or failure.
- [ ] The central job path reports truthful export phases, cancellation, retry, diagnostics, Activity, and safe shutdown.
- [ ] Commons IO and all replaced legacy generation, preview, copy, BoS, and export routes are removed, and packaged tests cover exact bytes and atomic failure.

## Blocked by

- [#105](21-generate-and-inspect-captured-project-output.md)

## Comments

No comments at migration.
