---
phase: 07-replay-saved-strategies-in-automation-outputs
verified: 2026-04-28T09:31:35Z
status: passed
score: 13/13 must-haves verified
overrides_applied: 0
---

# Phase 7: Replay Saved Strategies in Automation Outputs Verification Report

**Phase Goal:** CLI and portable bundle output generation replay saved deterministic assignment strategies before morph generation so automation is reproducible from project data.
**Verified:** 2026-04-28T09:31:35Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A Core orchestration seam applies saved assignment strategies deterministically before automation morph generation without bypassing the existing random-provider abstraction. | ✓ VERIFIED | `AssignmentStrategyReplayService.PrepareForBodyGen` clones via `new ProjectModel(); ReplaceWith(sourceProject)` when requested, checks BodyGen/all intent, and delegates to `morphAssignmentService.ApplyStrategy` (`src/BS2BG.Core/Automation/AssignmentStrategyReplayService.cs:33-51`). `MorphAssignmentService.ApplyStrategy` branches seeded strategies to `DeterministicAssignmentRandomProvider` while still calling `service.Apply(project, strategy, eligibleRows)` (`src/BS2BG.Core/Morphs/MorphAssignmentService.cs:160-172`). |
| 2 | CLI `generate` replays a saved strategy before writing morph output when the loaded project contains an assignment strategy. | ✓ VERIFIED | `HeadlessGenerationService.Run` builds the request catalog, calls `PrepareForBodyGen(project, request.Intent, cloneBeforeReplay: true)` before validation/overwrite planning/writes, assigns `generationProject = replayResult.Project`, and passes that project to `GenerateMorphs` (`src/BS2BG.Core/Automation/HeadlessGenerationService.cs:64-112`). |
| 3 | Portable bundle generation replays a saved strategy before adding generated BodyGen morph output to the bundle. | ✓ VERIFIED | `PortableProjectBundleService.BuildPlan` calls `PrepareForBodyGen(request.Project, request.Intent, cloneBeforeReplay: true)` before validation, project entry staging, generated output entries, manifest construction, or zip creation; `AddGeneratedOutputEntries` receives `outputProject` and uses it for `GenerateMorphs` (`src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:203-247`, `293-317`). |
| 4 | Regression tests prove CLI and bundle outputs are reproducible from saved strategy data rather than only from already-mutated in-memory assignments. | ✓ VERIFIED | Direct replay, CLI, and bundle tests construct stale-assignment fixtures and assert replayed output does not contain `StalePreset` while seeded runs repeat byte-identically (`AssignmentStrategyReplayServiceTests.cs:15-196`; `CliGenerationTests.cs:192-390`; `PortableBundleServiceTests.cs:494-657`). Targeted verification run passed 85/85 Phase 7-focused tests. |
| 5 | BodyGen/all automation replays saved strategies automatically without a new opt-in flag. | ✓ VERIFIED | CLI options remain `--project`, `--output`, `--intent`, `--overwrite`, and `--omit-redundant-sliders`; no replay flag exists. `Program.CreateGenerationService` composes `AssignmentStrategyReplayService`, and Core replay happens from saved project data based on `OutputIntent` (`src/BS2BG.Cli/Program.cs:96-108`, `177-192`). |
| 6 | BoS-only automation does not replay saved strategies. | ✓ VERIFIED | Replay seam returns `Replayed=false` for non-BodyGen intents (`AssignmentStrategyReplayService.cs:41-42`). CLI and bundle tests assert BoS-only does not write `morphs.ini` or replay reports (`CliGenerationTests.cs:281-299`; `PortableBundleServiceTests.cs:562-585`). |
| 7 | No saved strategy keeps existing assignment behavior. | ✓ VERIFIED | Replay seam returns no-op when `AssignmentStrategy` is null (`AssignmentStrategyReplayService.cs:41-42`); CLI and bundle tests assert `morphs.ini` still contains the existing stale assignment in no-strategy projects (`CliGenerationTests.cs:301-318`; `PortableBundleServiceTests.cs:562-585`). |
| 8 | Blocked replay fails before output files or zip entries and does not emit stale prior assignments. | ✓ VERIFIED | CLI returns `ValidationBlocked` immediately when `replayResult.IsBlocked`, before target planning/writes (`HeadlessGenerationService.cs:65-71`). Bundle returns a blocked plan with empty entries/manifest before staging entries or creating the zip (`PortableProjectBundleService.cs:207-219`, `356-375`). Tests assert no CLI output files and no bundle zip/entries on blocked replay (`CliGenerationTests.cs:320-347`; `PortableBundleServiceTests.cs:587-611`). |
| 9 | Bundle `project/project.jbs2bg` remains original source project state with strategy configuration intact while generated morph output uses request-scoped replay state. | ✓ VERIFIED | Bundle source entry serializes `request.Project` (`PortableProjectBundleService.cs:238-241`) while generated outputs use `outputProject = replayResult.Project` (`PortableProjectBundleService.cs:221`, `247`, `293-317`). Tests load the bundled project and compare its assignments/strategy against the original caller state while generated `morphs.ini` reflects replayed assignments (`PortableBundleServiceTests.cs:494-524`). |
| 10 | Request-scoped replay does not mutate caller/source project state. | ✓ VERIFIED | Core direct tests verify clone-before-replay leaves source assignments, `AssignmentStrategy`, `IsDirty`, and `ChangeVersion` unchanged (`AssignmentStrategyReplayServiceTests.cs:100-122`). Bundle tests assert caller assignments, strategy, dirty state, and change version match pre-call snapshots (`PortableBundleServiceTests.cs:494-524`). |
| 11 | Replay summary surfaces provide concise success counts and actionable blocked-NPC details. | ✓ VERIFIED | CLI success includes `Assignment strategy replayed: {kind}; assigned NPCs: {assigned}; blocked NPCs: 0.` before success text (`HeadlessGenerationService.cs:196-210`; `Program.cs:296-305`). Blocked messages include Mod, Name, EditorId, Race, FormId, and Reason without private project/output paths (`HeadlessGenerationService.cs:216-235`). Bundle preview/result expose `ReplayReportText` and add `reports/replay.txt` for successful replay (`PortableProjectBundleContracts.cs:52-86`; `PortableProjectBundleService.cs:243-244`, `377-405`). |
| 12 | CLI and App composition paths wire replay-aware services without adding App/Avalonia dependencies to Core or CLI. | ✓ VERIFIED | CLI composes Core-only replay service for generation and bundles (`src/BS2BG.Cli/Program.cs:180-215`). App DI registers `AssignmentStrategyReplayService` and passes it into the bundle service (`AppBootstrapper.cs:46-57`). Design-time/fallback construction in `MainWindowViewModel` also supplies the replay dependency (`MainWindowViewModel.cs:177-186`). Core source contains no App/Avalonia references in these replay services. |
| 13 | AUTO-02 and AUTO-03 are accounted for in implementation and tests. | ✓ VERIFIED | `AUTO-02` maps to portable bundle creation without private paths and is preserved while adding replay, source project preservation, manifest/report coverage, and path-scrubbed replay text. `AUTO-03` maps to deterministic assignment strategy seams; replay uses `MorphAssignmentService`/`AssignmentStrategyService` and seeded deterministic provider without alternate RNG. Both IDs are declared in Plan 07 frontmatter and mapped to Phase 7 in `.planning/REQUIREMENTS.md:110-111`. |

**Score:** 13/13 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/BS2BG.Core/Automation/AssignmentStrategyReplayService.cs` | Core-only replay seam returning request-scoped project state plus replay summary/blockers | ✓ VERIFIED | Exists and substantive; clones on demand, gates on BodyGen/all, delegates strategy application, returns replay result. |
| `src/BS2BG.Core/Automation/AssignmentStrategyReplayContracts.cs` | Replay result with blocked-project contract | ✓ VERIFIED | `AssignmentStrategyReplayResult` exposes `Project`, `Replayed`, `StrategyKind`, `AssignedCount`, `BlockedNpcs`, and `IsBlocked`; XML docs warn blocked projects may be partial and must not be generated from. |
| `src/BS2BG.Core/Morphs/MorphAssignmentService.cs` | Provider-compatible seeded strategy application preserving `eligibleRows` | ✓ VERIFIED | Seeded branch uses `DeterministicAssignmentRandomProvider`; both seeded and unseeded branches call `service.Apply(project, strategy, eligibleRows)`. |
| `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` | CLI/headless replay integration before validation/overwrite/writes | ✓ VERIFIED | Constructor requires replay service; replay happens immediately after project load/catalog composition; blocked replay returns before output target planning. |
| `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` | Bundle replay integration with cloned working project and source project preservation | ✓ VERIFIED | Constructor requires replay service; `BuildPlan` replays before output staging; `SaveToString(request.Project, request.SaveContext)` preserves source project entry. |
| `src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs` | Explicit bundle replay report surface | ✓ VERIFIED | Non-positional `ReplayReportText` init properties exist on preview and result records. |
| `src/BS2BG.Cli/Program.cs` | CLI composition and stdout/stderr replay surfacing | ✓ VERIFIED | Composes replay services for `generate` and `bundle`; generation success writes result message to stdout; bundle output writes replay report text. |
| `src/BS2BG.App/AppBootstrapper.cs` | GUI DI composition updated for replay-aware bundle service | ✓ VERIFIED | Registers `AssignmentStrategyReplayService` and injects it into `PortableProjectBundleService`. |
| `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` | Bundle preview/result summaries include replay report text | ✓ VERIFIED | Fallback bundle service construction includes replay service; `BuildBundleSummary` appends replay text from preview/result. |
| `tests/BS2BG.Tests/AssignmentStrategyReplayServiceTests.cs` | Direct replay/no-replay/blocker/clone/seed/scope coverage | ✓ VERIFIED | Covers BodyGen/all replay, BoS no-replay, null strategy, blocked NPC identity, clone/no-clone behavior, seeded repeatability, empty NPC no-op, and eligible-row scope. |
| `tests/BS2BG.Tests/CliGenerationTests.cs` | CLI/service regression coverage | ✓ VERIFIED | Covers stale assignments, seeded determinism, all-intent parity, BoS-only, no-strategy, blockers, validation-after-replay, overwrite ordering, and Program.Main stdout/stderr. |
| `tests/BS2BG.Tests/PortableBundleServiceTests.cs` | Bundle regression coverage | ✓ VERIFIED | Covers replay output bytes, source project preservation, caller non-mutation, seeded repeatability, CLI/bundle parity, BoS/no-strategy, blockers, preview reports, `reports/replay.txt`, and manifest checksum entry. |

Artifact SDK checks passed for all 11 planned artifacts across `07-01`, `07-02`, and `07-03`. SDK symbolic key-link checks could not resolve class/member names as file paths, so key links were verified manually below.

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AssignmentStrategyReplayService` | `MorphAssignmentService.ApplyStrategy` | Provider-compatible strategy application | ✓ WIRED | `PrepareForBodyGen` calls `morphAssignmentService.ApplyStrategy(workingProject, strategy)` after clone/no-op decisions (`AssignmentStrategyReplayService.cs:44-45`). |
| `AssignmentStrategyReplayService` | `ProjectModel.ReplaceWith` | Request-scoped clone for mutation isolation | ✓ WIRED | `CloneProject` creates a new `ProjectModel`, calls `workingProject.ReplaceWith(sourceProject)`, and returns it (`AssignmentStrategyReplayService.cs:56-60`). |
| `MorphAssignmentService.ApplyStrategy` | `AssignmentStrategyService.Apply(project, strategy, eligibleRows)` | Seeded/unseeded provider-compatible dispatch | ✓ WIRED | Seeded path swaps only the random provider; both paths preserve the `eligibleRows` parameter (`MorphAssignmentService.cs:168-172`). |
| `HeadlessGenerationService.Run` | `AssignmentStrategyReplayService.PrepareForBodyGen` | Before validation, overwrite preflight, and output writes | ✓ WIRED | Replay call occurs at line 65 before validation, missing-profile checks, `PlanTargets`, and writer calls. |
| `HeadlessGenerationService.Run` | `MorphGenerationService.GenerateMorphs` | `generationProject = replayResult.Project` | ✓ WIRED | `generationProject` is assigned from `replayResult.Project` and passed to `GenerateMorphs` for BodyGen output (`HeadlessGenerationService.cs:73-112`). |
| `PortableProjectBundleService.BuildPlan` | `AssignmentStrategyReplayService.PrepareForBodyGen` | Request-scoped cloned project before generated BodyGen entries | ✓ WIRED | Replay call occurs at `BuildPlan` line 207 before validation, staging entries, manifest, or zip creation. |
| `project/project.jbs2bg` | `ProjectFileService.SaveToString(request.Project, request.SaveContext)` | Serialize original request project, not replay result | ✓ WIRED | Source project entry explicitly uses `request.Project`; generated output uses separate `outputProject` from replay result (`PortableProjectBundleService.cs:221`, `240`, `247`). |
| Bundle preview/result | GUI/CLI report surfaces | `ReplayReportText` | ✓ WIRED | Preview/result contracts set `ReplayReportText`, CLI prints it, and `MainWindowViewModel` appends it to bundle summaries. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `AssignmentStrategyReplayService.PrepareForBodyGen` | `workingProject` assignments | `ProjectModel.AssignmentStrategy` + `MorphAssignmentService.ApplyStrategy` over actual NPC/preset collections | Yes | ✓ FLOWING — no hardcoded/static assignment output; direct tests assert stale assignments change only through saved strategy replay. |
| `HeadlessGenerationService.Run` | `generationProject` | Loaded `.jbs2bg` project → replay clone → validation/missing-profile checks → `GenerateMorphs(generationProject)` | Yes | ✓ FLOWING — CLI `morphs.ini` uses replayed state; blocked replay exits before target planning/writes. |
| `PortableProjectBundleService.BuildPlan` | `outputProject` | In-memory request project → replay clone → `AddGeneratedOutputEntries(... outputProject ...)` | Yes | ✓ FLOWING — generated `outputs/bodygen/morphs.ini` uses replayed state while `project/project.jbs2bg` serializes source state. |
| `PortableProjectBundleService.BuildPlan` | `ReplayReportText` / `reports/replay.txt` | `AssignmentStrategyReplayResult` success/blocker/no-replay fields | Yes | ✓ FLOWING — preview/result text and optional report entry are derived from replay result, not placeholders. |
| `Program.WriteResult` / `WriteBundleResult` | stdout/stderr replay messages | Core service result messages and bundle `ReplayReportText` | Yes | ✓ FLOWING — success goes to stdout; blocked outcomes go to stderr via existing result mapping. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Phase 7 targeted replay/CLI/bundle regressions | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~AssignmentStrategyReplayServiceTests\|FullyQualifiedName~CliGenerationTests\|FullyQualifiedName~PortableBundleServiceTests" --nologo --verbosity quiet` | Passed: 85, Failed: 0, Skipped: 0, Total: 85. | ✓ PASS |
| Artifact contract checks | `gsd-sdk query verify.artifacts` for `07-01`, `07-02`, and `07-03` plans | Passed: 11/11 artifacts. | ✓ PASS |
| Roadmap contract load | `gsd-sdk query roadmap.get-phase 7 --raw` | Returned Phase 7 goal and all 4 success criteria. | ✓ PASS |
| Deferred filtering | `gsd-sdk query roadmap.analyze --raw` | Phase 7 is the final roadmap phase; no later phase deferrals apply. | ✓ PASS |

Known execution inputs also report Plan 07-01 targeted suite passed 50 tests, Plan 07-02 targeted CLI suite passed 36 tests, Plan 07-03 targeted bundle suite passed 39 tests, and full `dotnet test` passed 570 with 3 skipped and 0 failed.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AUTO-02 | `07-03-PLAN.md` | User can create a portable project bundle containing the `.jbs2bg` file, generated outputs, profile JSON copies, and a validation report without private local paths. | ✓ SATISFIED | Existing Phase 5 bundle contract remains wired; Phase 7 adds replay before BodyGen output, preserves `project/project.jbs2bg` source state, emits `reports/replay.txt` only on successful replay, and path-scrubs replay report text. Tests assert no zip on blocked replay, source project preservation, manifest checksum coverage, CLI/bundle morph parity, and no private path leaks in replay blockers. |
| AUTO-03 | `07-01-PLAN.md`, `07-02-PLAN.md`, `07-03-PLAN.md` | User can apply deterministic assignment strategy presets through seams that remain testable and do not bypass existing random-provider abstractions. | ✓ SATISFIED | `AssignmentStrategyReplayService` delegates to `MorphAssignmentService`; seeded strategies use `DeterministicAssignmentRandomProvider` through `AssignmentStrategyService`, unseeded strategies keep the injected provider, and tests pin seeded replay plus eligible-row scope. CLI and bundle integrations consume the same seam. |

No orphaned Phase 7 requirement IDs were found: `.planning/REQUIREMENTS.md` maps AUTO-02 and AUTO-03 to Phase 7, and both are declared in Phase 7 plan frontmatter and accounted for above.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` | 380-383 | BoS-only saved-strategy bundle report says `No saved assignment strategy` because no replay occurred | ⚠️ Warning | Matches advisory `07-REVIEW.md` WR-01. This is misleading for BoS-only bundles with saved strategies, but BoS-only intentionally does not generate morph output or replay; it does not invalidate the Phase 7 morph-generation replay goal. |
| `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` / `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` | 66-71 / 209-218 | Replay-blocked outcomes return a validation report produced by normal validation rather than structured replay blocker findings | ⚠️ Warning | Matches advisory `07-REVIEW.md` WR-02. CLI text and bundle `ReplayReportText` do include actionable blocked-NPC details, satisfying D-09; structured validation-report enrichment would improve API consumers but is not required for the phase goal. |
| `src/BS2BG.Core/Bundling/BundlePathScrubber.cs` | 53 | Word `placeholder` in XML doc text | ℹ️ Info | Documentation describes privacy redaction placeholder text; not a stub. |
| `src/BS2BG.Core/Morphs/MorphAssignmentService.cs` | 22 | `target = null!` | ℹ️ Info | Nullable out-parameter initialization before validation; not a stub or user-visible output. |

No blocker TODO/FIXME/placeholder implementation, orphaned replay artifact, or hardcoded empty data source was found in Phase 7 source paths.

### Human Verification Required

None. Phase 7 is headless/Core/CLI/bundle behavior and is covered by source inspection plus deterministic automated tests. No visual, real-time, or external-service behavior is required for goal achievement.

### Gaps Summary

No blocking gaps found. The codebase evidence shows the phase goal is achieved: Core replay is centralized and provider-compatible; CLI and portable bundle BodyGen/all outputs replay saved strategies before morph generation; BoS-only and no-strategy paths preserve intended existing behavior; blocked replay exits before writing stale output; bundle project entries stay source-state; and regression tests prove stale in-memory assignments are not the source of generated morph output.

The two advisory review warnings remain non-blocking quality improvements because they affect report precision/structured diagnostics rather than the required replay-before-morph-generation behavior.

---

_Verified: 2026-04-28T09:31:35Z_
_Verifier: the agent (gsd-verifier)_
