---
phase: 7
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-28T01:44:21.5259936-07:00
plans_reviewed: [07-01-PLAN.md, 07-02-PLAN.md, 07-03-PLAN.md]
---

# Cross-AI Plan Review - Phase 7

## Gemini Review

# Cross-AI Plan Review: Phase 07 - Replay Saved Strategies in Automation Outputs

I have completed a comprehensive review of plans **07-01**, **07-02**, and **07-03**. The proposed strategy is technically sound, architecturally consistent with recent Phase 6 patterns, and rigorously focused on the modder-trust constraints of the project.

## 1. Summary
The plans provide a high-quality, surgical approach to closing the v1.0 milestone automation gap. By centralizing replay logic in a dedicated Core service (`AssignmentStrategyReplayService`) and enforcing request-scoped state isolation through cloning, the plans ensure that CLI and portable bundle outputs are reproducible from saved project data. The TDD strategy, particularly the requirement for "stale assignment" test fixtures, is excellent and ensures that the fix is empirically verified against the root cause (stale in-memory state).

## 2. Strengths
- **Centralized Replay Logic**: Creating `AssignmentStrategyReplayService` as a reusable Core seam prevents logic drift between CLI and bundle services, ensuring consistent trigger (D-01) and blocking (D-06) behavior.
- **Robust State Isolation**: The explicit use of `cloneBeforeReplay` in both automation services perfectly satisfies the constraint to preserve bundled project source data (D-04/D-05) while generating replayed outputs.
- **High-Signal Diagnostics**: The plans effectively translate the "Visibility" decisions (D-08/D-09) into concise success summaries and actionable failure details, significantly improving the utility of automation for modders.
- **Preservation of RNG Seams**: Reusing `MorphAssignmentService.ApplyStrategy` ensures that deterministic replay remains backed by the existing `DeterministicAssignmentRandomProvider` without bypassing the established random-provider abstraction.
- **Strategic Validation**: Treating blocked strategy replays as fatal `ValidationBlocked` errors (Plan 02/03) instead of mere cautions aligns with a "fail-fast" automation philosophy that prioritizes output correctness over partial completion.

## 3. Concerns
- **Performance (LOW)**: Cloning large projects (`ProjectModel.ReplaceWith`) for every automation request adds minor overhead. **Mitigation**: This is negligible compared to the I/O costs of file generation and zipping, and it is a necessary trade-off for the "single mental model" safety pattern established in research.
- **BoS/BodyGen Project Divergence (LOW)**: In Plan 03, the mention of using the "original request project" for BoS while using "replayResult.Project" for BodyGen within the same bundle plan might lead to slight code complexity. **Mitigation**: Using `replayResult.Project` for *all* generated output entries within the bundle plan (regardless of intent) would likely be cleaner and safer, even if BoS output is unaffected by NPC assignments.
- **Scrubbing Replay Details (LOW)**: Replay failure details (blocked NPC names/mods) must not accidentally leak private local paths. **Mitigation**: Plan 03 Task 2 explicitly mentions passing report text through the existing `BundlePathScrubber`, which handles this risk effectively.

## 4. Suggestions
- **ProjectModel.Clone()**: Consider adding a simple `Clone()` method to `ProjectModel` that wraps the `new ProjectModel() + ReplaceWith()` pattern to keep the orchestration code in the new services more concise.
- **Shared Failure Formatter**: If the formatting of "Blocked NPC Details" (D-09) becomes non-trivial, consider adding a small helper to `DiagnosticReportTextFormatter` or the new replay service to ensure failure text is identical between CLI and Bundle reports.
- **Integration Test Coverage**: Ensure that the "stale assignment" test fixtures in Task 1 of Plans 02 and 03 are sufficiently different from the "replayed" output to avoid "false green" results where the test passes simply because *any* assignment exists.

## 5. Risk Assessment
**Risk Level: LOW**

The architectural risk is minimal because the plans build upon proven Core services (`AssignmentStrategyService`) and established automation patterns from Phase 6 (`RequestScopedProfileCatalogComposer`). The most significant technical risk - accidentally mutating the source project - is directly addressed and mitigated by the mandatory cloning in the `AssignmentStrategyReplayService`. The TDD gate ensures that the primary behavioral goal (reproducibility) is verified before implementation is considered complete.

---

## the agent Review

# Phase 7 Plan Review: Replay Saved Strategies in Automation Outputs

## Summary

The three-plan structure is well-architected and faithfully implements the locked Phase 7 decisions (D-01 through D-09). Plan 01 establishes a clean Core-only replay seam with TDD, then Plans 02 and 03 wire it into CLI/headless and bundle paths respectively, both depending only on Plan 01 (correct DAG). The plans correctly identify that this is an orchestration problem, not a new algorithm problem, and reuse `MorphAssignmentService.ApplyStrategy`/`AssignmentStrategyService` rather than introducing new RNG. The biggest concerns are around (a) how `ReplaceWith`'s `MarkClean()` and dirty-state side effects interact with bundle source-project preservation, (b) BoS JSON generation continuing to use `request.Project` while BodyGen uses the replay clone in the same bundle plan, and (c) whether the replay summary text is actually exposed where users will see it (CLI stdout vs bundle report).

---

## Plan 07-01: Core Replay Seam

### Strengths
- **Clean TDD discipline** - RED-GREEN with concrete behavior list (5 distinct test cases) covering D-01, D-03, D-05, D-06, D-07, D-08, D-09.
- **Reuses `MorphAssignmentService.ApplyStrategy`** correctly preserving the random-provider seam (D-07).
- **Result contract design** matches existing record style (`HeadlessGenerationContracts.cs`, `PortableProjectBundleContracts.cs`) with `IsBlocked` convenience property for downstream gate decisions.
- **`cloneBeforeReplay` parameter** gives callers explicit choice - important since CLI loads fresh per request and may not need clone overhead, while bundle MUST clone.
- Scope is tightly bounded - no writer changes, no UI, no new strategy kinds.

### Concerns
- **[MEDIUM] `ProjectModel.ReplaceWith` calls `MarkClean()` and triggers `DirtyStateChanged`** (ProjectModel.cs:104, 142-149). On a freshly-constructed `new ProjectModel()` this is harmless because `IsDirty` starts false, but the `ChangeVersion` increments and event firing during clone happens in a clone path that's about to be mutated again by `ApplyStrategy`. Worth confirming this doesn't leak observable state changes anywhere (it shouldn't - it's a fresh local instance - but the test in Task 1 should explicitly assert `sourceProject.IsDirty` and `sourceProject.ChangeVersion` are unchanged after replay, not just that assignments differ).
- **[MEDIUM] `ReplaceWith` does not preserve NPC stable App-layer identity** (Phase 2 WORK-02). Each NPC is reconstructed with `new Npc(npc.Name) { Mod, EditorId, Race, FormId }`. For Core-only automation this is fine, but the blocked-NPC details returned in `BlockedNpcs` will reference the *clone's* NPC instances, not the source project's. Test 4 says blocked rows are "identifiable by `Mod`, `EditorId`, `Race`, and `FormId`" - good, that's the right identity contract. Just confirm error messages/logs don't leak the clone instance reference anywhere.
- **[LOW] No test for `OutputIntent.All`** - Test 1 says "BodyGen and All intents" but the behavior list combines them. Add explicit `[Theory]` or two test methods so a future regression that breaks `All` but not `BodyGen` is caught.
- **[LOW] No null-strategy + `cloneBeforeReplay: true` test** - Should still return a clone (or source?) with `Replayed=false`. The behavior here matters for callers: if bundle always passes `cloneBeforeReplay: true`, what `Project` does it get back when there's no strategy? The implementation as specified clones first, then short-circuits, so it returns the clone. That's correct (bundle still gets isolation guarantees) but should be tested.
- **[LOW] `AssignmentStrategyReplayResult` location** - frontmatter says `HeadlessGenerationContracts.cs`, but the result is shared by bundle service too. Consider a dedicated `AssignmentStrategyReplayContracts.cs` or place it next to the service in `AssignmentStrategyReplayService.cs`. Cosmetic, but bundle-service authors shouldn't have to import a "Headless" contract file.

### Suggestions
- Add explicit assertions in Task 1: `sourceProject.IsDirty.Should().BeFalse()` and `sourceProject.MorphedNpcs[i].SliderPresets.Should().BeEquivalentTo(originalAssignments)` after replay with `cloneBeforeReplay: true`.
- Add a `[Theory]` for `OutputIntent.BodyGen` and `OutputIntent.All` to lock both intents.
- Place `AssignmentStrategyReplayResult` in either its own file or co-located with the service, not in `HeadlessGenerationContracts.cs`.

### Risk: **LOW**
Pure additive Core code with strong TDD discipline and well-understood dependencies.

---

## Plan 07-02: CLI/Headless Wiring

### Strengths
- **Gate ordering is correct** - replay before overwrite preflight before writes (matches research Pattern 3 and existing missing-profile blocker pattern from Phase 6).
- **Maps blocked replay to `AutomationExitCode.ValidationBlocked`** preserving stable script-facing exit codes (D-09).
- **BoS-only non-replay test** locks D-01 behavior so a future refactor can't accidentally replay on `--intent bos`.
- **Stale-assignment fixture** - task 1 explicitly requires fixtures where existing assignments are wrong, matching research Pitfall 5.
- **Reuses `ConsoleCaptureCollection`** for `Program.Main` tests (matches Phase 6 LEARNINGS).

### Concerns
- **[HIGH] `cloneBeforeReplay: true` for CLI is a behavioral change vs research recommendation A1.** Research assumption A1 says "Always use the helper result's `Project` and clone for both CLI and bundle" which the plan follows - fine. But the existing `HeadlessGenerationService.Run` *also* uses `project` for `templateGenerationService.GenerateTemplates(project.SliderPresets, ...)` and `bosJsonExportWriter.Write(..., project.SliderPresets, ...)`. After Plan 02, BodyGen morph generation uses `replayResult.Project`, but templates and BoS still use `project` (the loaded one) - **except both should produce identical bytes since `SliderPresets` is cloned identically by `ReplaceWith`**. Confirm the test asserts `templates.ini` bytes are identical with and without replay (since templates don't depend on NPC assignments). If `ReplaceWith` ever introduces preset-cloning drift, byte parity breaks silently. Add an explicit byte-equality test for `templates.ini`.
- **[MEDIUM] Replay summary text destination is unclear.** The plan says "Append success message text such as `Assignment strategy replayed: {kind}; assigned NPCs: {assigned}; blocked NPCs: 0.`" to `HeadlessGenerationResult.Message`. But on success today, `Program.cs` likely prints `WrittenFiles` paths (per existing test `ProgramMainGenerateAllInvokesServiceInProcessAndPrintsWrittenFiles`). Where does this success message actually surface in CLI stdout? Task 1 says "Program.Main test: `generate --intent bodygen` prints the concise replay summary on stdout when replay succeeds" - but no task in 07-02 modifies `src/BS2BG.Cli/Program.cs`. If `Program.WriteResult` only prints `WrittenFiles` on success, the summary will be invisible. **Either (a) include a `Program.cs` edit in `files_modified`, or (b) clarify that `Result.Message` is included in stdout, or (c) drop the Program.Main stdout assertion.**
- **[MEDIUM] Failure-message PII scope.** "Failure message must include blocked NPC `Mod`, `Name`, `EditorId`, `Race`, `FormId`, and `Reason`." For CLI this goes to stderr. NPC names from BodySlide projects are typically character names from mods (public data), not personally identifying - so this is fine. But the threat model T-07-05 says "do not include private file paths beyond existing output error handling" which is correct. Worth a one-line confirmation that no `request.ProjectPath` or output directory leaks into the blocked-NPC message.
- **[MEDIUM] Service construction.** "Instantiate `AssignmentStrategyReplayService` with a `MorphAssignmentService` that uses `RandomAssignmentProvider`, or inject/store an equivalent Core-only replay service inside `HeadlessGenerationService`." This is vague. The current `HeadlessGenerationService` constructor takes 7 services. Adding `AssignmentStrategyReplayService` as an 8th constructor parameter is the cleanest answer, but this changes the public constructor signature, which `CliGenerationTests.CreateHeadlessService()` and `Program.cs` both depend on. The plan should be explicit: **add as constructor parameter and update both call sites**, not lazy-instantiate.
- **[LOW] No test that overwrite preflight still runs after replay.** Task 1 doesn't assert ordering. Add a test where replay succeeds and target files exist without `--overwrite` -> expect `OverwriteRefused`, not `Success`.

### Suggestions
- Add `src/BS2BG.Cli/Program.cs` to `files_modified` if the success summary needs to print to stdout - or change Task 1 to assert on `result.Message` text, not stdout.
- Specify constructor injection explicitly: "Add `AssignmentStrategyReplayService replayService` as a new constructor parameter; update `CreateHeadlessService()` test factory and `Program.cs` DI composition."
- Add an explicit `templates.ini` byte-equality test (clone vs no-clone) to lock that templates are unaffected by the replay clone path.
- Add an overwrite-after-successful-replay test to lock gate ordering.

### Risk: **MEDIUM**
The wiring is straightforward but the constructor change and stdout-summary plumbing have unstated knock-on effects on `Program.cs` and existing tests.

---

## Plan 07-03: Bundle Wiring

### Strengths
- **Source-project preservation contract is explicit** - `Entry("project/project.jbs2bg", ..., SaveToString(request.Project, ...))` stays unchanged, only `AddGeneratedOutputEntries` is modified.
- **Test 1's project entry preservation assertion** directly catches Pitfall 1 from research.
- **Cloned working state** addresses the bundle-specific risk that `request.Project` is a *caller-shared* model (vs CLI's freshly-loaded project).
- **`PortableProjectBundleOutcome.ValidationBlocked`** reuses existing outcome enum - no schema change.
- **Privacy/path-scrubbing preserved** through existing report-text pipeline.

### Concerns
- **[HIGH] BodyGen uses replay clone, BoS uses original - possible drift.** The plan says "use `replayResult.Project` for BodyGen template/morph generation but continue to use the original request project for BoS JSON if appropriate." This means within a single `OutputIntent.All` bundle, two different `ProjectModel` instances feed two different writers. Today, `AddGeneratedOutputEntries` does:
  ```csharp
  templateGenerationService.GenerateTemplates(request.Project.SliderPresets, requestProfileCatalog, ...)  // BodyGen
  bosJsonExportWriter.Write(bosDirectory, request.Project.SliderPresets, requestProfileCatalog)           // BoS
  ```
  After Plan 03, BodyGen will use `replayResult.Project.SliderPresets` and BoS will use `request.Project.SliderPresets`. **`ReplaceWith` clones presets identically (line 75-80 of ProjectModel.cs), so bytes should match - but this is a non-obvious invariant** that depends on clone fidelity. Two safer options: (1) Use `replayResult.Project` for both BodyGen *and* BoS (since BoS doesn't depend on assignments, this is byte-equivalent and simpler to reason about), or (2) Add an explicit byte-parity test asserting `outputs/bos/Alpha.json` bytes are identical with and without `AssignmentStrategy` present. **Recommend option (1).**
- **[HIGH] Constructor signature change.** `PortableProjectBundleService` has both a public and an internal constructor. Adding `AssignmentStrategyReplayService` requires updating both, plus all call sites in `MainWindowViewModel` (App layer), `AppBootstrapper` (DI), `CliGenerationTests`, and `PortableBundleServiceTests` (`CreateService`, `CreateBundledOnlyService`, `CreateServiceWithCommitter`). The plan does not enumerate these. The bundle service is consumed by both CLI and GUI, so this is broader than CLI alone.
- **[MEDIUM] Templates use `SliderPresets`, which doesn't depend on NPC assignments.** Calling `templateGenerationService.GenerateTemplates(replayResult.Project.SliderPresets, ...)` when no strategy is present is a no-op semantically (clone of presets has identical content). But when `cloneBeforeReplay: true` and no strategy exists, you've cloned an entire `ProjectModel` (including `MorphedNpcs`, `CustomMorphTargets`, `CustomProfiles`) just to use `SliderPresets`. For typical projects this is fine; for projects with thousands of NPCs it's wasted allocation. Consider: skip the clone when `AssignmentStrategy is null` (Plan 01 already does this *internally* for the strategy application, but if `cloneBeforeReplay: true` is passed, the clone happens before the short-circuit). **Confirm Plan 01's contract: does `cloneBeforeReplay: true` clone before or after the strategy null check?** The Plan 01 code sample suggests clone first, then short-circuit - meaning bundle always pays clone cost. Acceptable, but worth flagging.
- **[MEDIUM] Replay summary surface in bundle artifacts.** Research assumption A2 says "Add summary to result/preview-facing report text only; do not alter manifest schema." Plan 03 follows this but doesn't specify *which* report. Bundle has `reports/validation.txt` (built from `reportTextFormatter.Format(validationReport, ...)`). The replay summary is *not* a validation finding - appending it to validation.txt mixes concerns. Cleanest options: (a) append a dedicated `reports/replay.txt`, (b) extend `validation.txt` with a separate section, or (c) add to `PortableProjectBundleResult`/`PortableProjectBundlePreview` as a new top-level string property. The plan should pick one explicitly so tests can target the exact surface.
- **[MEDIUM] Test 1's "Project entry preservation test"** asserts `project/project.jbs2bg` "still contains the original stale/source assignment." This requires the test fixture to have a deserializable, non-empty NPC assignment in the saved project that is *different* from what replay produces. Make sure the fixture stale assignment is to a *valid* preset name (so JSON deserialization round-trips), not just an arbitrary string - otherwise the assertion is checking string contains, not project semantics. The pseudo-test in research uses `.Should().Contain("StalePreset")` which is a string-contains check on the raw JSON; that's fragile but workable. Consider parsing the bundled `project.jbs2bg` back through `ProjectFileService.Load` and asserting on `project.MorphedNpcs[0].SliderPresets`.
- **[MEDIUM] No test asserts `request.Project.MorphedNpcs` is unmutated after `Create()` returns.** D-05 says "Bundle generation must avoid mutating the caller's project model." The only way `cloneBeforeReplay: true` can fail to honor this is if `ReplaceWith` shares references (it doesn't - it clones), but a regression test should exist: assert NPC assignments on the *caller's* `ProjectModel` are byte-identical before and after `service.Create(request)`.
- **[LOW] Preview success/blocker behavior** - Task 1 mentions "Preview test: `Preview` reports the same replay success/blocker outcome without writing the zip." Today `Preview` calls `BuildPlan` which calls `AddGeneratedOutputEntries` (which materializes generated bytes into staging directory just to checksum them). Replay will run during preview too. Confirm this is intentional - it's correct per D-09 (preview must show blocker details), just non-obvious.

### Suggestions
- **Use `replayResult.Project` for both BodyGen and BoS in `AddGeneratedOutputEntries`** to eliminate the dual-instance footgun.
- Enumerate all constructor call sites that need updating (`MainWindowViewModel`, `AppBootstrapper`, three test factories).
- Decide replay-summary surface explicitly - recommend a new property on `PortableProjectBundleResult`/`Preview` rather than embedding in `validation.txt`.
- Add an explicit "caller's `ProjectModel` unmutated" test after `service.Create(request)` returns.
- Parse the bundled `project.jbs2bg` back through `ProjectFileService.Load` for the preservation test instead of relying on string-contains assertions on raw JSON.

### Risk: **MEDIUM-HIGH**
The bundle path has more surface area (constructor changes ripple to GUI), the dual-instance pattern is subtle, and the replay-summary destination is underspecified.

---

## Cross-Plan Concerns

### Dependency Graph
- ✅ Wave 1: 07-01 (no deps)
- ✅ Wave 2: 07-02 and 07-03 both depend on 07-01 only - correctly parallelizable.

### Coverage Gaps
- **[MEDIUM] No phase-level integration test** that runs `generate` and `bundle` against the *same* fixture project to confirm `morphs.ini` bytes match between CLI and bundle outputs. Phase 6 had this implicit through shared `RequestScopedProfileCatalogComposer` testing; Phase 7 should too. Add to either Plan 02 or 03.
- **[LOW] No test for projects with `AssignmentStrategy` but zero `MorphedNpcs`** - does replay return `Replayed=true, AssignedCount=0, BlockedNpcs=[]` (no-op success) or `Replayed=false`? The current `MorphAssignmentService.ApplyStrategy` will iterate an empty list and return `AssignmentStrategyResult(0, [])`. Plan 01's Test 1 should cover this corner.

### Scope & Over-engineering
- ✅ Scope is tight and matches phase goal.
- ✅ Reuses existing services; no new RNG, no new strategy kinds, no writer changes.
- ⚠️ The `cloneBeforeReplay` parameter is the only optional knob. Given that the user-facing decisions all favor "clone always" (D-05), consider whether the parameter should exist at all, or whether the helper should always clone. Keeping it gives tests a way to assert the no-clone path doesn't mutate when it shouldn't, which is useful - keep it but document clearly.

### Performance
- Bundle clone of full `ProjectModel` (including potentially thousands of NPCs) on every `Preview`/`Create` is acceptable for a one-shot CLI/GUI operation but worth a comment.
- No new I/O; no new external calls; deterministic.

### Security
- Threat model captures the relevant tampering and information-disclosure risks.
- Privacy scrubbing is preserved through existing `BundlePathScrubber`.
- One missing item: **confirm that blocked NPC details written to bundle reports go through the same path-scrubbing pipeline as validation report text.** If a strategy rule somehow includes a path-like string (it shouldn't - rules contain preset names and race filters), it would bypass scrubbing.

---

## Overall Risk Assessment: **MEDIUM**

**Justification:**
- Plan 01 is low-risk additive Core work with strong TDD.
- Plans 02 and 03 are well-designed but underspecified in three areas: (a) constructor-injection ripple effects on existing tests and DI, (b) where the success replay summary actually surfaces (CLI stdout vs `result.Message` vs bundle report), and (c) the dual-`ProjectModel`-instance pattern in bundle generation that depends on `ReplaceWith` clone fidelity for byte parity.
- None of these are blockers, but each adds review surface for the executor and could produce subtle test failures or runtime drift if not addressed before implementation.

**Recommended pre-execution adjustments:**
1. Plan 02: explicitly add `Program.cs` to `files_modified` (or remove the stdout-summary assertion).
2. Plan 02 & 03: enumerate all constructor call sites that need updating.
3. Plan 03: use `replayResult.Project` for both BodyGen and BoS generation, eliminating the dual-instance pattern.
4. Plan 03: pick an explicit surface for the replay summary (recommend a new property on result/preview records, not embedded in `validation.txt`).
5. All plans: add a cross-CLI/bundle byte-parity test for `morphs.ini` produced from the same fixture project.

With these tightenings, risk drops to **LOW-MEDIUM**.

---

## Codex Review

## Overall Summary
The three-plan structure is sound: Wave 1 creates the shared Core seam, and Wave 2 wires it into CLI and bundle paths with targeted integration tests. The main risk is not scope, but a few integration details that could leave Phase 7 only partially closed: seeded deterministic replay may not be guaranteed if the new seam uses `MorphAssignmentService(new RandomAssignmentProvider())`, bundle success/failure replay details do not currently have a clear contract surface, and validation ordering could block replay before stale assignments are replaced.

## 07-01-PLAN.md

### Summary
Strong foundational plan. It correctly isolates replay in Core, uses TDD, avoids writer changes, and covers request-scoped cloning. It needs sharper coverage for seeded deterministic replay and blocked partial-mutation semantics.

### Strengths
- Centralizes replay in one Core service, reducing CLI/bundle drift.
- Uses `ProjectModel.ReplaceWith` for clone-based mutation isolation.
- Tests BodyGen/All vs BoS-only scope, no-strategy behavior, blockers, and cloning.
- Keeps byte-sensitive generation/export writers out of scope.

### Concerns
- **HIGH:** Seeded replay behavior is under-specified. The plan says to use `MorphAssignmentService.ApplyStrategy`, but the existing deterministic seed path appears to live on `AssignmentStrategyService.Apply(project, strategy)`. If the helper is built with `RandomAssignmentProvider`, saved seeded strategies may not replay deterministically.
- **MEDIUM:** Blocked replay may partially mutate eligible NPCs before returning blockers. That can be acceptable, but the service/result should document that callers must discard or block output when `IsBlocked`.
- **LOW:** `cloneBeforeReplay` is good, but tests should assert source project assignments, dirty state expectations, and strategy config remain unchanged.

### Suggestions
- Add a direct `SeededRandom` test with a pinned expected assignment sequence.
- Clarify or adjust the provider path so saved `strategy.Seed` is honored while preserving the random-provider abstraction.
- Add a blocked replay test where one NPC is assigned and one is blocked, then assert callers can identify the blocked result and avoid using partial output state.

### Risk Assessment
**MEDIUM.** The seam is the right abstraction, but seeded replay is central to "deterministic automation." If that path is wrong, later plans will inherit the bug.

## 07-02-PLAN.md

### Summary
The CLI integration plan targets the right behavior and includes meaningful end-to-end checks. The main gap is ordering: replay may need to happen before certain validation/export-readiness checks, or the working replayed project should be what BodyGen validation/generation sees.

### Strengths
- Tests stale/wrong assignments so replay is actually proven.
- Covers BodyGen, All, BoS-only, no-strategy, blocked replay, and `Program.Main`.
- Requires `ValidationBlocked` exit code and no output files on replay failure.
- Keeps replay automatic, matching D-02.

### Concerns
- **HIGH:** Calling replay after validation can be too late if validation blocks on stale assignment references that replay would replace. Existing validation has blocker paths for missing preset references.
- **HIGH:** Same seeded-strategy risk as Plan 01 if `HeadlessGenerationService` composes the replay service with plain `RandomAssignmentProvider`.
- **MEDIUM:** "No output files" should explicitly include all requested `OutputIntent.All` outputs, not just `templates.ini` and `morphs.ini`.
- **LOW:** Success summary appended only to `HeadlessGenerationResult.Message` is fine, but tests should avoid brittle exact full-message matching.

### Suggestions
- Define the exact ordering as: load project, compose catalog, prepare replay working state for BodyGen intents, block replay failures, then plan/overwrite/write using the proper source or working project per output type.
- Add tests for `OutputIntent.All` blocked replay proving no BoS JSON is written either.
- Add a seeded CLI test that saves a seeded strategy and verifies repeated runs produce identical `morphs.ini`.

### Risk Assessment
**MEDIUM-HIGH.** The plan will likely close the common CLI gap, but validation ordering and seeded determinism are correctness risks.

## 07-03-PLAN.md

### Summary
The bundle plan correctly protects `project/project.jbs2bg` as source state while generating BodyGen output from replayed working state. The weakest area is reporting: current bundle result/preview contracts do not obviously carry success summary or blocked replay details, so D-08/D-09 need a more explicit contract change.

### Strengths
- Explicitly preserves original bundled project state.
- Tests generated `morphs.ini` separately from `project/project.jbs2bg`.
- Covers BodyGen/All, BoS-only, blocked replay, and preview behavior.
- Keeps path scrubbing and no-private-path constraints in view.

### Concerns
- **HIGH:** Replay summary/blocker details have no clear result surface. `PortableProjectBundlePreview`/`Result` currently expose manifest, validation report, and privacy findings, not report text or status messages. Do not overload `PrivacyFindings` with replay details.
- **HIGH:** Same seeded-strategy risk if bundle replay uses `MorphAssignmentService(new RandomAssignmentProvider())`.
- **MEDIUM:** `reports/validation.txt` is built before replay in the current service. If success summaries belong there, the plan must explicitly rebuild/append report text before adding the report entry and scanning privacy.
- **MEDIUM:** Preview currently builds a plan and stages generated files in a temp directory. The plan should distinguish "does not write destination zip" from "may create temp staging files," and tests should assert cleanup if practical.

### Suggestions
- Extend contracts with a typed `ReplaySummary` / `StatusMessages` / `ReportText` surface, or add a synthetic validation/report finding. Pick one and test both preview and create.
- Ensure blocked replay details include NPC identity and reason in a surface the CLI/App can actually display.
- Add a test that opens the zip and confirms `reports/validation.txt` contains the concise replay success summary.
- Add seeded bundle replay tests with repeated bundle creation producing identical `outputs/bodygen/morphs.ini`.

### Risk Assessment
**MEDIUM-HIGH.** The generation/state split is right, but the reporting contract and seeded determinism need tightening before this is execution-ready.

## Final Recommendation
Approve the phase shape, but revise the plans before implementation. The required revisions are small and concrete: lock down seeded replay through tests and service design, make bundle replay reporting a first-class contract, and clarify validation/replay ordering so stale assignment state cannot block or leak into automation outputs.

---

## Consensus Summary

The three reviewers broadly agree that Phase 7 is well-scoped and architecturally sound: a shared Core replay seam, followed by CLI and bundle integration, is the right shape. The highest-value pre-execution changes are to tighten contract surfaces and ordering rather than rework the approach.

### Agreed Strengths
- The shared Core `AssignmentStrategyReplayService` seam is the right abstraction to prevent CLI and bundle drift.
- Reusing the existing assignment strategy services preserves the random-provider abstraction and avoids new assignment algorithms.
- The stale-assignment test strategy is high signal because it proves replay actually happens instead of merely preserving existing assignments.
- Request-scoped cloning is the correct safety posture for bundle output and likely acceptable for CLI consistency.
- Treating blocked replay as fatal before output writes matches the modder-trust and reproducibility goals.

### Agreed Concerns
- **Replay summary/reporting surface is underspecified.** Claude and Codex both flagged that CLI stdout/result messages and bundle preview/result/report surfaces need an explicit contract so D-08/D-09 are actually visible to users and tests.
- **Bundle dual-project generation is subtle.** Gemini and Claude both recommend avoiding a BodyGen-from-clone / BoS-from-original split, or at least testing clone byte parity. The simplest revision is to use `replayResult.Project` consistently for generated outputs while preserving `project/project.jbs2bg` from `request.Project`.
- **Constructor/composition ripple effects need to be enumerated.** Claude flags `Program.cs`, test factories, App bootstrap, and ViewModel call sites. Codex flags the same risk through provider composition and deterministic replay behavior.
- **Seeded deterministic replay needs explicit tests and construction guidance.** Codex raised this as a high-risk gap; it should be addressed by pinned seeded replay tests and clear service composition through the existing provider-compatible path.
- **Blocking and write-order tests should cover full output intents.** Codex and Claude both recommend proving blocked `OutputIntent.All` creates no BodyGen or BoS outputs, and that overwrite preflight still happens after successful replay but before writes.

### Divergent Views
- Gemini assesses overall risk as low, while Claude and Codex rate the implementation risk medium to medium-high because of integration details. This is not a disagreement on architecture; it reflects whether the current plan wording is precise enough for execution.
- Gemini suggests a `ProjectModel.Clone()` helper, while Claude treats the existing `ReplaceWith` path as sufficient but asks for dirty-state and mutation tests. Add the tests first; only add a clone helper if execution shows duplication or readability pressure.
- Claude recommends a new result/preview property for bundle replay summaries, while Codex allows a typed summary, status messages, report text, or synthetic validation/report finding. The common requirement is to pick one explicit surface and test it.
