# Generate and inspect captured Project output

Status: resolved
Source: [GitHub #105](https://github.com/evildarkarchon/BS2BG/issues/105)
GitHub state at migration: closed (completed)
Created: 2026-08-29T07:12:44Z
Updated: 2026-09-01T06:19:02Z
Closed: 2026-09-01T06:19:02Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 20

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Generate and inspect captured Project output.

## Acceptance criteria

- [ ] Generate captures one immutable Project snapshot and generation-settings basis for Templates, Morphs, and BoS artifacts.
- [ ] Fresh results populate their Output tabs and may reveal the drawer without stealing focus; stale, cancelled, failed, or superseded results never publish.
- [ ] Project content changes invalidate generated output while save-only, unchanged, rejected, and failed outcomes do not.
- [ ] Generation uses central job progress, cancellation, Activity, retry, and safe-shutdown contracts.
- [ ] Packaged tests verify accepted output semantics, freshness, focus, cancellation, staleness, tab navigation, and read-only text accessibility.

## Blocked by

- [#104](20-import-bodyslide-presets-and-manage-settings-in-the-workbench.md)

## Comments

No comments at migration.
