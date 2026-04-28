---
phase: 5
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-27T18:22:33.0263701-07:00
plans_reviewed:
  - 05-01-PLAN.md
  - 05-02-PLAN.md
  - 05-03-PLAN.md
  - 05-04-PLAN.md
  - 05-05-PLAN.md
  - 05-06-PLAN.md
  - 05-07-PLAN.md
  - 05-08-PLAN.md
  - 05-09-PLAN.md
---

# Cross-AI Plan Review - Phase 5

## Gemini Review

# Phase 5 Review: Automation, Sharing, and Release Trust

## Summary
Phase 5 is a comprehensive and well-architected expansion of the BS2BG toolset, transitioning it from a GUI-only utility to a robust, automation-friendly platform. The plans demonstrate a sophisticated understanding of the project's core constraints—specifically **byte-identical output parity** and **local/offline trust**. By introducing a dedicated CLI, path-scrubbed portable bundles, and deterministic assignment strategies, the project significantly increases its value to power modders while maintaining the strict reliability required by its user base. The separation of concerns between `BS2BG.Core`, `BS2BG.Cli`, and `BS2BG.App` is maintained perfectly throughout the 9 plans.

---

## Strengths
*   **Architectural Integrity**: The decision to keep all generation, validation, and export logic in `BS2BG.Core` ensures that the CLI and GUI can never drift in behavior.
*   **Validation-First Design**: Plans 02 and 04 prioritize validation blockers, preventing invalid or risky writes in both automation and manual workflows.
*   **Privacy-Centric Bundling**: Plan 06's `BundlePathScrubber` is a standout feature, demonstrating a "security-by-design" approach to sharing support artifacts without leaking PII (Usernames) or local drive structures.
*   **Algorithm Determinism**: The formal specification of the weighted assignment algorithm (Plan 04), including the use of fixed two-decimal weight units and stable ordering, is excellent for ensuring cross-machine reproducibility.
*   **Release Trust Evolution**: Plan 08 pragmatically handles the absence of a signing certificate by strengthening checksum verification while still providing an "opt-in" path for future signing.
*   **Undo/Redo Coverage**: Plan 05 correctly extends the undo system to capture both NPC assignments *and* the strategy configuration that produced them, preventing state-mismatch bugs.

---

## Concerns

### 1. Temporary Directory Staging and Cleanup (Plan 06)
*   **Severity: LOW**
*   **Detail**: Plan 06 mentions writing outputs to a "temp directory" for bundling. While this ensures parity, the plan doesn't explicitly mention the cleanup strategy for these temp files in the event of an I/O crash or process termination.
*   **Impact**: Potential disk clutter in `AppData/Local/Temp` or equivalent.

### 2. Race Filter String Complexity (Plan 04/05)
*   **Severity: LOW**
*   **Detail**: Strategy filters match against the imported `Npc.Race` text. In some NPC dumps (e.g., from certain xEdit versions), race strings can include suffixes or formatting variations. 
*   **Impact**: Users might experience "No eligible preset" errors if they aren't aware that "NordRace" and "Nord" might differ in their specific data export. (Mitigated by D-14's text-only matching policy, but worth noting for documentation).

### 3. ZIP Path Normalization (Plan 06)
*   **Severity: LOW**
*   **Detail**: Plan 06 requires replacing backslashes with forward slashes for ZIP entries. While standard for cross-platform ZIP compatibility, it's critical to ensure this happens *before* SHA-256 calculation if the manifest entries are expected to match external verification tools.
*   **Impact**: Inconsistent checksums if tools differ in path separators.

---

## Suggestions
*   **CLI Exit Code 5**: Consider adding a specific exit code for "Partially Succeeded" (e.g., 5) for the `--intent all` case where BodyGen succeeds but BoS fails (as discussed in Plan 02, Task 1, Test 6).
*   **Zip Determinism Flag**: While not a requirement for Phase 5, adding a `FixedTimestamp` option to `PortableProjectBundleService` in the future would allow for byte-identical ZIP hashes across different machines, which is the ultimate level of sharing trust.
*   **Strategy Import/Export**: In a future milestone, consider allowing users to export the `AssignmentStrategyDefinition` as a standalone JSON file to share rules without sharing the whole project.

---

## Risk Assessment
**Overall Risk: LOW**

### Justification:
The plans are exceptionally detailed, TDD-driven, and strictly follow the established architecture. 
*   **Technical Risk**: Low. Reuses proven `netstandard2.1` and `net10.0` BCL libraries. Reuses existing Core writers for all file I/O.
*   **Functional Risk**: Low. Every new feature is backed by "success criteria" and "must-have truths" that align with the user's PRD.
*   **Security Risk**: Low/Mitigated. Path scrubbing and overwrite refusal are baked into the core logic.
*   **Parity Risk**: Low. No new formatters or sliders math logic is introduced; the CLI and Bundles simply orchestrate existing services.

The sequence of waves correctly handles dependencies (e.g., the CLI foundation exists before the generation service is wired to it). The inclusion of visual checkpoints for UI work ensures the human-in-the-loop remains satisfied with the UX placement.

---

**Review Status: APPROVED FOR EXECUTION**


---

## the agent Review

# Phase 5 Cross-AI Plan Review

## Overall Summary

The Phase 5 plan set is unusually well-constructed: locked decisions (D-01 through D-20) trace cleanly into nine plans with explicit dependency waves, requirement coverage is complete (AUTO-01–05 + ADV-03), and the planner has internalized critical project constraints (sacred files, byte parity, no second writer paths, ReactiveUI conventions). The strongest plans (05-03, 05-04, 05-06) replan in response to obvious review pressure — embedding formal algorithm specs, shared-eligibility surfaces, and writer-byte-equality assertions directly into the task contracts. The weakest seams are around (1) deterministic PRNG portability across .NET runtimes, (2) sacred-file modification in 05-02 disguised as "extracting a planner from BosJsonExportWriter," and (3) test-time coupling between unit tests and PowerShell publish/zip workflows in 05-08/09. Risk is **MEDIUM overall** — every plan is buildable, but two of them have subtle correctness/sacredness traps that will reappear at integration.

---

## 05-01: CLI Foundation

**Strengths**
- Pins `System.CommandLine 2.0.7` stable explicitly per research.
- Tests solution registration via `dotnet sln list`, not `File.Exists` — catches a class of mistakes immediately.
- Forbids Avalonia/App references in CLI csproj.
- Copies bundled profile JSON to CLI build output, anticipating Plan 02's catalog composition.

**Concerns**
- **MEDIUM** — Task 1 mixes two responsibilities (project scaffolding + parser contract + asset copy). If the asset-copy `Content` items are wrong, this becomes the failure mode that blocks 05-02 catalog discovery. Worth its own micro-task with a concrete test that runs the built CLI from `bin/Debug/net10.0/` and proves the JSON files sit beside the exe.
- **LOW** — `OutputType=Exe` will produce `BS2BG.Cli.dll` + a launcher; on Windows publish it becomes `BS2BG.Cli.exe`. Plan 05-08 asserts `BS2BG.Cli.exe` exists in the package. Worth noting that this only holds after `dotnet publish -r win-x64`, not after `dotnet build`. Tests in 05-01 should not check for a `.exe`.

**Suggestions**
- Split Task 1 into 1a (csproj + sln entry) and 1b (parser surface + asset copy), each with its own acceptance line.
- Add an explicit acceptance criterion: `bin/Debug/net10.0/settings.json` exists post-build. The current acceptance line only checks the csproj text contains the Content items.

**Risk: LOW**

---

## 05-02: Validation-First Headless Generation

**Strengths**
- Validation-first gate is wired before any writer call, with explicit exit-code mapping (0/1/2/3/4) inherited from research.
- Recognizes the partial-failure case for `--intent all` and demands an outcome ledger consistent with Phase 3.
- In-process `Program.Main` invocation with captured stdout/stderr is the right test shape — avoids `dotnet run` flakiness.
- Catalog factory move into Core is the right architectural call.

**Concerns**
- **HIGH** — The plan instructs the executor to *modify* `BosJsonExportWriter` to "extract or expose a writer-owned `ExportOutputPlan`/dry-run method." `BosJsonExportWriter.cs` is on the **sacred files list** in CLAUDE.md and PROJECT.md. The plan acknowledges "preserve all existing golden output behavior" but does not explicitly require running the existing BoS golden-file suite as a gate, nor does it require an explicit user-facing flag that a sacred file is being touched. The deviation rules will likely (correctly) escalate this as Rule 4 (architectural change requiring approval) when execution starts.
- **MEDIUM** — Task 1 demands the service handle "corrupt JSON/project load failures, missing profile assets, ArgumentException, IOException, UnauthorizedAccessException, and path errors and map them to UsageError (1) for invalid input/config or IoFailure (4) for filesystem failures." That mapping is ambiguous: a malformed `.jbs2bg` is "invalid input" by some readings (UsageError) but "I/O failure" by others. Without a concrete table, behavior will drift between the implementer's reading and the tests.
- **MEDIUM** — Partial failure for `--intent all` says "do not silently roll back BodyGen unless an existing writer API already supports rollback." Existing writers use `AtomicFileWriter.WriteAtomicBatch`, which is per-batch, not cross-batch. So if BodyGen writes succeed and BoS writes then fail, the user is left with an inconsistent on-disk state. The plan accepts this but only via the ledger; the ledger is reported via stdout, which scripted callers may not parse. Worth elevating to a "report and return 4" with explicit guidance that BodyGen artifacts remain present.
- **LOW** — `--omit-redundant-sliders` is added in Task 2 without a test for behavior parity vs the GUI preference (`UserPreferences.OmitRedundantSliders`). One-line test would help.

**Suggestions**
- Replace the "extract from `BosJsonExportWriter`" guidance with: "Add a *new* `BosJsonExportPlanner` class in the same namespace that takes the same preset list + catalog and returns the same sanitized filenames. `BosJsonExportWriter` consumes the planner internally. Treat the writer's existing logic as inviolate — refactoring is allowed only if the existing golden suite passes byte-identical." This avoids the sacred-file flag while still removing duplication.
- Add an exit-code mapping table directly to the plan (input file missing → 1, project JSON malformed → 1, output dir unwritable → 4, etc.).
- Add a test asserting BodyGen artifacts remain on disk and are listed in the ledger when BoS writes fail mid-batch under `--intent all`.

**Risk: MEDIUM-HIGH** (sacred-file modification is the real risk; everything else is fixable in execution)

---

## 05-03: Strategy Persistence

**Strengths**
- Uses nullable `AssignmentStrategyDefinition?` on `ProjectModel` — preserves byte-identical legacy save when null, matching the Phase 4 `CustomProfiles` pattern exactly.
- Validation rules at the persistence boundary (negative/NaN/Infinity weights, duplicate rule names, empty required collections) push errors as far left as possible.
- Inspects existing DTO ordering before assigning `JsonPropertyOrder` — avoids the obvious copy-paste collision with `CustomProfiles`.
- Race matching pinned to `OrdinalIgnoreCase` everywhere.

**Concerns**
- **MEDIUM** — `AssignmentStrategyRule` has a single field set used differently per strategy kind (e.g., `Weight` is meaningless for RoundRobin, `BucketName` is meaningless for Weighted). Flat-with-validation works but blurs intent and makes 05-04's algorithm code branch heavily on `Kind`. A discriminated approach (per-kind rule subtypes or one rule type per strategy collection) would localize validation and keep weighted/bucket invariants closer to their use sites. Not blocking; flagging because the cost compounds in 05-04 and 05-05.
- **LOW** — Tests should round-trip a project through file write → read → re-serialize and assert byte-identical JSON when no strategy is set, to lock the legacy-compat claim.

**Suggestions**
- Consider sealing each rule shape (`SeededRandomConfig`, `RoundRobinConfig`, `WeightedRule[]`, `RaceFilterRule[]`, `GroupBucketRule[]`) as alternatives in `AssignmentStrategyDefinition`, with `Kind` as the discriminator. This is the data shape that a 1.0 protocol wants to live with.
- Add an explicit "legacy round-trip is byte-identical" test, not just "loads cleanly."

**Risk: LOW**

---

## 05-04: Deterministic Strategy Execution

**Strengths**
- Formal algorithm spec embedded in the `<interfaces>` block — weighted-unit conversion, ordering tie-breakers, bucket-membership rules — removes a huge class of "what does this mean?" deviations.
- `ComputeEligibility` as the single non-mutating eligibility surface used by both `Apply` and `ProjectValidationService` is exactly right.
- Refuses silent fallback (D-15) and exact-sequence assertions (not just count) catch the subtle drift.
- Hides the PRNG behind `IRandomAssignmentProvider` so the executor stays testable.

**Concerns**
- **HIGH** — `SeededRandomAssignmentProvider` is specified as `private readonly Random random = new(seed)`. **`System.Random` is not portable across .NET versions or runtimes.** Microsoft has changed the LCG implementation between framework releases; using it for "the same seed produces the same assignments on a collaborator's machine" (D-11) is a latent correctness bug. A user on .NET 8 SDK runtime and a user on .NET 10 SDK runtime can produce different assignments from the same seed and same project. Since `BS2BG.Core` is `netstandard2.1`, the underlying runtime is whatever the consumer uses — explicitly cross-runtime.
- **MEDIUM** — Weighted "fixed two-decimal weight-unit cumulative draw" with `Math.Round(weight * 100, MidpointRounding.AwayFromZero)`: if a user enters `Weight = 0.005`, this rounds to 1 unit, not 0. That's defensible but undocumented; users may expect "weight below 0.01 means zero." Spec it.
- **MEDIUM** — `ComputeEligibility` signature takes `IReadOnlyList<Npc> eligibleRows`, but the diagnostics call from `ProjectValidationService` (Task 3) doesn't have an obvious "scope" — what does it pass? `project.MorphedNpcs`? If so, the diagnostic flags blockers for *all* NPCs even when the GUI is about to apply only a subset. The plan partially addresses this in 05-05 by saying GUI passes all rows, but the validation-from-CLI path is implicit.
- **LOW** — Round-robin "in stable preset collection order" is fine, but rotation start position is unspecified. Always start at index 0 across runs? Resume from last-assigned-position? Spec it (probably "always start at 0" for full determinism).

**Suggestions**
- Replace `System.Random` with a deterministic algorithm pinned in the codebase: PCG, Xoshiro256**, or Mulberry32. ~20 lines, fully portable, and unit-testable against published reference vectors. Keep `IRandomAssignmentProvider` as the seam.
- Add explicit acceptance: "Running the same seed test on .NET 8.0 and .NET 10.0 SDK runtimes produces identical sequences." This catches `System.Random` drift in CI.
- Document round-robin start position and weighted-unit minimum threshold in `AssignmentStrategyContracts.cs` XML doc comments.

**Risk: MEDIUM-HIGH** (the `System.Random` issue undermines D-11 silently)

---

## 05-05: Strategy UI

**Strengths**
- Apply scope is the **full** `MorphedNpcs`, not the visible filter — exactly right for cross-machine determinism, and the plan calls this out explicitly with a count display.
- Undo restores both assignments *and* the prior `AssignmentStrategy` configuration. This is the subtle bit most planners miss.
- Forbids icon-only controls, enforces `AutomationProperties.Name` and `x:DataType`, matches UI-SPEC copy verbatim.
- Visual checkpoint is appropriately placed and gated.

**Concerns**
- **MEDIUM** — Comma-separated text fields for `PresetNames` and `RaceFilters` are a regression from typical Avalonia patterns (multi-select listbox, chip input). Fine for v1, but the plan should flag that Phase 5 ships "string-typed token input" as a deliberate choice and revisit in v2; otherwise this becomes a permanent cost.
- **MEDIUM** — Validation strategy mixes runtime (Apply disabled when invalid) with persistence (Plan 03 rejects malformed JSON on load). What happens if a user opens a project saved on a different schema version that has a now-rejected combination? The dirty/dirty-after-load semantics aren't pinned. Probably: load surfaces a `ProjectLoadDiagnostic`, GUI shows it, Apply stays disabled until repaired. Worth stating.
- **LOW** — Test 5 conflates three failure modes ("unknown preset, duplicate bucket, invalid weights, empty required") into one test. Split into four.

**Suggestions**
- Add an XML doc comment on `MorphsViewModel` describing why strategy apply ignores the visible filter (the user *will* be confused otherwise; users have come to expect filter-respecting bulk actions from Phase 2).
- Add explicit error UX for "project loaded but strategy is invalid" — a banner in the strategy panel pointing at the diagnostic.

**Risk: LOW-MEDIUM**

---

## 05-06: Portable Bundle Service

**Strengths**
- Generates BodyGen/BoS outputs into a temp directory using *real* writers and zips those exact bytes. This is the only correct way to preserve byte parity, and the plan calls it out as an explicit anti-pattern to avoid duplication.
- Central `BundlePathScrubber` for path-leak detection with documented false-positive trade-off.
- Manifest schema is fixed (`schemaVersion: 1`, `createdUtc`, `bundleSourceProjectName`, kinds enumerated) — testable.
- Missing-referenced-profile is a *blocker*, not silent omission. Right call.
- Uses `SHA256.Create().ComputeHash` (netstandard2.1-compatible) instead of the .NET 5+ `SHA256.HashData`.

**Concerns**
- **HIGH** — `createdUtc: DateTimeOffset` in the manifest makes `Preview()` *non-deterministic* by definition. Test 5 says "Preview result exposes ... manifest JSON ... without writing a zip" — but that manifest will differ on every preview call, so any test asserting manifest content beyond field presence will be flaky or require time injection. The fix: inject `IBundleClock` (or accept `DateTimeOffset createdUtc` on the request), and have tests pass a fixed value.
- **MEDIUM** — "Reject duplicate normalized entry names" is correct, but the reverse case isn't tested: if `BosJsonExportWriter` produces `Body (2).json` due to its own dedup logic, the bundle's `outputs/bos/` will contain it. Ensure tests cover the writer's dedup name flowing through unchanged.
- **MEDIUM** — `ProfileDefinitionService.ExportProfileJson` is referenced as the profile-copy mechanism. If that API doesn't exist with that exact signature (the Phase 4 archive references it but the actual API may be `Save`), the executor will deviate. Worth Read-First-ing the actual service surface in the read_first list.
- **LOW** — Temp directory cleanup on partial failure isn't specified. If `Create()` throws after staging BodyGen but before zipping, the temp directory leaks. Use `try/finally` with `Directory.Delete(tempDir, recursive: true)`.

**Suggestions**
- Inject `Func<DateTimeOffset>` or `IBundleClock` into `PortableProjectBundleService`. Default to `DateTimeOffset.UtcNow`. Tests use a fixed clock.
- Read-First should explicitly include the actual profile-export service API surface.
- Add `try/finally` cleanup test.

**Risk: MEDIUM** (the clock issue is ergonomically annoying but easy to fix)

---

## 05-07: CLI/GUI Bundle Wiring

**Strengths**
- Reuses `Preview` / `Create` from 05-06 cleanly — no duplicate planning logic.
- `AssignmentStrategy` is implicitly carried because it lives on `ProjectModel`, no extra plumbing needed.
- Visual checkpoint catches privacy-status text visibility, which is the easiest thing to miss.

**Concerns**
- **MEDIUM** — `IFileDialogService` is mentioned as "extend if needed for zip save path" but the extension is undefined. If `PickSaveBundleFileAsync` doesn't exist, the executor will create it with Phase 5 conventions but the spec doesn't show its signature. Worth one task line.
- **LOW** — CLI `bundle` command exit codes aren't enumerated as cleanly as `generate` was in 05-02. The plan implies 0/2/3/4 reuse but doesn't bind the existing `HeadlessGenerationExitCode` to bundle outcomes. Either reuse the enum or add a sibling enum.

**Suggestions**
- Add an explicit `IFileDialogService.PickSaveBundleFileAsync(CancellationToken)` signature to the plan if it's being added.
- Reuse `HeadlessGenerationExitCode` for `bundle`, or rename it to something neutral (`AutomationExitCode`).

**Risk: LOW**

---

## 05-08: Release Trust Packaging

**Strengths**
- Treats SignTool as optional (D-17), assertions correctly skip when absent.
- `SIGNING-INFO.txt` redaction is explicit — passwords never logged, certificate paths reduced to filenames.
- Adds CLI to the package, including runtime/profile assets.

**Concerns**
- **HIGH** — Test 1 says "Release script/package smoke packages README.md, ... BS2BG.Cli.exe ..." If this test actually runs `tools/release/package-release.ps1`, that requires `dotnet publish -r win-x64 --self-contained true` for two projects. That's slow (60–120s+) and the test framework is xUnit — running PowerShell publish in a unit test is heavyweight. The plan says "prefer extending existing `ReleasePackagingScriptTests` if they already run the script quickly," but it doesn't say what to do if they don't. Worth elevating to a category-tagged test that only runs in CI/release mode, not on every `dotnet test`.
- **MEDIUM** — "Tests can inspect a zip and fail on absolute paths or backslash entry names." The actual `Compress-Archive` PowerShell cmdlet is known to use backslashes on Windows in some scenarios. The test will likely fail on a clean run unless `package-release.ps1` is updated to convert separators, which the plan doesn't explicitly require. Verify the existing zip output before writing the assertion.
- **MEDIUM** — `BS2BG.Cli.csproj` needs `PublishSingleFile=true` for `BS2BG.Cli.exe` to actually exist after publish (current `BS2BG.App.csproj` uses it). The plan says "publish/package both BS2BG.App and BS2BG.Cli artifacts" but doesn't pin the publish properties for CLI, and `BS2BG.Cli.csproj` (created in 05-01) doesn't currently have them.
- **LOW** — `[switch]$SkipSigning` next to `[string]$CertificateSubject` is awkward — three signing-disable signals (no cert, SkipSigning, no SignTool) can interact. Simplify: signing is enabled only if `$CertificateSubject` or `$CertificatePath` is set *and* SignTool is on PATH; everything else is unsigned with checksum sidecars.

**Suggestions**
- Tag the package smoke test as `[Trait("Category", "ReleaseSmoke")]` and exclude from default `dotnet test` runs.
- Verify `Compress-Archive` separator behavior on the current Windows PowerShell version *before* writing the backslash assertion. Add a `package-release.ps1` post-step that re-zips with `[System.IO.Compression.ZipFile]::CreateFromDirectory` with explicit forward-slash entry names if needed.
- Pin CLI publish properties (`PublishSingleFile`, `IncludeNativeLibrariesForSelfExtract`, `EnableCompressionInSingleFile`) in this plan or 05-01.
- Drop `[switch]$SkipSigning` — let the absence of a cert subject/path mean unsigned.

**Risk: MEDIUM-HIGH** (test infrastructure is the risk; the script changes themselves are routine)

---

## 05-09: Setup/Troubleshooting Docs

**Strengths**
- Anchored UI-negative-check pattern (`MenuItem Header contains Setup` etc.) instead of free-text grep — won't false-positive on the word "setup" in unrelated copy.
- Exact boundary sentence is testable.

**Concerns**
- **LOW** — Test 2 says "package smoke from ReleaseTrust can inspect it." This couples 05-09's tests to 05-08's smoke harness. Fine if 05-08 is merged first (it is — 05-08 is wave 2, 05-09 is wave 3), but the dependency should be explicit in `depends_on`.
- **LOW** — The doc itself isn't versioned. If BodyGen/BodySlide tooling changes, the packaged setup guide goes stale silently. Trivial to add `Last verified: 2026-MM-DD` at the top, and a README note pointing to the source-of-truth in repo.

**Suggestions**
- Add `depends_on: [05-08]` (currently `[05-08]` — actually it does, my read was wrong).
- Add a "Last verified" date line; QA-CHECKLIST entry to re-verify before each release.

**Risk: LOW**

---

## Cross-Plan Concerns

**Dependency wave correctness**

- Wave 1: 05-01, 05-03 — independent ✓
- Wave 2: 05-02 (depends 05-01), 05-04 (depends 05-03), 05-08 (depends 05-01) ✓
- Wave 3: 05-05 (depends 05-04), 05-06 (depends 05-02 + 05-04), 05-09 (depends 05-08) ✓
- Wave 4: 05-07 (depends 05-05 + 05-06) ✓

Waves are well-formed, but **05-08 in Wave 2 depending only on 05-01 means it asserts `BS2BG.Cli.exe` ships with working CLI before 05-02 implements the generate command.** This is fine if the test only checks `.exe` exists; not fine if it tries to run `BS2BG.Cli.exe generate ...` as a smoke test. Verify 05-08 tests are exe-existence only, not exe-behavior.

**System.Random portability (cross-cutting)**

The `System.Random` issue in 05-04 is the single highest-impact technical concern in the set. D-11 is a hard requirement: "the same project, eligible rows, preset set/order, strategy configuration, and seed produce the same assignments." `System.Random(seed)` does not satisfy this across runtimes. Recommend pinning a deterministic PRNG (Mulberry32 or PCG) and asserting reference vectors. ~30 lines of code, eliminates a permanent latent bug.

**Sacred file modifications**

05-02 instructs modifying `BosJsonExportWriter`. CLAUDE.md and `gsd-executor` deviation rules will (correctly) escalate this as Rule 4. The cleaner path — adding a new sibling `BosJsonExportPlanner` class — gets the same architectural benefit without flagging.

**Test infrastructure heaviness**

05-08 and 05-09 want `dotnet test` to inspect a generated release zip. That's a 60–120s round trip per CI run. Recommend `[Trait("Category", "ReleaseSmoke")]` and explicit opt-in.

---

## Overall Risk Assessment: **MEDIUM**

**Justification:** The plan set is well-engineered overall, but contains three issues that will cause real friction or correctness defects if not pre-empted:

1. **HIGH** — `System.Random` for deterministic assignment violates D-11 silently across runtimes (05-04).
2. **HIGH** — Sacred file modification in 05-02 will be flagged at execution and likely block until escalated; the workaround is trivial.
3. **MEDIUM** — Release-test infrastructure in 05-08/09 needs category tagging or it will slow every developer's `dotnet test` cycle.

Items 1 and 2 are 30-minute fixes if addressed in the plans before execution starts; item 3 is a 5-minute fix. None of these are deal-breakers, but all three are predictable and worth catching now rather than during execution.

The plans correctly identify and lock the genuinely hard problems (byte-parity through writer reuse, path scrubbing, deterministic eligibility, undo restoring strategy config, reactive UI conventions) — execution risk is concentrated in the items above and is recoverable.


---

## Codex Review

## Summary

Overall, the Phase 5 plan set is strong: it preserves the Core/App boundary, avoids alternate generation/export paths, has clear dependency waves, and repeatedly tests the byte-sensitive and privacy-sensitive contracts. The main risks are scope density, a few dependency/order mismatches, and some places where “validation blockers” may become too broad and accidentally block unrelated workflows.

## Strengths

- Strong alignment with locked decisions D-01 through D-20.
- Good separation: CLI parses, Core owns semantics, App only edits/presents.
- Explicit protection against output-writer drift, profile leakage, path leakage, and SignTool hard dependency.
- Good use of TDD and exact-sequence strategy tests.
- Human checkpoints are correctly placed for the two UI-heavy plans.
- Release trust remains checksum-backed even when signing is unavailable.

## Concerns

### 05-01 CLI Foundation

- **MEDIUM:** `HeadlessGenerationRequest` omits `--omit-redundant-sliders`, but 05-02 adds it. That creates immediate contract churn.
- **MEDIUM:** In-process CLI tests using `Console.SetOut/SetError` can race under parallel xUnit execution.
- **LOW:** Profile asset copy tests are good, but the plan should explicitly verify copy-to-publish, not only copy-to-build.

### 05-02 Headless Generation

- **HIGH:** Adding `ExportOutputPlan` through `BosJsonExportWriter` touches a sacred output component. The plan is cautious, but this should be treated as a contract-only addition with golden/export regression coverage.
- **HIGH:** “Validation Blocker prevents writes” is correct, but later strategy diagnostics may introduce blockers unrelated to plain export readiness.
- **MEDIUM:** Overwrite preflight has a normal TOCTOU race. Existing atomic writers should still be the final authority.
- **MEDIUM:** Partial success for `--intent all` needs a very explicit outcome contract. Reporting BodyGen success plus BoS failure is acceptable, but users must not infer an all-or-nothing transaction.
- **LOW:** Mapping corrupt project JSON to `UsageError` vs `IoFailure` should be deterministic and documented.

### 05-03 Strategy Persistence

- **HIGH:** `AssignmentStrategyKind` makes weighted, race filters, and groups/buckets mutually exclusive. The Phase 5 scope sounds like these may need to compose. Clarify whether “weighted race-filtered bucket rules” are valid before locking the schema.
- **MEDIUM:** Rejecting invalid strategy JSON with `JsonException` may make shared projects hard to recover. Consider loading with invalid strategy diagnostics instead of failing the whole project where possible.
- **MEDIUM:** Dirty tracking and clone/replace coverage is correctly called out, but strategy rule collections need child-change tracking too if they are mutable in the App.
- **LOW:** Duplicate rule-name validation should define whether unnamed/default rules are allowed.

### 05-04 Strategy Execution

- **HIGH:** Adding no-eligible strategy gaps as `DiagnosticSeverity.Blocker` can block CLI generation even when the user is only exporting already-assigned morphs and not applying a strategy. That may overreach D-15.
- **HIGH:** Deterministic seeded random must not rely on `System.Random` if cross-runtime replay is required. The formal text notes this, but acceptance criteria should forbid `new Random(seed)` in the provider.
- **MEDIUM:** Default eligible NPC ordering should include original index as a final tie-breaker to avoid instability with duplicate NPC identity fields.
- **MEDIUM:** Validation and apply share `ComputeEligibility`, which is good, but diagnostics should include enough rule context to fix the gap.

### 05-05 Strategy UI

- **HIGH:** This is a large ViewModel/UI plan. Putting all parsing, validation, editable rows, command logic, undo snapshots, and status text into `MorphsViewModel` risks a very large class.
- **HIGH:** The objective says “undoable assignment strategy configuration,” but Task 1 only clearly makes Apply undoable. Editing the saved configuration itself should also be undoable or the wording should be narrowed.
- **MEDIUM:** AXAML compiled-binding correctness should be tested by at least a load/build/headless surface, not only ViewModel tests.
- **MEDIUM:** Full-project apply is the right determinism choice, but it conflicts with earlier visible/selected bulk-operation mental models. The UI copy must be very hard to miss.

### 05-06 Bundle Service

- **HIGH:** `PortableProjectBundleRequest(ProjectModel Project, string BundlePath, ...)` lacks a source project filename/path, but the manifest requires `bundleSourceProjectName`. Add an explicit source filename field.
- **HIGH:** `ProjectFileService.SaveToString` may not exist. If the service needs a new API, call that out as part of the plan.
- **MEDIUM:** Missing referenced custom profile handling needs to cover embedded project profiles as valid sources, not only local catalog files.
- **MEDIUM:** Temp-directory staging needs cleanup in `finally`, with a comment for any intentionally swallowed cleanup failure.
- **LOW:** `createdUtc` makes manifest output nondeterministic. That is fine, but tests should not overcompare it.

### 05-07 Bundle CLI/GUI Wiring

- **MEDIUM:** This combines CLI command wiring, App preview UI, file dialogs, AppBootstrapper changes, and tests. It may be safer as two plans.
- **MEDIUM:** `tests/PortableBundleServiceTests.cs` is an odd home for CLI and GUI wiring tests. Use separate CLI and ViewModel test files for clearer failures.
- **MEDIUM:** GUI bundle creation needs a clear behavior for unsaved projects, dirty projects, and source project filename.
- **LOW:** Preview privacy status should be generated from the Core preview result, not recomputed in App text.

### 05-08 Release Trust

- **HIGH:** This depends only on 05-01, but it packages/distributes `BS2BG.Cli.exe`. It should depend on 05-02 so the distributed CLI is functional, not just present.
- **MEDIUM:** Running full release packaging inside unit tests can be slow and brittle. Prefer a script smoke mode or narrowly scoped package fixture.
- **MEDIUM:** Package smoke should also execute extracted `BS2BG.Cli.exe --help` if feasible.
- **LOW:** Signing metadata redaction is well covered; also ensure PowerShell errors do not echo secret-containing command lines.

### 05-09 Release Docs

- **MEDIUM:** Setup docs can easily become too prescriptive for MO2/Vortex/manual layouts. Keep paths generic and emphasize verification.
- **LOW:** Negative UI scans are acceptable if anchored, but avoid failing on legitimate future Help text unrelated to this packaged guide.
- **LOW:** Add package zip assertion here or share a helper from 05-08 so docs inclusion is tested against the actual artifact.

## Suggestions

- Add `OmitRedundantSliders` to `HeadlessGenerationRequest` in 05-01 if 05-02 needs it.
- Introduce a fixed deterministic PRNG type and explicitly ban `System.Random` for persisted seed replay.
- Split strategy UI into small row/editor ViewModels instead of expanding `MorphsViewModel` directly.
- Clarify whether strategy rules are composable. If yes, prefer one rule model with optional filters/weights/bucket fields over mutually exclusive strategy kinds.
- Separate “strategy apply blockers” from “export readiness blockers” so an invalid unused strategy does not prevent normal export.
- Add `SourceProjectFileName` or `SourceProjectPath` to bundle requests.
- Make 05-08 depend on 05-02.
- Add a test synchronization helper or collection fixture for in-process CLI tests that mutate `Console.Out/Error`.
- Add extracted-package smoke checks for both `BS2BG.App.exe` presence and `BS2BG.Cli.exe --help`.

## Risk Assessment

**Overall risk: MEDIUM-HIGH.** The architecture is sound and the plans mostly achieve AUTO-01 through AUTO-05, but the phase combines new executable packaging, deterministic algorithms, project schema changes, UI editing, zip privacy controls, and release trust automation. The highest-risk areas are strategy schema/execution semantics, accidental validation overblocking, and release/package tests becoming slow or environment-sensitive. Tightening those contracts before execution would bring the risk down to medium.




---

## Consensus Summary

### Agreed Strengths

- All reviewers agreed the Phase 5 plan set is directionally sound, detailed, and aligned with AUTO-01 through AUTO-05 plus the Phase 5 context decisions.
- All reviewers highlighted the Core-first architecture as a major strength: CLI and GUI surfaces orchestrate existing Core services instead of introducing alternate generation or export paths.
- All reviewers recognized validation-first automation, explicit output intent, and overwrite refusal as important trust controls for headless and bundle workflows.
- All reviewers called out the portable bundle privacy posture as strong, especially relative paths, path scrubbing, referenced-profile scoping, and ZIP/manifest tests.
- All reviewers viewed optional signing plus SHA-256 verification as the right release-trust compromise for a Windows-first portable tool.

### Agreed Concerns

- **HIGH: Deterministic seeded assignment must not rely on runtime-dependent randomness.** the agent and Codex explicitly flagged `System.Random` as unsafe for cross-runtime replay, while Gemini praised the deterministic strategy intent without addressing runtime portability. The plan should explicitly require a pinned deterministic PRNG/reference-vector test and forbid `new Random(seed)` for persisted seed replay.
- **HIGH: Sacred output-writer changes in 05-02 need tighter guardrails.** the agent and Codex both warned that exposing BoS filename planning through `BosJsonExportWriter` touches a sacred, byte-sensitive component. Prefer a writer-owned/sibling planner with golden/export regression gates and explicit sacred-file caution before execution.
- **MEDIUM-HIGH: Release packaging tests need scoping so trust checks do not make ordinary test runs slow or brittle.** the agent and Codex both warned that package smoke tests can be heavyweight if they publish/package real win-x64 artifacts under normal `dotnet test`. Add category gating, a focused smoke mode, or a shared fixture strategy.
- **MEDIUM: Strategy validation and apply semantics need to avoid overblocking unrelated exports.** Codex warned that no-eligible strategy blockers could block plain export of existing assignments; the agent noted diagnostics scope must be explicit. The planner should distinguish apply-time strategy blockers from export-readiness blockers or document why saved invalid strategies block headless generation.
- **MEDIUM: Bundle service contracts need small hardening details.** Gemini and Codex both requested temp-directory cleanup; the agent flagged time injection for `createdUtc`; Codex flagged an explicit source project filename/path and potential `SaveToString` API mismatch. These are low-cost plan edits before execution.
- **MEDIUM: Strategy UI is large and needs careful decomposition/testing.** the agent and Codex both noted token input, ViewModel size, undo/persistence, and visible full-project-apply copy as execution risks. Splitting editor row state into smaller ViewModels and adding compiled-binding/headless checks would reduce risk.

### Divergent Views

- **Overall risk level differed.** Gemini rated the plans LOW risk and approved execution, the agent rated them MEDIUM, and Codex rated them MEDIUM-HIGH. The difference comes from Gemini emphasizing architecture and coverage, while the agent and Codex focused on implementation traps in deterministic randomness, sacred writer changes, validation scope, and release-test cost.
- **Validation blockers were viewed differently.** Gemini treated validation-first behavior as an unqualified strength; Codex warned that strategy-derived blockers may be too broad for plain export workflows. This should be resolved in planning before execution.
- **Bundle determinism emphasis varied.** Gemini suggested future fixed ZIP timestamps only as an enhancement, the agent flagged createdUtc test determinism, and Codex focused more on source filename/API completeness and temp cleanup.
- **Strategy schema composition remains a design choice.** Codex questioned whether weighted, race-filtered, and bucket rules should compose rather than being mutually exclusive strategy kinds; the agent accepted the formalized semantics but suggested documenting edge cases such as tiny weights and round-robin start position.

### Recommended Planning Follow-Up

- Re-run `/gsd-plan-phase 5 --reviews` so the planner can incorporate this review set before execution.
- Prioritize edits to 05-04 deterministic PRNG requirements, 05-02 BoS filename planner/sacred-file guardrails, 05-08 release smoke test gating and CLI publish properties, 05-06 bundle clock/source-path/temp-cleanup details, and validation scope for strategy blockers.
