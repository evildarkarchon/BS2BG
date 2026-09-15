# Import and inspect NPC Database sources

Status: ready-for-agent
Source: [GitHub #110](https://github.com/evildarkarchon/BS2BG/issues/110)
GitHub state at migration: open (no closure reason)
Created: 2026-08-29T07:13:00Z
Updated: 2026-08-29T07:13:00Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 25

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Import and inspect NPC Database sources.

## Acceptance criteria

- [ ] The session-scoped NPC Database provides source management, catalog filtering and sorting, identity-stable selection, inspection, portraits, diagnostics, and clear behavior.
- [ ] Each selected source parses transactionally with accepted trimming, field tolerance, fallback names, Form ID normalization, deduplication, import order, and deterministic UTF-8 fallback.
- [ ] Imports use the central job path; cancellation publishes none of the current source while preserving prior committed sources truthfully.
- [ ] Malformed sources, races, progress, retry, failure, cancellation, Activity, and shutdown produce accepted observable outcomes.
- [ ] Packaged tests cover successful, malformed, mixed, failed, and cancelled imports plus keyboard, accessibility, theme, DPI, and responsive behavior.

## Blocked by

- [#109](25-complete-morphs-bulk-actions-and-portrait-inspection.md)

## Comments

No comments at migration.
