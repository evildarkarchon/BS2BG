# Roadmap: BS2BG Future Work

## Overview

This roadmap advances BS2BG from the completed M0-M7 port into future parity-sensitive improvements. The phases follow the requirement categories and research ordering: establish trustworthy profile semantics first, harden daily workflow operations next, add diagnostics before editable customization, then expose automation and sharing workflows only after the same Core generation/export paths are proven reliable.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Profile Correctness and Trust** - Users can generate output with explicit, profile-specific semantics, neutral unresolved-profile fallback information, and release-facing Fallout 4 profile confidence context. (completed 2026-04-26)
- [ ] **Phase 2: Workflow Persistence, Filtering, and Undo Hardening** - Users can safely resume, filter, bulk-edit, and undo large modding workflows.
- [ ] **Phase 3: Validation and Diagnostics** - Users can inspect project health, import effects, and export consequences before committing risky changes.
- [ ] **Phase 4: Profile Extensibility and Controlled Customization** - Users can manage custom local profiles without corrupting bundled profiles or legacy projects.
- [ ] **Phase 5: Automation, Sharing, and Release Trust** - Users can automate generation, bundle projects, and verify portable releases through trusted paths.

## Phase Details

### Phase 1: Profile Correctness and Trust
**Goal**: Users can generate output with explicit, profile-specific semantics, neutral unresolved-profile fallback information, and release-facing Fallout 4 profile confidence context.
**Depends on**: Nothing (first phase)
**Requirements**: PROF-01, PROF-02, PROF-03, PROF-04, PROF-05
**Success Criteria** (what must be TRUE):
  1. User can select a distinct Fallout 4 CBBE profile whose defaults, multipliers, and inverted-slider behavior are not reused from Skyrim CBBE or UUNP.
  2. User can open and save legacy `.jbs2bg` projects while preserving compatible `isUUNP` profile semantics.
  3. User can see neutral unresolved-profile fallback information when a saved project references an unbundled profile, while Phase 1 avoids profile inference, mismatch warnings, and in-app Fallout 4 experimental labels per D-05 through D-10.
  4. User can generate templates, morphs, and BoS JSON with bundled-profile behavior protected by profile-specific tests.
  Context override: Phase 1 CONTEXT.md D-05 through D-08 intentionally narrow the original warning/experimental wording to neutral fallback information plus release documentation; modal warnings, mismatch heuristics, and in-app FO4 experimental labels remain out of scope.
**Plans**: 8 plans
Plans:
- [x] 01-01-PLAN.md — Add distinct bundled Fallout 4 CBBE profile data and catalog wiring.
- [x] 01-02-PLAN.md — Preserve legacy/unbundled profile semantics and expose detectable fallback.
- [x] 01-03-PLAN.md — Implement selected-profile import and neutral unresolved-profile fallback ViewModel state.
- [x] 01-04-PLAN.md — Add neutral fallback UI, info resources, and release-facing FO4 profile note.
- [x] 01-05-PLAN.md — Fix explicit adoption of the displayed fallback profile without adding warning UX.
- [x] 01-06-PLAN.md — Add profile-specific BoS JSON, BodyGen export, and morph profile-independence tests.
- [x] 01-07-PLAN.md — Reconcile roadmap/requirements warning wording with locked neutral-fallback decisions.
**UI hint**: yes

### Phase 2: Workflow Persistence, Filtering, and Undo Hardening
**Goal**: Users can work safely across restarts and large NPC/preset datasets without hidden-row mutations, lost preferences, or corrupted undo state.
**Depends on**: Phase 1
**Requirements**: WORK-01, WORK-02, WORK-03, WORK-04, WORK-05
**Success Criteria** (what must be TRUE):
  1. User can restart BS2BG and retain last-used folders plus generation-affecting preferences such as omit-redundant sliders.
  2. User can filter NPC rows by mod, name, editor ID, form ID, race, assignment state, and preset-related values while each NPC keeps stable identity.
  3. User can run bulk NPC operations with explicit all, visible, selected, and visible-empty scopes so filtered rows are not changed accidentally.
  4. User can undo and redo high-risk preset, target, NPC assignment, import, clear, and profile operations without mutable live-state corruption.
  5. User can operate on large real-world preset and NPC datasets without UI freezes or unbounded filter/import delays.
**Plans**: 9 plans
Plans:
- [x] 02-01-PLAN.md — Persist Omit Redundant Sliders as local workflow preference state.
- [x] 02-02-PLAN.md — Remember separate project and export folders for shell-level file workflows.
- [x] 02-03-PLAN.md — Remember separate BodySlide XML and NPC text import folders.
- [x] 02-04-PLAN.md — Create stable NPC row identity and filter predicate contracts.
- [x] 02-05-PLAN.md — Integrate stable, debounced NPC filtering into Morphs ViewModel.
- [x] 02-06-PLAN.md — Expose full checklist filtering in the Avalonia Morphs UI.
- [x] 02-07-PLAN.md — Add explicit scoped NPC bulk operations and scope selector UI.
- [x] 02-08-PLAN.md — Bound undo history and harden template/profile undo snapshots.
- [x] 02-09-PLAN.md — Harden morph/NPC undo snapshots and scoped bulk undo behavior.
**UI hint**: yes

### Phase 3: Validation and Diagnostics
**Goal**: Users can understand project readiness, profile behavior, import impact, and export risk before committing changes to disk.
**Depends on**: Phase 2
**Requirements**: DIAG-01, DIAG-02, DIAG-03, DIAG-04, DIAG-05
**Success Criteria** (what must be TRUE):
  1. User can run a read-only validation report that identifies profile, preset, target, NPC assignment, reference, and export-readiness issues.
  2. User can inspect profile diagnostics for slider coverage, unknown sliders, injected defaults, multipliers, inversions, and likely profile mismatches.
  3. User can preview NPC import results, invalid lines, duplicates, charset fallback, and assignment effects before committing import changes.
  4. User can preview export destinations and exact output effects before overwriting files or risking partial output.
  5. User receives save/export failure messages that identify which files were written, restored, skipped, or left untouched.
**Plans**: 8 plans
Plans:
- [x] 03-01-PLAN.md — Create Core project/profile diagnostics services and tests.
- [x] 03-02-PLAN.md — Add read-only NPC import preview classification and duplicate diagnostics.
- [x] 03-03-PLAN.md — Add export preview and atomic outcome ledger Core contracts.
- [x] 03-04-PLAN.md — Expose project/profile diagnostics through a ReactiveUI Diagnostics ViewModel and copyable report.
- [x] 03-05-PLAN.md — Integrate optional no-mutation NPC import preview and distinct assignment-effect summaries.
- [x] 03-06-PLAN.md — Wire export preview and overwrite-risk confirmation into shell export workflows.
- [x] 03-07-PLAN.md — Surface save/export outcome ledgers in failure messages without save preview friction.
- [x] 03-08-PLAN.md — Add the first-class compiled-bound Diagnostics tab and visual verification checkpoint.
**UI hint**: yes

### Phase 4: Profile Extensibility and Controlled Customization
**Goal**: Users can create, validate, share, and recover local profile definitions without silent fallback or damage to bundled and legacy-compatible profile data.
**Depends on**: Phase 3
**Requirements**: EXT-01, EXT-02, EXT-03, EXT-04, EXT-05
**Success Criteria** (what must be TRUE):
  1. User can import, copy, export, and validate local JSON profile files without corrupting bundled profiles or existing projects.
  2. User can edit supported profile metadata and slider tables through workflows that reject malformed or ambiguous profile data.
  3. User can save projects that reference custom profiles while preserving legacy compatibility fields for older `.jbs2bg` consumers.
  4. User can resolve missing custom profile references through clear diagnostics rather than silent fallback.
  5. User can bundle or copy project-specific profiles when sharing a project with another machine.
**Plans**: 13 plans
Plans:
**Wave 1**
- [x] 04-01-PLAN.md — Create Core custom profile validation and JSON export contracts.
**Wave 2 (blocked on Wave 1 completion)**
- [x] 04-02-PLAN.md — Add AppData custom profile storage and source-tagged catalog composition.
- [x] 04-03-PLAN.md — Embed referenced custom profile definitions in `.jbs2bg` projects while preserving legacy fields.
**Wave 3 (blocked on Wave 2 completion; 04-04 also depends on 04-02 for local catalog semantics)**
- [x] 04-04-PLAN.md — Add read-only missing-profile recovery diagnostics and exact-match resolution checks.
- [x] 04-05-PLAN.md — Build ReactiveUI profile manager/editor workflows for import, copy, edit, validate, save, export, and delete.
**Wave 4 (blocked on Wave 3 recovery diagnostics and catalog/project contracts)**
- [x] 04-06-PLAN.md — Integrate explicit embedded-profile project-open conflict transactions and overlay reset.
**Wave 5 (blocked on Wave 4 conflict transaction and Profile Manager workflows)**
- [x] 04-07-PLAN.md — Expose explicit diagnostics/profile recovery actions, undo-aware remaps, selected JSON export, and OpenSpec deltas.
**Wave 6 (blocked on Wave 5 recovery/action contracts)**
- [x] 04-08-PLAN.md — Wire the first-class Profiles workspace UI with compiled bindings and visual verification.
**Wave 7 (gap closure; blocked on Wave 6 verification)**
- [x] 04-09-PLAN.md — Close profile row selection, copy-as-custom, and Game metadata export gaps.
- [x] 04-11-PLAN.md — Close GUI project-save embedding and conflict rename validation gaps.
**Wave 8 (gap closure; blocked on 04-09 editor/selection fixes)**
- [x] 04-10-PLAN.md — Complete profile editor table add/remove controls and live row validation.
**Wave 9 (gap closure; blocked on Wave 8 verification)**
- [x] 04-12-PLAN.md — Close manager save gating, selection rollback, dirty refresh preservation, and profile JSON I/O failure gaps.
- [ ] 04-13-PLAN.md — Apply profile editor slider filtering consistently to Defaults, Multipliers, and Inverted tables.
Cross-cutting constraints: custom profile identity is internal display-name based; bundled profiles stay read-only; unresolved/missing profile behavior remains neutral and non-blocking; all profile mutations use explicit user actions and validation before catalog inclusion.
**UI hint**: yes

### Phase 5: Automation, Sharing, and Release Trust
**Goal**: Users can reuse the proven Core generation path outside the GUI, package shareable projects, and verify release artifacts with clear setup guidance.
**Depends on**: Phase 4
**Requirements**: AUTO-01, AUTO-02, AUTO-03, AUTO-04, AUTO-05
**Success Criteria** (what must be TRUE):
  1. User can run headless CLI generation that uses the same Core services and output semantics as the GUI.
  2. User can create a portable project bundle containing the `.jbs2bg` file, generated outputs, profile JSON copies, and a validation report without private local paths.
  3. User can apply deterministic assignment strategy presets through testable seams that do not bypass existing random-provider abstractions.
  4. User can verify downloaded release artifacts through checksums, signing information when available, and release-package assertions.
  5. User can access setup and troubleshooting guidance for BodyGen, BodySlide, BoS, and common output-location mistakes without BS2BG editing external game plugins.
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Profile Correctness and Trust | 7/7 | Complete   | 2026-04-26 |
| 2. Workflow Persistence, Filtering, and Undo Hardening | 0/TBD | Not started | - |
| 3. Validation and Diagnostics | 8/8 | Complete | 2026-04-27 |
| 4. Profile Extensibility and Controlled Customization | 8/8 | Complete | 2026-04-27 |
| 5. Automation, Sharing, and Release Trust | 0/TBD | Not started | - |
