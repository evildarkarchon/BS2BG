# Phase 7: Replay Saved Strategies in Automation Outputs - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-28
**Phase:** 07-replay-saved-strategies-in-automation-outputs
**Areas discussed:** Bundle project state, Blocked NPC policy, Replay visibility, Replay trigger

---

## Bundle Project State

| Option | Description | Selected |
|--------|-------------|----------|
| Original project | Keep the bundled project as the saved source of truth, with the strategy config intact. Outputs are reproducible because Phase 7 replays the strategy on generation. | yes |
| Replay assignments | Save the replayed NPC assignments into the bundled project so `morphs.ini` and `project.jbs2bg` visibly match, but the bundle mutates/share-saves generated state. | |
| Add replay snapshot | Keep original project and add a separate report/snapshot describing replayed assignments, avoiding mutation while making generated state inspectable. | |

**User's choice:** Original project
**Notes:** Bundle `project/project.jbs2bg` should remain the source project rather than serialized replay side effects.

---

## Blocked NPC Policy

| Option | Description | Selected |
|--------|-------------|----------|
| Fail before writing | Treat blocked NPCs as automation blockers for BodyGen output, avoiding partial or stale morphs.ini results. | yes |
| Succeed with report | Generate outputs for eligible NPCs and list blocked NPCs as cautions; less disruptive but easier to miss in scripts. | |
| Clear blocked rows | Clear assignments for blocked NPCs, generate remaining output, and report exactly which rows were omitted. | |

**User's choice:** Fail before writing
**Notes:** Strategy gaps should block CLI/bundle BodyGen output before files or zip entries are written.

---

## Replay Visibility

| Option | Description | Selected |
|--------|-------------|----------|
| Summary counts | Report strategy kind, assigned count, and zero blocked rows. Detailed NPC listings only appear on blocked/failure paths. | yes |
| Silent success | Keep current quiet success style unless replay fails; simplest for scripts but less trust-building. | |
| Detailed report | Always include per-NPC assignment details in bundle/CLI report; most transparent but noisier and may expose more NPC data in support bundles. | |

**User's choice:** Summary counts
**Notes:** Success should be concise; failure should include actionable blocked-NPC details.

---

## Replay Trigger

| Option | Description | Selected |
|--------|-------------|----------|
| Auto on BodyGen | If `ProjectModel.AssignmentStrategy` exists, replay automatically for CLI/bundle requests that include BodyGen morph output. BoS-only requests do not replay. | yes |
| Require flag | Add an explicit CLI/bundle opt-in flag; safer surprise-wise, but saved strategy projects remain non-reproducible unless users know the flag. | |
| Auto plus opt-out | Replay by default for BodyGen output, with a skip flag for rare cases where users want existing assignments only. | |

**User's choice:** Auto on BodyGen
**Notes:** Reproducibility should come from saved project data; no new opt-in flag is required for Phase 7.

---

## the agent's Discretion

- Exact helper/seam names, DTO names, success wording, and test helper shape remain open to the planner as long as locked behavior and Core-only automation boundaries are preserved.

## Deferred Ideas

None.
