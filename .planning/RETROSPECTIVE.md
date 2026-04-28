# Retrospective

## Milestone: v1.0 - MVP

**Shipped:** 2026-04-28
**Phases:** 7 | **Plans:** 51 | **Tasks:** 115

### What Was Built

- Profile-correct generation with distinct bundled profile semantics, legacy project compatibility, neutral fallback state, and release-facing FO4 calibration context.
- Workflow reliability features for remembered folders/preferences, stable NPC filtering, scoped bulk operations, and bounded snapshot-based undo/redo.
- Read-only diagnostics, no-mutation import previews, export previews, overwrite-risk confirmation, and atomic write outcome ledgers.
- Custom profile validation, AppData storage, project embedding, recovery diagnostics/actions, and first-class Profiles workspace editing/filtering.
- CLI/headless generation, portable project bundles, deterministic assignment strategies, release trust packaging, setup docs, custom-profile catalog composition, and saved strategy replay in automation outputs.

### What Worked

- Keeping Core generation/export paths shared across GUI, CLI, and bundle workflows prevented automation drift.
- Verification and audit gap closure phases caught real cross-phase problems before milestone close.
- Request-scoped services avoided global catalog mutation while preserving custom profile semantics.

### What Was Inefficient

- Some roadmap/progress fields drifted while phase summaries and requirements were complete, requiring cleanup during close.
- The generated milestone entry was too granular and needed manual condensation for long-term readability.
- Manual UAT artifacts for early UI phases lagged behind automated verification and had to be explicitly waived.

### Patterns Established

- Archive live roadmap/requirements at milestone boundaries to keep planning context small.
- Treat CLI, GUI, and bundle output as one shared Core behavior surface with targeted byte-level regression tests.
- Carry manual verification waivers explicitly in milestone audit/state rather than losing them in phase history.

### Key Lessons

- Cross-phase automation paths need end-to-end replay/catalog tests, not only service-level unit tests.
- Custom profile behavior must be reasoned about per request to avoid silent fallback or stale global state.
- Docs-only close automation still needs human review because summaries and archive granularity can be noisy.

### Cost Observations

- Model mix: not tracked.
- Sessions: not tracked.
- Notable: Small, plan-scoped phases made verification tractable, but milestone closure needs explicit archive normalization.

## Cross-Milestone Trends

| Milestone | Shipped | Phases | Plans | Notes |
|-----------|---------|--------|-------|-------|
| v1.0 MVP | 2026-04-28 | 7 | 51 | First post-M0-M7 GSD milestone; all v1 requirements complete. |
