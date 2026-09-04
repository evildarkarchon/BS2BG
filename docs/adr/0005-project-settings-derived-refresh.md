---
status: accepted
date: 2026-09-01
---

# ProjectSession may re-derive Project choices from published Settings

ADR-0001 keeps Settings drafts, configuration, loading, and persistence outside `ProjectSession`. That boundary
remains accepted, but `ProjectSession` now exposes the narrow no-argument `refreshSettings()` operation so existing
Project Slider choices can be re-derived after another owner successfully publishes Settings. The operation captures
exactly one immutable `Settings.snapshot()` generation and uses it for every preset, synthesized identity, and
explicit-null endpoint in that refresh; the session retains no Settings field or draft state.

This is not `apply(ProjectEdit)`: no user-authored Project intent or caller-selected value is being applied. It is a
re-projection of current Project content from application configuration the Slider-choice rules already consume, so
it preserves file identity, lifecycle, and the existing dirty flag instead of manufacturing a user edit. Observable
choice changes advance `ProjectContentVersion`; an identical re-projection returns the same snapshot and version.

The process-global published Settings source is accepted because `Settings` already owns one atomically published
application generation and every existing Slider-default consumer reads that source. Passing an arbitrary Settings
value through `ProjectSession` would widen its external contract and blur configuration ownership, while repeated
global reads could mix generations during one multi-preset rebuild; pinning one snapshot avoids both outcomes.
