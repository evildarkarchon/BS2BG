---
phase: 7
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-28T01:58:08.7316634-07:00
plans_reviewed: [07-01-PLAN.md, 07-02-PLAN.md, 07-03-PLAN.md]
---

# Cross-AI Plan Review - Phase 7

## Gemini Review

This review evaluates the implementation plans for **Phase 7: Replay Saved Strategies in Automation Outputs**. These plans aim to ensure that CLI and portable bundle outputs are reproducible from saved project data by replaying deterministic assignment strategies before morph generation.

---

### **General Phase Assessment**
The phase is exceptionally well-architected. It transforms the current "stale assignment" bug into a robust, "fail-fast" automation pipeline. By centralizing the replay logic into a dedicated Core service (`AssignmentStrategyReplayService`) and strictly separating the "source project" (for serialization) from the "working project" (for generation), the plans achieve high-confidence reproducibility. The plans also proactively incorporate feedback from previous AI reviews, particularly regarding seeded determinism and constructor ripple effects.

---

### **07-01-PLAN.md: Core Replay Seam**

#### **Summary**
This foundational plan creates the reusable Core seam required for all automation paths. It focuses on isolating mutation via cloning and preserving the random-provider abstraction.

#### **Strengths**
*   **Encapsulation**: Centralizing the replay logic in `AssignmentStrategyReplayService` prevents divergence between CLI and bundle behavior.
*   **RNG Integrity**: The update to `MorphAssignmentService.ApplyStrategy` ensures that saved seeded strategies honor the deterministic path while unseeded GUI draws still use the injected provider.
*   **Dirty State Protection**: Explicit TDD cases for `IsDirty` and `ChangeVersion` guarantee that the source project remains pristine during automation.

#### **Concerns**
*   **Performance (LOW)**: Cloning a `ProjectModel` with thousands of NPCs incurs allocation overhead. However, this is negligible compared to the I/O of bundle creation and is a necessary trade-off for state safety.
*   **NPC Identity (LOW)**: Cloned NPC instances will have new memory references. The plan correctly mitigates this by identifying blocked NPCs via stable domain properties (`Mod`, `FormId`, etc.) instead of object references.

#### **Suggestions**
*   **ProjectModel.Clone()**: While `ReplaceWith` is effective, adding a surgical `ProjectModel.Clone()` extension or method would make the orchestration in Task 2 even cleaner.

#### **Risk Assessment: LOW**
The plan uses standard patterns, avoids byte-sensitive writers, and has a strong TDD gate.

---

### **07-02-PLAN.md: CLI/Headless Wiring**

#### **Summary**
This plan integrates the replay seam into the CLI. It ensures that `generate --intent bodygen/all` produces replayed output and blocks generation if any NPCs are ineligible for the saved strategy.

#### **Strengths**
*   **Correct Gate Ordering**: Replay occurs before overwrite preflight and file writes, ensuring no files (including BoS) are produced if BodyGen replay fails.
*   **Visibility**: Updating `Program.cs` to surface the replay summary on stdout fulfills the requirement for concise success reporting without cluttering scripts.
*   **Stale-Assignment Fixtures**: The requirement to use "intentionally wrong" assignments in tests is the most effective way to prove that replay is actually functioning.

#### **Concerns**
*   **Templates Parity (LOW)**: Since `templates.ini` does not depend on assignments, using a cloned project could technically introduce drift if the clone logic were broken. The plan mitigates this with a specific parity test.

#### **Suggestions**
*   **Exit Code Documentation**: Ensure the CLI `--help` or documentation reflects that `ValidationBlocked` (2) now covers assignment strategy failures.

#### **Risk Assessment: LOW**
The wiring is surgical and the dependency on the Plan 01 seam is clearly defined.

---

### **07-03-PLAN.md: Bundle Wiring**

#### **Summary**
This plan wires replay into the portable bundle service. It is the most complex of the three, requiring updates to both the Core bundling logic and the GUI's dependency injection.

#### **Strengths**
*   **Semantic Preservation**: Generating `outputs/bodygen/morphs.ini` from replayed state while keeping `project/project.jbs2bg` as the original source state perfectly balances reproducibility with project integrity.
*   **Consistent Generation Source**: Refactoring `AddGeneratedOutputEntries` to use the same `outputProject` for all writers (BodyGen and BoS) eliminates the risk of "dual-project" drift identified in earlier reviews.
*   **Reporting Contract**: Adding `ReplayReportText` to the bundle result and a dedicated `reports/replay.txt` in the zip ensures the replay outcome is persistent and discoverable.

#### **Concerns**
*   **Constructor Ripple Effects (MEDIUM)**: This plan modifies constructors used by the CLI, the GUI (`AppBootstrapper`), and three different test factories. Failure to update any of these will cause compilation or runtime DI errors. **Mitigation**: Task 2 explicitly enumerates these sites.
*   **Privacy Scrubbing (LOW)**: Failure details include NPC names. While usually public mod data, the plan correctly ensures these go through the `BundlePathScrubber` just in case.

#### **Suggestions**
*   **Test Semantic Preservation**: The plan's suggestion to parse the bundled `.jbs2bg` back through `ProjectFileService.Load` to verify assignments is much stronger than a "string contains" check; ensure this is prioritized in the implementation.

#### **Risk Assessment: MEDIUM**
The risk is slightly elevated due to the breadth of the constructor changes (App/CLI/Tests), but the logic itself is sound and the verification plan is robust.

---

### **Final Verdict**
**APPROVED.** The plans are high-quality, technically precise, and directly address the core value of "modder trust" by ensuring automation is deterministic and verifiable. Proceed with Wave 1.

---

## the agent Review

# Cross-AI Plan Review: Phase 07 (Revision Pass)

## Summary

The revisions absorb most of the prior round's high-value feedback: the constructor ripple in Plans 02/03 is now enumerated, the bundle dual-project footgun is collapsed by using `replayResult.Project` for both BodyGen and BoS generation, the replay summary has an explicit contract surface (`ReplayReportText` + `reports/replay.txt`), and Plan 01 grew explicit seeded-determinism, dirty-state, and empty-NPC tests. The biggest remaining issue is **a real behavioral bug introduced by Plan 01's edit to `MorphAssignmentService.ApplyStrategy`**: the new seeded dispatch path uses `AssignmentStrategyService.Apply(project, strategy)` (2-arg static) and silently drops the `eligibleRows` parameter, regressing existing GUI scoped-bulk semantics. Plan 02 also leaves CLI's BoS path using the source `project.SliderPresets` while BodyGen uses the clone — mirroring the dual-project pattern that was correctly eliminated in Plan 03.

---

## Plan 07-01: Core Replay Seam

### Strengths
- 7 explicit test cases now cover D-01, D-03, D-05, D-06, D-07, D-08, D-09 plus seeded determinism, dirty/ChangeVersion invariants, and empty-NPC corner cases.
- `AssignmentStrategyReplayResult` moved to its own `AssignmentStrategyReplayContracts.cs` file — bundle code no longer imports a "Headless" file. Good.
- Test 5 asserts `IsDirty` and `ChangeVersion` on the source project — Claude's clone-fidelity concern.
- Test 6 pins a seeded sequence — Codex's seeded-determinism concern.
- `[Theory]` over `BodyGen` and `All` locks both intents independently.

### Concerns
- **[HIGH] `MorphAssignmentService.ApplyStrategy` regression: `eligibleRows` is silently dropped in the seeded path.** Plan 01 Task 2 says "when `strategy.Seed.HasValue`, delegate to `AssignmentStrategyService.Apply(project, strategy)`" — that's the 2-arg static overload that uses `eligibleRows: null` (= all `MorphedNpcs`). Existing GUI callers in `MorphsViewModelStrategyTests` and the scoped-bulk paths pass `eligibleRows` for visible/selected scopes. After this change, **a seeded GUI scoped-bulk run will silently expand to full project scope**, violating WORK-03. The fix is straightforward: branch on the provider, not the entry point:
  ```csharp
  var service = strategy.Seed.HasValue
      ? new AssignmentStrategyService(new DeterministicAssignmentRandomProvider(strategy.Seed.Value))
      : new AssignmentStrategyService(randomAssignmentProvider);
  return service.Apply(project, strategy, eligibleRows);
  ```
  The plan should mandate this exact shape (or explicitly state `eligibleRows` is preserved across both paths).
- **[HIGH] Plan 01's targeted gate misses GUI regressions.** Acceptance only runs `AssignmentStrategyReplayServiceTests`. Because Task 2 modifies `MorphAssignmentService.ApplyStrategy` — a service consumed by `MorphsViewModel` — the gate must include `AssignmentStrategyServiceTests` and `MorphsViewModelStrategyTests` (or the full suite). Otherwise the `eligibleRows` regression and any other GUI-side breakage land silently and only surface in Wave 2/3 noise.
- **[MEDIUM] Test 4 documents partial mutation as expected without explicit guard rails.** "Documents that eligible rows may have been mutated on the working project" is the right factual statement, but Test 4 should also explicitly assert `result.IsBlocked == true` so downstream code (Plan 02/03) cannot accidentally consume a partially-mutated working project for output writes. Make the contract: callers must not use `Project` for generation when `IsBlocked` is true.
- **[MEDIUM] No-clone path is untested.** Test 5 covers `cloneBeforeReplay: true` (source unchanged). The `cloneBeforeReplay: false` path mutates the source — but no test asserts that this is the actual behavior. Add a sibling test so both contracts are locked; otherwise nothing prevents a future "always clone" regression.
- **[LOW] WHY comment on the seeded fork.** The seeded vs unseeded dispatch in `MorphAssignmentService` is non-obvious; a future reader will wonder why two code paths exist. The plan asks for a "short WHY comment" but doesn't specify what to say. Suggested wording: "Seeded strategies must use the deterministic provider directly so saved seeds reproduce identically across automation runs; unseeded calls keep the injected provider so tests can substitute their own draws."

### Suggestions
- Replace the seeded-dispatch action with the `eligibleRows`-preserving form above and add it to acceptance criteria as `eligibleRows` appears in both branches.
- Expand Plan 01 acceptance gate to `dotnet test --filter "FullyQualifiedName~AssignmentStrategyReplayServiceTests|FullyQualifiedName~AssignmentStrategyServiceTests|FullyQualifiedName~MorphsViewModelStrategyTests"`.
- Add a no-clone source-mutation test to Test 5 (or as Test 5b).

### Risk: **MEDIUM**
The replay seam itself is well-designed, but the `MorphAssignmentService` edit is a load-bearing change to a shared service with a real downstream regression baked into the plan as written.

---

## Plan 07-02: CLI/Headless Wiring

### Strengths
- `Program.cs` now in `files_modified` and constructor injection is fully spelled out.
- Stale-assignment fixture, seeded determinism repeat-test, BoS-only non-replay, no-strategy fallback, and `OutputIntent.All` blocker-no-write all covered.
- Explicit gate-order test (replay → overwrite preflight → writes).
- Templates byte-parity test (clone vs no-clone) locks the cloned-project invariant.

### Concerns
- **[MEDIUM] CLI dual-project pattern reintroduced.** Plan 02 Task 2 says "use `replayResult.Project` for BodyGen validation and BodyGen `PlanTargets`/template/morph generation; keep BoS JSON using the same `requestProfileCatalog` and byte-equivalent slider presets." This is exactly the dual-project shape that Plan 03 correctly eliminates. CLI should use `replayResult.Project` consistently for **all** generation (BodyGen + BoS), since `SliderPresets` clone fidelity is what makes the templates parity test pass — the same fidelity covers BoS bytes. Either commit to single-project consistency in CLI to match Plan 03, or add an explicit BoS byte-parity test mirroring the templates test.
- **[MEDIUM] Replay-before-validation is implicit.** The plan lists order as "after project load and request profile catalog construction, then run validation/missing-profile checks/overwrite preflight/output planning". But the validation that follows must operate on `replayResult.Project`, not the source `project` — otherwise stale-assignment validation findings could block runs that replay would have fixed (Codex's HIGH concern from prior round). The plan implies this but doesn't enforce it; spell it out: "Validation, missing-profile checks, and PlanTargets all run against `replayResult.Project`. The original `project` is referenced only when `replayResult.Replayed == false`."
- **[MEDIUM] `HeadlessGenerationResult.Message` semantics on success are ambiguous.** Today the success message is `"Generation completed successfully."` and `WriteResult` already prints it on stdout. The plan says to "append" the replay summary — does that mean the final Message becomes `"Assignment strategy replayed: {kind}; assigned NPCs: {n}; blocked NPCs: 0.\nGeneration completed successfully."`, or does the summary replace the existing line, or is there a new field? Tests assert `StandardOutput.Should().Contain("Assignment strategy replayed")`, but they don't lock the exact format. Pick: `Message` is multi-line with the replay summary first, then the success line — and lock it with a test that checks both substrings.
- **[MEDIUM] Unseeded-strategy reproducibility hole.** With Plan 01's fix, seeded strategies are deterministic via `DeterministicAssignmentRandomProvider`, but unseeded strategies use the injected `RandomAssignmentProvider` — so repeated CLI runs of the same unseeded saved strategy produce different `morphs.ini` bytes. This may be intentional (users who want reproducibility save a seed) but it should be acknowledged in the plan. Otherwise users running `bs2bg generate` twice with an unseeded saved strategy will be surprised when bytes change. At minimum: document this in the success message (e.g., "(unseeded — non-deterministic)") or in `07-LEARNINGS.md`.
- **[LOW] Failure-message PII assertion is loose.** The plan says blocked NPC details "must not include `request.ProjectPath` or output-directory strings". A test should explicitly assert `result.Message.Should().NotContain(request.ProjectPath).And.NotContain(request.OutputDirectory)` to lock this.

### Suggestions
- Use `replayResult.Project` for both BodyGen and BoS in `HeadlessGenerationService.Run`. Add an explicit BoS byte-parity test (clone vs no-clone) mirroring the templates test.
- Add a "validation runs against replay project" assertion: a test where the source project has a stale preset reference that validation would block but replay replaces, and confirm `Success` not `ValidationBlocked`.
- Lock the success-Message format with a positive contains-both assertion.
- Add explicit "no project path / no output dir leak" assertion to the blocked-replay test.

### Risk: **MEDIUM**
Solid integration plan but the dual-project hangover from earlier reviews lingers in CLI even after Plan 03 fixed it for bundles. Validation-against-which-project ordering is also load-bearing and underspecified.

---

## Plan 07-03: Bundle Wiring

### Strengths
- All constructor call sites enumerated: `Program.cs`, `AppBootstrapper.cs`, `MainWindowViewModel.cs`, `CreateService`, `CreateBundledOnlyService`, `CreateServiceWithCommitter`.
- Single working project (`replayResult.Project` or `outputProject` parameter) feeds both BodyGen and BoS — the dual-project footgun is gone.
- Explicit `ReplayReportText` property on both `Preview` and `Result`, **plus** `reports/replay.txt` zip entry — both surfaces locked and testable.
- `project/project.jbs2bg` preserved via the existing `SaveToString(request.Project, request.SaveContext)` call — explicitly called out.
- Project-entry preservation test parses through `ProjectFileService.Load` (semantic, not string-contains) — Claude's robustness concern addressed.
- Caller-mutation test asserts `request.Project` `ChangeVersion`/`IsDirty`/assignments unchanged — D-05 enforcement.
- Cross-CLI/bundle byte parity test in Task 1 — closes the cross-plan integration gap from prior round.

### Concerns
- **[MEDIUM] `PortableProjectBundlePreview`/`Result` are positional records.** Adding `ReplayReportText` as a new property requires updating every constructor invocation in source and tests. Looking at the codebase, the existing test file constructs these directly:
  ```csharp
  var preview = new PortableProjectBundlePreview(
      PortableProjectBundleOutcome.Success,
      new[] { ... },
      "{...}",
      report,
      new[] { "..." });
  ```
  Adding a 6th positional parameter breaks every such call. The plan doesn't enumerate these test sites (e.g., `PreviewContractsExposeEntriesManifestValidationReportPrivacyFindingsAndOutcome` in `PortableBundleServiceTests.cs`). Either make `ReplayReportText` a non-positional `public string ReplayReportText { get; init; } = string.Empty;` after the record header, or list every breaking call site. The init-property approach is simpler and backward-compatible.
- **[MEDIUM] Validation-and-replay ordering for bundles is underspecified the same way as Plan 02.** Today `BuildPlan` runs validation against `request.Project` first, then `MissingProfile` check, then output entries. With replay, validation should run against `replayResult.Project` (otherwise stale-assignment findings could block). The plan says "after request-scoped catalog composition but before validation/missing-profile checks that could be affected by stale assignments" — making it explicit as: "Replay first; if blocked, return `ValidationBlocked` immediately. Otherwise run validation on `replayResult.Project`, then missing-profile on `replayResult.Project`."
- **[MEDIUM] Manifest must include `reports/replay.txt`.** The bundle manifest already enumerates every entry with SHA-256 checksum. Adding `reports/replay.txt` to the zip without adding it to the manifest would break the existing checksum invariant tested by `BundleOutputBytesExactlyMatchExistingWriters` and friends. The plan adds the file but doesn't say "add the entry to the manifest list with kind = `replay-report`". Make this explicit.
- **[MEDIUM] No `reports/replay.txt` for projects without a saved strategy.** D-08 says successful replay is visible. What about successful generation with no strategy (D-03)? Should the bundle include `reports/replay.txt` with content like `"No saved assignment strategy; generated from project assignments."`? Or omit the file entirely? The plan should pick one and lock it with a test, otherwise behavior diverges from CLI (which always emits a Message).
- **[LOW] `ReplayReportText` privacy scrubbing is correctly added but tests must prove it.** The plan says "Include `ReplayReportText` in privacy scanning with existing `BundlePathScrubber.Scrub`/`FindPrivacyFindings`." Add a test where blocked NPC details are constructed in a way that includes a path-like string (synthetic — preset names don't normally include paths, but defensive) and assert the rendered `ReplayReportText` and bundle entry are scrubbed.

### Suggestions
- Switch `ReplayReportText` to a non-positional `init` property to avoid breaking every existing constructor call:
  ```csharp
  public sealed record PortableProjectBundlePreview(...)
  {
      public string ReplayReportText { get; init; } = string.Empty;
  }
  ```
- Spell out the replay/validation/missing-profile order explicitly in Task 2.
- Require `reports/replay.txt` to be added to the manifest entry list (and confirm with a test that loads the manifest and asserts the entry exists with a valid SHA-256).
- Decide and lock no-strategy bundle behavior: either emit `reports/replay.txt` with a non-replay message, or skip the file entirely.

### Risk: **MEDIUM**
The bundle plan is well-architected and addresses the prior dual-project critique cleanly, but the contract additions need to be lower-friction (init properties) and the manifest/replay-report relationship needs to be locked explicitly.

---

## Cross-Plan Concerns

### Dependency Graph
- ✅ Wave 1 (07-01) → Wave 2 (07-02, 07-03 both depend on 07-01) — correct.
- ⚠️ Plan 07-03 lists `depends_on: [07-01, 07-02]` and is `wave: 3`, but the roadmap and prior reviews treat 07-02 and 07-03 as parallelizable Wave 2. The frontmatter ordering forces serialization. If parallel execution is desired, drop the `07-02` dependency. If sequential is intentional (e.g., to build on 02's success-message format for bundle reuse), keep it but document why.

### Coverage Gaps
- **[HIGH] Plan 01 acceptance does not run GUI strategy tests.** See Plan 01 [HIGH] above.
- **[MEDIUM] No test that validation runs against the replay project.** Both Plan 02 and Plan 03 should include a fixture where the source project has stale references that validation would flag, but replay replaces them — proving validation operates on the post-replay state.
- **[LOW] No test for bundle preview without a strategy emitting consistent replay-report behavior** (see Plan 03 above).

### Scope and Performance
- ✅ Scope is tight; no new RNG, no new strategy kinds, no writer changes.
- Cloning `ProjectModel` per `Preview` and `Create` for projects with thousands of NPCs is acceptable for one-shot operations but worth a one-line comment near `cloneBeforeReplay: true`.

### Security
- Threat model is complete. The new concern — `ReplayReportText` carrying blocked-NPC strings — is correctly routed through `BundlePathScrubber`. Just make sure the routing is **tested**, not just specified.

---

## Overall Risk Assessment: **MEDIUM**

**Justification:** The architectural revisions are excellent and address most prior-round concerns. Plan 03 is now cleanest of the three. Risk is concentrated in two places:

1. **Plan 01's `MorphAssignmentService.ApplyStrategy` edit drops `eligibleRows` for seeded strategies** — a real GUI regression baked into the plan as written. Must be fixed before execution.
2. **Plan 02 leaves CLI with the dual-project shape Plan 03 correctly eliminated** — works today via clone fidelity but creates an asymmetric mental model and hides any future clone-drift bug.

Plus three smaller-but-load-bearing items: the `Preview`/`Result` record additions need to be init-properties to avoid breaking call sites, validation-against-which-project ordering needs to be explicit in both plans, and Plan 01's acceptance gate needs to include GUI strategy tests.

### Recommended pre-execution adjustments
1. **Plan 01 Task 2:** Branch on provider, not entry point, so `eligibleRows` is preserved across both seeded and unseeded paths. Add `eligibleRows` to acceptance criteria.
2. **Plan 01 acceptance:** Expand targeted gate to include `AssignmentStrategyServiceTests` and `MorphsViewModelStrategyTests`.
3. **Plan 02 Task 2:** Use `replayResult.Project` consistently for BodyGen and BoS generation (mirror Plan 03). Add BoS byte-parity test.
4. **Plans 02 & 03:** State explicitly that validation, missing-profile checks, and overwrite/PlanTargets run against `replayResult.Project` after a successful replay. Add a test where validation against the source would have blocked but against the replay project succeeds.
5. **Plan 03 Task 2:** Switch `ReplayReportText` to a non-positional `init` property on the record. Require the new `reports/replay.txt` zip entry to be registered in the manifest entry list. Decide and lock no-strategy bundle behavior.
6. **All plans:** Add a "no path leakage in blocked-NPC message" assertion to the blocked-replay tests.

With these tightened, risk drops to **LOW-MEDIUM**.

---

## Codex Review

## Overall Summary

The three plans are strong and mostly phase-complete: they preserve the right dependency order, keep replay in Core, reuse existing assignment services, and add tests that prove replay from saved strategy data rather than already-mutated assignments. The biggest risks are around shared-service behavior changes in `MorphAssignmentService`, validation/replay ordering, and Plan 03’s added bundle report surface, which may be more schema churn than necessary unless it is deliberately accepted.

## 07-01-PLAN.md

### Strengths

- Good first wave: creates the reusable Core seam before CLI and bundle wiring.
- Tests are aimed at the real bug: stale/wrong existing assignments with saved strategy data.
- Includes BoS-only non-replay and no-strategy no-op paths.
- Explicitly tests clone/no-mutation behavior, including `IsDirty` and `ChangeVersion`.
- Recognizes blocked replay can partially mutate the working project and treats the result as fatal for callers.
- Seeded deterministic replay is covered before integration paths inherit it.

### Concerns

- **HIGH:** The proposed `MorphAssignmentService.ApplyStrategy` change may ignore `eligibleRows` when `strategy.Seed.HasValue` if it delegates directly to `AssignmentStrategyService.Apply(project, strategy)`. That could regress GUI or scoped strategy behavior if seeded strategies are ever applied to a subset.
- **MEDIUM:** The plan changes a shared morph service in Wave 1. That is broader than a pure automation seam and should be protected by existing `AssignmentStrategyServiceTests` and `MorphsViewModelStrategyTests`, not only the new replay tests.
- **MEDIUM:** Replay result exposes raw fields but no shared formatter for summary/blocker messages. Plans 02 and 03 may duplicate message formatting and drift.
- **LOW:** `PrepareForBodyGen` name is slightly misleading because it also makes the BoS no-op decision. Acceptable, but the contract should state that BoS intent returns a no-op result.

### Suggestions

- Preserve `eligibleRows` for seeded strategies. Prefer constructing/using the deterministic provider through the same service path rather than switching to a static overload that may drop subset semantics.
- Add or run focused existing tests for seeded strategy with scoped eligible rows if that behavior exists.
- Consider a small Core formatter/helper on the replay result, such as `ToSummaryText()` and `FormatBlockedDetails()`, to reduce CLI/bundle message drift.
- Add XML docs that explicitly say blocked replay may leave the working project partially assigned and callers must not generate output when `IsBlocked`.

### Risk Assessment

**MEDIUM.** The seam is well-scoped, but modifying `MorphAssignmentService` has shared behavioral risk, especially around seeded strategies plus `eligibleRows`.

## 07-02-PLAN.md

### Strengths

- Correctly wires replay before BodyGen/all morph generation.
- Tests cover BodyGen, All, BoS-only, no-strategy, blocked replay, overwrite ordering, stdout/stderr, and seeded repeatability.
- Good no-write assertion for blocked `OutputIntent.All`; this is essential.
- Keeps replay automatic and avoids adding a flag.
- Constructor dependency is explicit and Core-only.

### Concerns

- **HIGH:** The action text says validation/missing-profile checks should run after replay and use replay state “for BodyGen validation.” If project validation includes checks unrelated to generated assignments, changing the validation subject from loaded source to replay clone may alter diagnostics unexpectedly. The phase needs replay blockers elevated, but not necessarily all validation semantics rewritten around replayed state.
- **MEDIUM:** “Use `replayResult.Project` for BodyGen `PlanTargets`/template/morph generation” is imprecise. `PlanTargets` should normally depend on request intent/output paths, not project mutation. If it currently depends on project name/profile, this is probably harmless, but the plan should avoid implying target planning needs replay state.
- **MEDIUM:** Successful replay followed by overwrite refusal means replay work happens even when output will not be written. That matches the planned gate-order test, but it may be surprising. If replay can be expensive for large NPC sets, overwrite preflight before replay would save work. The tradeoff should be intentional because D-06 only requires blocked replay before writes, not before overwrite checks.
- **LOW:** Requiring blocked messages to omit project/output paths is reasonable for privacy, but existing CLI error conventions may already include paths for actionable output errors. Keep the restriction scoped to replay messages.

### Suggestions

- Define the exact ordering as: load project, build request catalog, run existing validation/missing-profile checks as before where possible, run replay before target creation/writes, then overwrite/write gates. If replay blockers must appear before overwrite refusal, state that explicitly as a product choice.
- Use `replayResult.Project` only where generated output actually consumes assignments, especially `MorphGenerationService.GenerateMorphs`.
- Add one test proving existing validation-blocked behavior still wins or remains unchanged for non-replay validation failures.
- If success summaries are appended to `HeadlessGenerationResult.Message`, ensure existing success output tests allow both message and written paths in a stable order.

### Risk Assessment

**MEDIUM.** The CLI plan achieves the phase goal, but validation ordering and “which project state is validated” need tightening to avoid diagnostic regressions.

## 07-03-PLAN.md

### Strengths

- Correctly preserves `project/project.jbs2bg` from `request.Project`.
- Uses request-scoped replay state for generated output.
- Strong tests for project entry preservation, caller mutation, BoS-only behavior, blocked no-zip behavior, preview behavior, and CLI/bundle byte parity.
- Explicit constructor ripple list is practical and likely prevents broken composition.
- Adds a visible replay report surface instead of overloading privacy findings.
- Includes privacy scanning for replay report text.

### Concerns

- **HIGH:** The plan says `AddGeneratedOutputEntries` should use `replayResult.Project` consistently for BodyGen templates, BodyGen morphs, and BoS JSON. Replay mutates NPC assignments, which should not affect templates or BoS JSON, but using replay state for all outputs unnecessarily broadens the blast radius. It also conflicts slightly with D-01’s “BoS-only does not replay” spirit if All-mode BoS is generated from replayed state.
- **MEDIUM:** Adding `ReplayReportText` to both preview and result plus a new `reports/replay.txt` bundle entry is probably useful, but it is a new bundle contract surface. The phase required replay visibility in preview/report text, not necessarily a new artifact. This may be acceptable, but it should be treated as intentional product surface, not incidental implementation.
- **MEDIUM:** “Before validation/missing-profile checks that could be affected by stale assignments” is vague. Bundle validation likely covers profile/output readiness beyond assignment replay. As in Plan 02, avoid changing unrelated validation semantics unless necessary.
- **MEDIUM:** Updating `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` for fallback construction risks App churn. If DI can fully own this, prefer reducing ViewModel construction knowledge of Core services.
- **LOW:** A `reports/replay.txt` success entry may create a new privacy/reporting test burden. It should be scrubbed and included in manifest/checksum behavior intentionally.

### Suggestions

- Use separate variables: `sourceProject` for project serialization and non-assignment-sensitive outputs; `bodyGenProject` for morph output after replay. Only use replay state for `morphs.ini` unless code inspection proves another generated artifact depends on assignments.
- If `reports/replay.txt` is kept, document it as a stable bundle artifact and test manifest/checksum inclusion.
- Make blocked replay happen before zip creation and generated entries, but preserve existing validation and missing-profile behavior unless a test proves replay must precede it.
- Prefer DI composition updates in `AppBootstrapper`; only touch `MainWindowViewModel` fallback construction if it currently instantiates `PortableProjectBundleService` directly.

### Risk Assessment

**MEDIUM-HIGH.** The bundle plan covers the right requirements, but it has the most contract and blast-radius risk: new report fields, new bundle artifact, App constructor ripple, and use of replayed state for outputs beyond `morphs.ini`.

## Final Recommendations

- Keep the three-wave structure. The sequencing is sound.
- Tighten `MorphAssignmentService` seeded behavior so it preserves `eligibleRows` and the existing provider abstraction.
- Centralize replay summary/blocker formatting in Core to avoid CLI and bundle text drift.
- Be more conservative about validation ordering. Elevate replay blockers for BodyGen automation, but do not accidentally redefine broader project validation semantics.
- In Plan 03, generate only assignment-dependent output from replayed state unless there is a demonstrated reason to use the replay clone everywhere.
- Treat `ReplayReportText` and `reports/replay.txt` as explicit product/API decisions; otherwise, use existing report text surfaces to reduce contract churn.

Overall risk: **MEDIUM**. The plans do achieve the Phase 7 goal, and the tests are stronger than usual because they target stale-assignment false positives. The main remaining risk is over-broad integration: changing shared assignment behavior and using replayed project state in places that do not need replay.

---

## Consensus Summary

The reviewers agree that Phase 7 has the right architecture: a reusable Core replay seam, followed by CLI and portable bundle wiring, directly targets the stale-assignment automation gap without changing byte-sensitive writers or assignment strategy semantics. The primary feedback is not to change the phase shape, but to tighten the plan details around shared-service regressions, validation ordering, and the new reporting surfaces before execution.

### Agreed Strengths

- Centralizing replay in `AssignmentStrategyReplayService` is the correct abstraction to prevent CLI and bundle behavior drift.
- Stale-assignment fixtures are high-signal tests because they prove replay comes from saved strategy data instead of pre-mutated NPC assignments.
- Reusing existing assignment services and writer/generation paths preserves the random-provider abstraction and byte-sensitive output contracts.
- Request-scoped clone/state isolation is the right safety posture for portable bundle output and acceptable for CLI consistency.
- Treating blocked replay as fatal before output writes supports the phase's reproducibility and modder-trust goals.

### Agreed Concerns

- **HIGH: Seeded replay must preserve `eligibleRows`.** Claude and Codex both flagged that Plan 01's proposed seeded dispatch through `AssignmentStrategyService.Apply(project, strategy)` can drop scoped eligible-row semantics and regress GUI/scoped strategy behavior. Plan 01 should require an `eligibleRows`-preserving deterministic-provider path and run existing assignment/GUI strategy tests.
- **MEDIUM-HIGH: Validation/replay ordering needs exact wording.** Claude and Codex both want the plans to state precisely which project instance is validated and generated from after replay, so stale source assignments do not block legitimate replay while unrelated validation semantics are not accidentally broadened.
- **MEDIUM: Replay reporting surfaces must be explicit and stable.** Gemini likes the proposed `ReplayReportText`/`reports/replay.txt`; Claude and Codex both require treating it as a deliberate contract with manifest/checksum/privacy handling, not incidental text output.
- **MEDIUM: CLI and bundle output project selection needs a clear invariant.** Claude recommends using `replayResult.Project` consistently for generated outputs; Codex recommends using replay state only for assignment-dependent morph output. Both agree the plan must make the state split explicit and test byte parity/no drift.
- **MEDIUM: Constructor and shared-service changes need broader gates.** Bundle constructor ripple effects are enumerated, but Plan 01 also modifies shared morph behavior and should verify existing `AssignmentStrategyServiceTests` and `MorphsViewModelStrategyTests`, not only new replay tests.

### Divergent Views

- Gemini rates the revised plans as low risk and approved, while Claude and Codex rate overall risk medium because Plan 01 still contains a concrete `eligibleRows` regression risk and Plans 02/03 still need tighter ordering/reporting contracts.
- Claude prefers a single replay project for CLI/bundle generated outputs to avoid dual-project drift. Codex prefers a narrower blast radius where only assignment-dependent `morphs.ini` uses replay state. The planner should choose one invariant and add parity tests to enforce it.
- Claude recommends `ReplayReportText` as a non-positional init property plus manifest inclusion for `reports/replay.txt`; Codex questions whether a new bundle artifact is necessary. The common requirement is to pick an explicit product/API surface and test it.

### Recommended Plan Updates

- Update Plan 01 so seeded strategies construct/use the deterministic provider while preserving `eligibleRows`; add acceptance checks for `eligibleRows` and run `AssignmentStrategyServiceTests` plus `MorphsViewModelStrategyTests`.
- Add explicit replay-result docs/tests stating that blocked replay may partially mutate the working project and callers must not generate output when `IsBlocked` is true.
- Clarify in Plans 02 and 03 whether validation, missing-profile checks, target planning, and each generated output use the source project or `replayResult.Project`; add tests for stale source assignments that replay fixes.
- Decide whether CLI/bundle generated BoS/template outputs use replay state or source state, then add byte-parity tests for the chosen invariant.
- Treat `ReplayReportText` and/or `reports/replay.txt` as a stable contract: choose no-strategy behavior, include manifest/checksum/privacy handling if a zip entry is added, and avoid breaking positional record call sites where possible.
- Add no-path-leak assertions for replay-blocked CLI and bundle messages.

