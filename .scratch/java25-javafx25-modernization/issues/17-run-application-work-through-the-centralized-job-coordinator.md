# Run application work through the centralized job coordinator

Status: resolved
Source: [GitHub #101](https://github.com/evildarkarchon/BS2BG/issues/101)
GitHub state at migration: closed (completed)
Created: 2026-08-29T07:12:31Z
Updated: 2026-08-30T00:59:36Z
Closed: 2026-08-30T00:59:36Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 16

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Run application work through the centralized job coordinator.

## Acceptance criteria

- [ ] A JavaFX-independent coordinator admits one application-wide job and exposes truthful phase-aware progress and cancellation.
- [ ] A real Workbench operation demonstrates captured inputs, cancellation safe points, commit-phase refusal, stale-completion rejection, and retry linkage.
- [ ] Activity records and diagnostics distinguish success, cancellation, stale completion, and failure without modal dialogs except job failures requiring action.
- [ ] Observer failures are isolated, late callbacks are rejected, and coordinated shutdown cannot publish abandoned work.
- [ ] Deterministic race tests and packaged UI tests verify admission, progress, cancellation, retry, staleness, and shutdown.

## Blocked by

- [#100](16-deliver-workbench-themes-and-accessible-platform-behavior.md)

## Comments

No comments at migration.
