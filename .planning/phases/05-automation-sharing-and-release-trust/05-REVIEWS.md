---
phase: 5
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-27T18:01:05.3620642-07:00
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

I will start by reading the project context, roadmap, requirements, phase context, and research findings to establish a solid understanding of the Phase 5 goals and constraints.
I've established a strong understanding of the Phase 5 objectives and constraints. Now, I will read all nine implementation plans for Phase 5 to evaluate their quality, completeness, and alignment with the established goals.
# Phase 5 Plan Review: Automation, Sharing, and Release Trust

## 1. Summary
Phase 5 implementation plans provide a robust and architecturally sound roadmap for delivering CLI automation, portable project sharing, and release trust. The strategy correctly prioritizes **Core service reuse**, ensuring that headless generation and bundling paths never drift from the proven, byte-sensitive GUI logic. By promoting the "Advanced" assignment strategies (ADV-03) into the core Phase 5 scope, the plans deliver a high-value deterministic engine that solves a key modder pain point: reproducible randomness across machines. The separation of concerns between `BS2BG.Cli` (parsing/orchestration) and `BS2BG.Core` (logic/I/O) is strictly maintained, and the "Validation-First" write gate ensures that automation remains safe and predictable.

## 2. Strengths
- **Thin CLI Architecture:** Plan 01 and 02 correctly use `System.CommandLine` to build a lightweight entry point that delegates all heavy lifting to `BS2BG.Core`, preventing the "dual implementation" trap that often breaks parity in ports.
- **Privacy-First Bundling:** Plan 06's emphasis on a dedicated `BundlePathScrubber` and relative-only manifest entries proactively mitigates information disclosure risks (e.g., leaking local usernames or drive structures) when sharing projects.
- **Deterministic Strategy Engine:** Plans 03 and 04 provide a comprehensive implementation of seeded random, round-robin, and weighted assignments. The decision to preserve the `IRandomAssignmentProvider` seam (D-16) ensures these features remain unit-testable and consistent.
- **Pragmatic Release Trust:** Plan 08 handles the "Signed vs. Unsigned" dilemma (D-17) by providing a checksum-backed fallback path that doesn't block developers without code-signing certificates while still improving user confidence.
- **Undo/Redo Integration:** Plan 05 ensures that even complex "Apply Strategy" operations remain user-friendly by integrating with the existing bounded undo history.

## 3. Concerns
- **[MEDIUM] CLI Service Composition Duplication:** Plan 02 Task 2 suggests composing Core services directly in `Program.cs`. While this avoids a dependency on `BS2BG.App`, there is a risk that the `TemplateProfileCatalog` initialization (which involves loading specific JSON settings files) might diverge from the App's bootstrapper over time. 
- **[LOW] Zip Determinism:** While Plan 06 mentions deterministic folders and sorting, `System.IO.Compression.ZipArchive` does not produce byte-identical ZIP files by default (due to timestamps). If modders start comparing bundle hashes for verification, this might cause minor confusion, though it doesn't affect functional trust.
- **[LOW] CLI Overwrite Safety Granularity:** The `--overwrite` flag is a global toggle in the current plans. If a user only wants to overwrite one of multiple outputs, the CLI doesn't currently support that level of granularity (it's "all or nothing" for the selected intent).

## 4. Suggestions
- **Shared Bootstrapper Logic:** Consider moving the "Core Service Composition" logic (the part that sets up the DI container with `ProjectFileService`, `TemplateProfileCatalog`, etc.) into a shared `BS2BG.Core.Startup` or similar assembly. This allows both `BS2BG.App` and `BS2BG.Cli` to share the exact same service registration and profile-loading logic.
- **Exit Code Documentation:** Explicitly document the CLI exit codes (0=Success, 1=Usage, 2=Validation Blocker, 3=Overwrite Refused, 4=I/O Error) in the packaged `README.md` or as part of the `--help` output to assist scripters.
- **Bundle Manifest Validation:** In the `BundlePathScrubber` (Plan 06), ensure that the "no absolute paths" check also scans the *content* of the validation reports, as these often contain full file paths if not explicitly truncated.

## 5. Risk Assessment
**Overall Risk: LOW**

The plans are highly detailed and strictly follow the established architectural boundaries. The use of **TDD** across all tasks and the inclusion of **human-verify checkpoints** for UI work significantly reduce the risk of regressions. The most critical risk--breaking Java parity--is mitigated by reusing the existing, tested `BS2BG.Core` writers. The "no-plugin-editing" boundary is clearly respected, preventing the project from veering into high-risk plugin-corruption territory.

### Key Risk Mitigations:
- **Parity Risk:** Mitigated by reusing `BodyGenIniExportWriter` and `BosJsonExportWriter`.
- **Privacy Risk:** Mitigated by `BundlePathScrubber` and relative entries.
- **Safety Risk:** Mitigated by mandatory validation-before-write in both CLI and Bundle paths.

---

## the agent Review

I've read all the Phase 5 planning artifacts. Here is the cross-AI plan review.

---

# Phase 5 Cross-AI Plan Review - Automation, Sharing, and Release Trust

## 1. Summary

The Phase 5 plan set is structurally sound and Java-parity-aware: nine plans across four waves cover CLI foundation (01-02), strategy data + execution + UI (03-05), portable bundle Core + glue (06-07), and release trust + setup docs (08-09). Dependency ordering between plans is clean and parallelizable. Locked decisions D-01..D-20 are explicitly threaded into individual plans, the System.CommandLine 2.0.7 stable choice is justified, the existing `IRandomAssignmentProvider` seam is honored, and the no-fork-of-output-writers rule is repeatedly reinforced. The plans materially de-risk the "trust" part of Phase 5 well, but several plans are under-specified in load-bearing places -- most critically how the bundle reproduces byte-identical BodyGen INI / BoS JSON content without forking writer code (Plan 06), how `GroupsBuckets` and `Weighted` strategies actually decide membership/probabilities (Plan 04), and how the bundle reuses `DiagnosticsReportFormatter` without dragging the App layer into Core (Plan 06).

## 2. Strengths

- **Architectural discipline.** Every plan repeats the rule that CLI/bundle paths must call `BodyGenIniExportWriter` / `BosJsonExportWriter` / `TemplateGenerationService` / `MorphGenerationService` rather than `File.WriteAllText`. Anti-pattern callouts are concrete (Plan 01-02 acceptance criteria explicitly forbid `File.WriteAllText` for the parity-sensitive artifacts). This is the right defense against Pitfall 1.
- **Wave decomposition is correct.** Plans 01/03/08 are genuinely independent (CLI shell, strategy data shape, release script). Plans 02/04/09 each unblock on a single Wave 1 sibling. Plans 05/06 land in Wave 3, and the only Wave 4 plan (07) waits on both UI (05) and Core bundle (06). No false serialization, no circular deps.
- **Validation-first contract is enforced consistently.** Plan 02 ties `ValidationBlocked = 2` -> no writes, Plan 04 reuses the validation report for no-eligible findings, Plan 06 bundles the validation report into `reports/validation.txt`. The exit-code map (0/1/2/3/4) is defined once in Plan 01 and reused.
- **Provider seam preservation is explicit.** Plan 04 mandates `SeededRandomAssignmentProvider : IRandomAssignmentProvider`, satisfying D-16, and Plan 04 Task 1 Test 6 specifically asserts the seam is used rather than `new Random()` scattered through assignment logic.
- **Privacy posture is testable, not aspirational.** Plan 06 introduces `BundlePathScrubber` with rejection of `Path.IsPathRooted`, `..`, drive letters, backslashes in entry names, and `Environment.UserName`, with specific test assertions (Plan 06 Task 1 Test 2).
- **Optional signing is correctly modeled.** Plan 08 treats SignTool as optional, generates `SIGNING-INFO.txt` for both signed/unsigned paths, and the test plan explicitly does not require `signtool.exe` on PATH (Plan 08 Task 1 Test 4) -- directly addressing Research Pitfall 5.
- **Visual checkpoints are blocking.** Plans 05 and 07 each include a `checkpoint:human-verify` gate with `gate="blocking"` and clear `how-to-verify` steps -- appropriate for compiled-binding/accessibility verification that automated headless tests can't fully cover.

## 3. Concerns

### HIGH

- **HIGH - Plan 06 does not specify how the bundle reproduces byte-identical BodyGen INI / BoS JSON content without forking writers.** The plan says the bundle service should "compose `TemplateGenerationService.GenerateTemplates`, `MorphGenerationService.GenerateMorphs`, BosJsonExportWriter-equivalent preview content through TemplateGenerationService/filename sanitization without writing to external folders". This is the load-bearing parity concern of the entire phase. `BodyGenIniExportWriter` and `BosJsonExportWriter` currently only expose disk-write entry points (per the codebase notes), not stream-based writes. The plan must pick one of: (a) refactor those writers to expose `WriteToStream(Stream)` overloads that the bundle calls; (b) have the bundle write to a temp directory using the existing writers and then zip the temp directory; or (c) extract the byte-producing logic into a shared `IXyzWriter.RenderBytes(...)` and have both file and zip paths consume it. As written, the plan leaves room for someone to write a "BoS JSON-equivalent" code path inside `PortableProjectBundleService`, which silently breaks D-02 and Java parity -- exactly the kind of drift Pitfall 1 warns about. Sacred files (`BodyGenIniExportWriter.cs`, `BosJsonExportWriter.cs`) require explicit caution before refactor.

- **HIGH - Plan 04 under-specifies `Weighted` and `GroupsBuckets` semantics.** "Weighted strategy uses deterministic weighted selection with stable rule ordering" is not a spec -- it doesn't say whether the draw is per-NPC over rules or per-NPC over presets, whether weights are normalized first, whether ties are broken by ordinal rule name, or how seeded provider's `NextIndex(int)` is mapped to a fractional cumulative-weight selection. `GroupsBuckets` is worse: "restricts assignments to configured bucket preset names" doesn't say what determines which bucket an NPC is in (race? rule order match? mod prefix?). Two competing implementations would both pass the current acceptance criteria yet produce different outputs, and the exact-sequence test asserts (Plan 04 Task 1 acceptance) require a deterministic spec to write the expected sequence against. Recommend writing the algorithmic spec -- including bucket-membership predicate and weighted draw mechanism -- into Plan 04 before execution.

- **HIGH - Plan 04 Task 2 duplicates eligibility logic between `AssignmentStrategyService` and `ProjectValidationService`.** The plan says validation should "evaluate `ProjectModel.AssignmentStrategy` when present by reusing AssignmentStrategyService eligibility checking without mutating assignments". If "reusing" means a separate method on `AssignmentStrategyService` (e.g., `ComputeEligibility(project, strategy) -> IReadOnlyList<NpcEligibility>`) called from both apply-time and validation, that's fine. If it means a parallel implementation, drift is guaranteed and the no-eligible diagnostic could disagree with actual apply behavior. Plan should explicitly mandate a shared eligibility method consumed from both call sites.

### MEDIUM

- **MEDIUM - Plan 06 has an unaddressed Core/App boundary problem with the validation report formatter.** `src/BS2BG.App/Services/DiagnosticsReportFormatter.cs` (referenced in Phase 5 CONTEXT Reusable Assets) lives in App. The bundle service in Core cannot reference it. Plan 06 says "Generate validation/report text from findings with scrubbed source filenames and relative bundle paths only" but doesn't say whether to (a) move/duplicate the formatter into Core (Sacred-ish: it's been used by Phase 3 diagnostics, so existing tests anchor its output), (b) introduce a thin Core formatter and accept output divergence, or (c) inject an `IValidationReportFormatter` interface. Without choosing, the executor will likely duplicate formatter logic and create a second report shape.

- **MEDIUM - Plan 02 leaves CLI catalog composition as a fuzzy "or" decision.** "Create TemplateProfileCatalog from root settings.json/settings_UUNP.json/settings_FO4_CBBE.json using existing Core profile loaders or the same factory pattern copied only if it does not add Avalonia/App references." `TemplateProfileCatalogFactory` lives in App per the codebase map; the plan doesn't say which Core method (`SliderProfileJsonService` + `TemplateProfileCatalog` constructor?) to call, nor how the CLI resolves embedded custom profiles in `.jbs2bg` projects (Phase 4 trust domain). This decision should be made in Plan 02 -- either extract the factory into Core (preferred since it's referenced by both CLI and App) or document the exact composition recipe. Otherwise the executor will likely re-implement Phase 4 catalog composition and lose embedded-custom-profile semantics.

- **MEDIUM - Plan 03's `AssignmentStrategyRule` shape is a bag-of-fields rather than per-Kind variants.** A single record with `Name`, `PresetNames`, `RaceFilters`, `Weight`, `BucketName` is convenient for JSON round-trip but doesn't enforce per-Kind invariants (e.g., `RoundRobin` shouldn't have rules at all, `SeededRandom` shouldn't either, `Weighted` requires Weight > 0). This pushes invariant-checking into the executor and the validation finding rather than the type system. Acceptable for v1 if Plan 04 adds explicit rule-shape validation per Kind, but the plan doesn't currently call this out. Recommend adding load-time validation in Plan 03 Task 2 (negative weights rejected, duplicate rule names rejected, empty PresetNames for `GroupsBuckets` rejected) so malformed shared `.jbs2bg` files fail clearly rather than at apply time.

- **MEDIUM - Plan 03 places `[JsonPropertyOrder(4)]` for `AssignmentStrategy` without verifying the existing DTO order.** `CustomProfiles` was added in Phase 4; whether its `JsonPropertyOrder` is 3 or something else needs to be checked. If `CustomProfiles` is also at 4 (or the order numbers conflict), the new section may interleave incorrectly with downstream JSON consumers reading positionally. Add a `read_first` directive that explicitly inspects `ProjectFileService.cs` DTO ordering and chooses the next free slot.

- **MEDIUM - Plan 02 assumes `MorphGenerationService.GenerateMorphs(project).Text` and similar API shapes.** The plan instructs to call `MorphGenerationService.GenerateMorphs(project).Text` and `TemplateGenerationService.GenerateTemplates(project.SliderPresets, catalog, omitRedundantSliders)` without first reading those service signatures. The user's CLAUDE.md requires verifying APIs of what is being tested. If the actual signature differs (e.g., `MorphGenerationService` returns a `MorphGenerationResult` with property `Output` rather than `Text`, or takes a `ProjectModel` rather than a preset list), the executor will need to deviate and the plan's TDD test cases may not compile. Add `read_first` for both service files and their existing callers in `MainWindowViewModel`/`AppBootstrapper`.

- **MEDIUM - Plan 05 doesn't specify the rule-editing input format.** "binding collections for rules sufficient to edit Name, PresetNames text, RaceFilters text, Weight, BucketName" is loose. Are PresetNames a `TextBox` with comma-separated values? A multi-select listbox bound to `project.SliderPresets`? Comma-separated text means user typos silently produce empty rules (no eligible preset -> blocker for every NPC, by design -- but the UX is unfriendly). Recommend specifying input control type and parsing rules, plus client-side validation that rejects unknown preset names against the current project. Otherwise the visual checkpoint will keep bouncing back.

- **MEDIUM - Plan 07's GUI preview requires a method on `PortableProjectBundleService` that Plan 06 doesn't define.** Plan 07 Task 2 Test 1: "PreviewPortableBundleCommand populates layout entries ... without writing zip." Plan 06 only defines `Create(request)` with a `BundlePath`. Either Plan 06 must expose a `Preview(request) -> BundlePreview` method that returns the manifest entries and privacy status without zipping, or Plan 07 needs to call `Create` with a temp path and then delete -- wasteful and error-prone. Add `Preview(request)` to Plan 06's contracts (Task 1, `PortableProjectBundleContracts.cs`).

- **MEDIUM - Plan 04 Task 1 is too large.** Five strategy implementations (`SeededRandom`, `RoundRobin`, `Weighted`, `RaceFilters`, `GroupsBuckets`) plus provider-seam preservation in a single atomic commit, plus shared eligibility logic. This makes the per-task `dotnet test` filter run a wide blast radius, and it makes deviation handling messy if one strategy is harder than expected. Recommend splitting into `04a` (data-driven core: ordering, eligibility, SeededRandom + RoundRobin) and `04b` (Weighted + RaceFilters + GroupsBuckets), or alternatively splitting per-strategy into their own subtasks within Plan 04 with separate verifications.

- **MEDIUM - Plan 06 omits a `manifest.json` schema.** `BundleManifestEntry(string Path, string Kind, string Sha256)` is good, but the top-level manifest shape (version field? bundle creation timestamp? source project name with extension only?) isn't specified. Without a schema, the next person to read a bundle won't know what to expect, and downstream tools/tests can't lock the contract. Add a one-paragraph schema spec to Plan 06 Task 1 (e.g., `{ "schemaVersion": 1, "createdUtc": "...", "projectName": "...", "entries": [...] }`) and assert it in tests.

- **MEDIUM - Plan 02 doesn't address partial-failure rollback for `--intent all`.** If `BodyGenIniExportWriter.Write` succeeds but `BosJsonExportWriter.Write` fails midway, the user is left with templates.ini/morphs.ini written and partial BoS JSON. The exit-code returns `IoFailure (4)` but doesn't say whether to roll back the BodyGen writes. Phase 3's atomic outcome ledger work (DIAG-05) is the right reuse -- Plan 02 should explicitly call out "report a save outcome ledger consistent with Phase 3 atomic outcome reporting" and decide rollback policy. Right now it's implicit.

- **MEDIUM - Plan 08 Task 1 tests are largely string-grep over the PowerShell script.** `assert required strings` against `package-release.ps1` is brittle -- moving a comment around breaks the test. The actual contract is "the produced zip contains these files" which is testable by running the script in `artifacts/test-out` and inspecting the output zip. Research recommends doing the runtime smoke "if existing release tests already do so quickly". `tests/BS2BG.Tests/ReleasePackagingScriptTests.cs` already exists -- Plan 08 should explicitly state whether it runs the script in test scope or only string-greps, and if the latter, why the smoke is deferred.

- **MEDIUM - Plan 08 doesn't forbid logging signing secrets.** `[string]$CertificatePasswordEnvVar` is the right shape for accepting a password, but the plan doesn't add an explicit rule "never `Write-Host` or `Write-Output` the resolved password value" or "redact certificate path from `SIGNING-INFO.txt` if it contains user paths". Worth adding to the threat model and acceptance criteria.

- **MEDIUM - Plan 05 doesn't address scope interaction with Phase 2's bulk-scope selectors.** Phase 2 added explicit visible/selected/visible-empty bulk scopes for NPC operations to prevent silent mutation of filtered rows. Strategy apply mutates all NPCs by default (Plan 04 Task 1: "For each NPC in project.MorphedNpcs ordered by..."). If a user has filtered to "humans only" and applies a strategy, do filtered-out rows get reassigned? Plan 05 needs to either (a) respect scope selection (then strategy "deterministic across runs" only holds when scope matches) or (b) explicitly state strategy ignores scope and apply produces a confirmation dialog. Either way it's a decision that needs to be made before the visual checkpoint.

### LOW

- **LOW - Plan 01 Task 1 Test 1 ("project exists and is referenced by the solution") is more a structural check than a runtime test.** The test will need to either read `BS2BG.sln` text or rely on `dotnet sln list`. Plan should specify the mechanism so it isn't accidentally implemented as a `File.Exists` check that doesn't catch the sln-registration problem.
- **LOW - Plan 01 names `BS2BG.Cli` as `OutputType=Exe` (not `WinExe`).** Correct for CLI, but worth noting that on Windows, `Exe` produces a console-attached executable while `WinExe` doesn't -- Plan 01 chose right; just verify with the `read_first` of `BS2BG.App.csproj` that the existing project distinguishes correctly.
- **LOW - Plan 02 omits the `omit-redundant-sliders` knob.** GUI persists this preference (Phase 2). CLI hard-defaults to `false`. Two collaborators with different preferences will produce different outputs. Either add `--omit-redundant-sliders` to the CLI or document the decision (probably acceptable since CLI is automation-first).
- **LOW - Plan 06's `IsPrivatePathLeak` may false-positive on `C:` substrings inside legitimate report content** (e.g., a preset literally named `C:Body`). Probably acceptable for v1 but worth a comment in the scrubber.
- **LOW - Plan 09 Task 1 Test 3 ("MainWindow.axaml does not contain Setup wizard or BODYGEN-BODYSLIDE-BOS-SETUP menu entry") is a brittle string-grep.** Slight rewordings of unrelated UI elements could fail the test or false-pass. Plan should anchor the assertion on attribute names like `MenuItem Header="Setup"` patterns rather than free-text "Setup wizard".
- **LOW - Plan 02 doesn't specify how CLI tests invoke the binary.** Running `dotnet run --project src/BS2BG.Cli` from xUnit is slow and adds dotnet-cli + build coupling per test. Direct `Program.Main(args)` invocation in-process is the standard pattern for CLI testing and avoids out-of-process flakiness.
- **LOW - Plan 03 Test 2 description ("Strategy types include SeededRandom, RoundRobin, Weighted, RaceFilters, GroupsBuckets") is just an enum-shape assertion, not behavior coverage.** Fine for Plan 03 since behavior tests live in Plan 04, just call this out so the executor doesn't over-test in the wrong layer.
- **LOW - Plan 06 doesn't set `ZipArchiveEntry.LastWriteTime` to a fixed value.** Research Open Question 3 left this deliberately deferred. Calling it out in Plan 06's documented success criteria as "deterministic ordering, non-deterministic timestamps acceptable in v1" would prevent surprise during execution.

## 4. Suggestions

1. **Resolve the writer-stream question in Plan 06 before execution.** Add a Task 0 to Plan 06: "Refactor `BodyGenIniExportWriter` and `BosJsonExportWriter` to expose stream-write entry points (e.g., `WriteToStream(Stream, ...)` returning the same byte content), with golden-file tests verifying the existing disk-write path still produces identical bytes." Sacred-files caveat applies; flag the change with the user explicitly. This is the single biggest risk to Java parity in the phase.

2. **Promote `DiagnosticsReportFormatter` (or extract its core) into Core.** Either move the formatter to Core (and have App keep a thin wrapper) or define `BS2BG.Core.Diagnostics.IValidationReportFormatter` and migrate. This unblocks Plan 06 cleanly without report-shape divergence.

3. **Specify `Weighted` and `GroupsBuckets` algorithms in Plan 04 prose.** Add a sub-section: "Weighted: each NPC's eligible rule set is determined by combining `RaceFilters` (if any) with `BucketName` (none for Weighted), then a single weighted draw is performed using cumulative weights and `IRandomAssignmentProvider.NextIndex`. Ties broken by ordinal rule name. GroupsBuckets: bucket membership is determined by `RaceFilters` match against `Npc.Race`; preset eligibility is the rule's `PresetNames`."

4. **Split Plan 04 Task 1 into two atomic tasks.** "1a. Implement core executor with stable ordering, shared eligibility computation, SeededRandom, and RoundRobin." "1b. Add Weighted, RaceFilters, GroupsBuckets atop the shared executor." Each commits with its own filtered test pass.

5. **Define a shared eligibility surface explicitly.** In Plan 04, add an instruction: "Implement `IReadOnlyList<NpcEligibility> ComputeEligibility(ProjectModel, AssignmentStrategyDefinition)` and have both `Apply()` and `ProjectValidationService.Validate()` call this -- do not duplicate the logic." Add a regression test that strategy-blocked NPCs from `Apply` exactly equal those flagged by `Validate`.

6. **Add load-time strategy validation to Plan 03 Task 2.** Reject negative `Weight`, duplicate rule `Name`, empty `PresetNames` for `GroupsBuckets`/`Weighted`, and empty `RaceFilters` for `RaceFilters` rules -- surface as load-time `JsonException` with a clear message.

7. **Add a `PreviewBundle(request)` API to Plan 06.** Return `(IReadOnlyList<string> Entries, string ManifestJson, ProjectValidationReport ValidationReport, IReadOnlyList<string> PrivacyFindings)` without invoking `ZipArchive`. Plan 07 then becomes straightforward.

8. **Specify `manifest.json` schema in Plan 06.** Lock `schemaVersion`, `createdUtc` (UTC ISO-8601), `bundleSourceProjectName` (filename only), `entries[].path`, `entries[].kind` (one of `project|bodygen|bos|profile|report|manifest|checksum`), `entries[].sha256` (lowercase hex). Add a fixture-file test asserting JSON shape.

9. **Specify rule-editing UI controls in Plan 05.** "PresetNames bound to a multi-select dropdown over `project.SliderPresets`. RaceFilters bound to a token-list editor. Weight bound to a NumericUpDown with Min=0, Max=1000. BucketName bound to TextBox with project-scoped uniqueness validation."

10. **Decide strategy-vs-bulk-scope policy in Plan 05.** Recommend: "Strategy apply ignores filter scope and operates on the full `MorphedNpcs` collection so determinism holds across machines; expose the count of NPCs to be (re)assigned in `StrategySummaryText` before apply."

11. **Tighten Plan 08 Task 1 to actually run the release script in a smoke test.** If `ReleasePackagingScriptTests.cs` already runs `package-release.ps1` to a temp directory, extend it to assert `SIGNING-INFO.txt`, `SHA256SUMS.txt`, `BODYGEN-BODYSLIDE-BOS-SETUP.md` (Plan 09 alignment), and check ZipArchive entries. If it doesn't run the script, Plan 08 should add that capability rather than rely on string-greps.

12. **Add explicit `read_first` directives that verify service signatures** (Plan 02: `MorphGenerationService.GenerateMorphs` return type; `TemplateGenerationService.GenerateTemplates` parameters) so the executor confirms APIs before writing tests against assumed shapes.

13. **Specify CLI test invocation pattern in Plan 02.** "Tests invoke `BS2BG.Cli.Program.Main(string[])` directly in-process, capturing stdout/stderr via `Console.SetOut`/`SetError`. Do not invoke `dotnet run` from xUnit."

## 5. Risk Assessment

**Overall risk: MEDIUM.**

Justification:
- **Architectural risk is well-contained** -- the no-second-formatter rule, validation-first contract, and provider-seam preservation are repeated across plans and acceptance criteria. The Wave structure is correct, ADV-03 absorption is clearly scoped, and visual checkpoints gate UI work.
- **Two HIGH concerns concentrate in the parity-sensitive seams**: how the bundle reproduces byte-identical writer output (Plan 06) and how `Weighted`/`GroupsBuckets` strategies become unambiguous deterministic algorithms (Plan 04). Both are addressable with planning prose and, in Plan 06's case, a small refactor of the writer entry points; neither requires re-architecting the phase.
- **The MEDIUM cluster is mostly "specify-this-before-execute" issues**: catalog composition recipe, manifest schema, rule input format, partial-failure rollback, eligibility deduplication, scope policy. Each is a one-paragraph fix at planning time but a multi-hour deviation at execution time.
- **Sacred-file blast radius is bounded.** Only Plan 06 (and the suggested Plan 06 Task 0) plausibly touches the byte-load-bearing writers. Plan 02 only calls them; Plans 01/03/04/05/07/08/09 don't touch parity logic.
- **External dependencies are appropriately minimal.** SignTool is optional, NuGet versions verified 2026-04-27, no new third-party packages beyond `System.CommandLine`.

If the HIGH issues are addressed via plan-level edits (specifically: writer-stream API in Plan 06, Weighted/GroupsBuckets algorithms in Plan 04, shared eligibility method in Plan 04 Task 2), and at least the catalog-composition and validation-formatter MEDIUM concerns are decided in Plans 02 and 06 respectively, this phase can be executed at LOW-MEDIUM risk. Without those edits, the most likely failure mode is an invisible byte-level divergence in bundled outputs vs. CLI/GUI outputs, discovered only when a downstream user diff-checks them.

---

## Codex Review

**Overall Summary**
The Phase 5 plan set is mostly coherent and maps well to AUTO-01 through AUTO-05. The strongest parts are the explicit Core/App/CLI boundary, validation-first CLI posture, path-scrubbed bundle concept, optional signing path, and test-first framing. The main risks are around drift from existing export writers, under-specified assignment strategy semantics, release packaging not explicitly shipping the new CLI, and use of APIs/patterns that may not fit `BS2BG.Core`'s `netstandard2.1` target.

## 05-01 - CLI Foundation

**Summary:** Good foundation plan. It correctly creates a dedicated CLI project and typed Core contracts before implementing behavior.

**Strengths**
- Dedicated `BS2BG.Cli` honors the user decision.
- `System.CommandLine` avoids hand-rolled parsing.
- Explicit intent and exit-code contracts are useful downstream.

**Concerns**
- **MEDIUM:** Tests may need a clear strategy for invoking the CLI without requiring test project references to the CLI executable.
- **LOW:** The plan does not explicitly say CLI profile assets must copy to build/publish output.

**Suggestions**
- Add acceptance criteria that CLI publish/build output includes required profile JSON files.
- Decide whether CLI tests run `dotnet run`, invoke `Program`, or execute published binaries.

**Risk Assessment:** **LOW-MEDIUM**. The design is sound; packaging/runtime asset details need tightening.

## 05-02 - Headless Generation

**Summary:** This is the right shape for AUTO-01, but it needs stronger protection against export-writer drift and profile lookup regressions.

**Strengths**
- Reuses `ProjectFileService`, generation services, validation, and existing writers.
- Blocks on validation findings before writing.
- Preserves overwrite refusal behavior.

**Concerns**
- **HIGH:** BoS overwrite preflight requires knowing exact generated filenames, but `BosJsonExportWriter` currently owns private filename sanitization/uniqueness logic. Duplicating that logic in the service would create drift.
- **HIGH:** CLI profile catalog resolution is under-specified. It must resolve from install/publish locations such as `AppContext.BaseDirectory`, not repo cwd or ancestor probing.
- **MEDIUM:** Error mapping should cover corrupt JSON/project load failures, invalid profile files, missing profile assets, `ArgumentException`, and path errors, not only `IOException`/`UnauthorizedAccessException`.
- **LOW:** `ProjectValidationService.Validate` is currently static, while the plan describes it as a constructor dependency.

**Suggestions**
- Extract an export plan/filename planner from `BosJsonExportWriter`, or add a writer-owned dry-run method used by both preflight and writing.
- Add tests that run the CLI from a temp working directory to prove profile lookup is install-relative.
- Define failure mapping for invalid project JSON and missing profile files.

**Risk Assessment:** **MEDIUM-HIGH** because output filename parity and profile loading are trust-critical.

## 05-03 - Strategy Persistence

**Summary:** Good first step for AUTO-03, but the persisted model is too vague for full weighted/group/bucket behavior.

**Strengths**
- Keeps strategy state in Core and `.jbs2bg`.
- Optional JSON field preserves legacy project compatibility.
- Race matching is explicitly imported `Npc.Race` only.

**Concerns**
- **HIGH:** `GroupsBuckets` is not semantically defined enough. A `BucketName` field on rules does not explain how NPCs enter buckets or how bucket rules compose.
- **MEDIUM:** `Weight` needs validation for zero, negative, `NaN`, infinity, and all-zero rule sets.
- **MEDIUM:** `ProjectModel.AssignmentStrategy` dirty tracking is not automatic unless the setter/mutation path calls project change notification.
- **MEDIUM:** `ProjectModel.ReplaceWith` and cloning paths must copy strategy state, otherwise project open/replace flows may drop it.

**Suggestions**
- Add a formal strategy schema section before implementation: rule predicates, bucket selection, rule ordering, conflict handling, and invalid values.
- Add tests for dirty state, `ReplaceWith`, null strategy omission, and invalid strategy hydration.

**Risk Assessment:** **MEDIUM**. Persistence is achievable, but unclear semantics will cause downstream churn.

## 05-04 - Strategy Execution

**Summary:** The plan covers the requested strategy menu, but several algorithm details need to be made deterministic and observable before coding.

**Strengths**
- Exact-sequence tests are the right approach.
- No-eligible diagnostics correctly block instead of falling back.
- Existing random-provider abstraction remains central.

**Concerns**
- **HIGH:** "Groups/buckets" remains ambiguous enough that two implementers could build different behavior.
- **MEDIUM:** Applying to all `project.MorphedNpcs` ordered by model fields may conflict with "eligible rows" wording and existing visible/selected bulk-operation expectations.
- **MEDIUM:** `System.Random` seed replay may be stable enough for one runtime, but a small stable PRNG behind `IRandomAssignmentProvider` would better protect reproducibility across app versions.
- **MEDIUM:** Validation needs a non-mutating eligibility evaluator shared with execution, not a second partial implementation.

**Suggestions**
- Add `Apply(..., IReadOnlyList<Npc> eligibleRows)` or equivalent so GUI scopes and CLI behavior are explicit.
- Define stable PRNG behavior in tests by exact sequence, not just repeated-run equality.
- Split eligibility planning from mutation, and use that same planner for diagnostics.

**Risk Assessment:** **HIGH** until group/bucket and eligible-row semantics are pinned down.

## 05-05 - Strategy UI

**Summary:** The UI plan follows the ReactiveUI/Avalonia direction and includes a human checkpoint, but strategy editing and undo scope need more precision.

**Strengths**
- Places controls in Morphs where assignment behavior lives.
- Requires compiled bindings, accessible names, and visible trust/error text.
- Includes undo and dirty tracking expectations.

**Concerns**
- **HIGH:** If applying a strategy saves `ProjectModel.AssignmentStrategy`, undo must restore both previous assignments and previous strategy config. The plan currently emphasizes assignment snapshots only.
- **MEDIUM:** Text fields for `PresetNames`, `RaceFilters`, and rules need exact parsing syntax and validation errors.
- **MEDIUM:** The plan may produce a dense, complex Morphs panel; "full menu" likely needs careful layout and headless/visual checks.

**Suggestions**
- Add tests that undo restores previous strategy configuration as well as NPC assignments.
- Specify delimiters, trimming, duplicate handling, unknown preset handling, and invalid weight messages.
- Add headless UI assertions for required controls, not only ViewModel tests.

**Risk Assessment:** **MEDIUM-HIGH** because UI complexity and undo semantics can easily diverge from project persistence.

## 05-06 - Portable Bundle Service

**Summary:** This is a strong plan conceptually, but it has the highest parity and platform-compatibility risks.

**Strengths**
- Correct structured zip layout.
- Strong privacy scrubbing and relative-entry validation.
- Referenced custom profiles only is the right sharing boundary.

**Concerns**
- **HIGH:** `SHA256.HashData` is not safe to assume in `BS2BG.Core` because Core targets `netstandard2.1`; use `SHA256.Create().ComputeHash(...)` or isolate newer APIs outside Core.
- **HIGH:** "BosJsonExportWriter-equivalent preview content" risks duplicating private writer filename/content behavior. Bundle output must use writer-owned planning or extracted shared export logic.
- **MEDIUM:** If a project references a custom profile not embedded and not available in `ProjectSaveContext`, the bundle should block or report a clear missing-profile diagnostic, not silently omit it.
- **MEDIUM:** The plan should explicitly define whether validation blockers prevent bundle creation or produce a report-only bundle.

**Suggestions**
- Add an export planning abstraction reused by CLI generation, bundle creation, and writers.
- Add tests for missing referenced custom profiles and duplicate zip entry names.
- Use netstandard-compatible hash APIs.

**Risk Assessment:** **HIGH** due to parity-sensitive output generation and Core target constraints.

## 05-07 - CLI/GUI Bundle Integration

**Summary:** Good integration plan, especially the preview-first GUI workflow, but it depends on the bundle service being stricter than currently specified.

**Strengths**
- Adds both CLI and GUI bundle creation.
- Keeps explicit intent and overwrite safety.
- Requires visible privacy status before writing.

**Concerns**
- **MEDIUM:** CLI `ProjectSaveContext` "from embedded profiles only" may fail for local referenced profiles unless the CLI can load the same custom profile catalog or blocks clearly.
- **MEDIUM:** Tests are named `PortableBundleServiceTests`, but GUI preview command behavior deserves ViewModel/headless coverage.
- **LOW:** Preview should be generated from the same bundle planner used for writes so preview cannot lie.

**Suggestions**
- Add ViewModel tests for preview state, overwrite refusal, selected intent, and privacy failure state.
- Make missing profile copies a blocker or explicit preview warning before bundle write.

**Risk Assessment:** **MEDIUM**. Good user workflow, dependent on service-layer rigor.

## 05-08 - Release Trust

**Summary:** The signed/unsigned trust model is correct, but the plan misses an important release-distribution question: the new CLI must be packaged.

**Strengths**
- Signing remains optional.
- SHA-256 sidecars and `SHA256SUMS.txt` are required.
- Source-level tests protect release script/docs invariants.

**Concerns**
- **HIGH:** The plan does not explicitly publish/package `BS2BG.Cli.exe`. AUTO-01 is much weaker if users cannot get the CLI from the release artifact.
- **MEDIUM:** Source-text assertions alone are not enough release trust. At least one package-script smoke test or generated zip inspection should be part of the phase gate.
- **MEDIUM:** `settings_FO4_CBBE.json` is asserted but not explicitly called out in the script update action.
- **LOW:** Optional signing parameters need careful handling so certificate password values are never written to logs or package metadata.

**Suggestions**
- Update release packaging to publish and include both App and CLI artifacts, or explicitly justify a separate CLI package.
- Add a smoke test that runs `package-release.ps1` in a temp artifact path when feasible and inspects the resulting zip.
- Include `SIGNING-INFO.txt`, CLI docs, and checksum verification in package assertions.

**Risk Assessment:** **MEDIUM-HIGH** because release trust depends on testing actual artifacts, not just script text.

## 05-09 - Packaged Setup Docs

**Summary:** This plan is well-scoped and matches the "docs only, no wizard" decision.

**Strengths**
- Keeps guidance out of the app UI.
- Covers BodyGen, BodySlide, BoS, output locations, and no-plugin-editing boundary.
- Adds package inclusion tests.

**Concerns**
- **LOW:** Negative source-text tests against `MainWindow.axaml` can be brittle and may not catch other UI files.
- **LOW:** Docs tests should verify release package inclusion through script/package assertions, not only source script strings.

**Suggestions**
- Search all App AXAML/ViewModel files for forbidden setup-wizard/help-menu copy if this remains a test.
- Add the setup doc to the actual package smoke inspection from 05-08.

**Risk Assessment:** **LOW**. Scope is clear and implementation risk is modest.

## Cross-Plan Recommendations

- Add a shared export planning layer so overwrite preflight, CLI writes, bundle entries, and BoS filename generation cannot drift.
- Ensure the release package includes the new CLI executable and any profile assets it needs at runtime.
- Define assignment strategy semantics before implementation, especially eligible-row scope, group/bucket rules, invalid weights, and seeded PRNG behavior.
- Keep Core `netstandard2.1` compatibility in mind for hashing and any newer BCL APIs.
- Add one real package/bundle smoke path in addition to source-text tests.

---

## Consensus Summary

### Agreed Strengths

- All reviewers agreed the phase is directionally sound and maps cleanly to AUTO-01 through AUTO-05.
- All reviewers highlighted the thin CLI/Core boundary as a major strength because it protects Java parity and avoids a second generation implementation.
- All reviewers viewed validation-first behavior, explicit output intent, and overwrite refusal as appropriate automation safety controls.
- All reviewers supported the privacy-first portable bundle concept with relative paths, scrubbed reports, and referenced custom profiles only.
- All reviewers agreed optional signing plus SHA-256 verification is a pragmatic release-trust model.
- the agent and Codex both emphasized that wave ordering is mostly clean and parallelizable.

### Agreed Concerns

- **HIGH: Portable bundle output can drift from existing writers.** the agent and Codex both flagged Plan 06's "BosJsonExportWriter-equivalent" wording as a parity risk. Gemini rated overall risk lower but still called for stronger manifest/report validation. The planner should remove any ambiguity by requiring writer-owned stream/temp-directory/export-plan reuse for bundle contents.
- **HIGH: Assignment strategy semantics are underspecified.** the agent and Codex independently flagged weighted and groups/buckets behavior as ambiguous. The planner should define bucket membership, rule ordering, weight validation, tie-breaking, seeded draw mechanics, and eligible row scope before execution.
- **MEDIUM-HIGH: CLI and bundle profile/catalog composition need a single recipe.** Gemini, the agent, and Codex all raised some form of service/bootstrap/catalog duplication risk. The planner should specify install-relative profile lookup and avoid copying App-only bootstrap logic into CLI.
- **MEDIUM: Plan 05 needs clearer undo and UI parsing semantics.** Codex flagged undo must restore both assignments and strategy configuration; the agent flagged exact control/parsing ambiguity. The planner should specify strategy input controls and scope/undo behavior.
- **MEDIUM: Release trust should inspect real artifacts and package the CLI.** Codex explicitly flagged that `BS2BG.Cli.exe` is not guaranteed to ship. the agent and Codex both warned that source-string tests are weaker than release zip smoke inspection.
- **MEDIUM: Shared eligibility and preview/planning APIs should be explicit.** the agent and Codex both recommended a shared non-mutating eligibility surface for validation/apply consistency and a preview/planning API for bundle preview.

### Divergent Views

- **Overall risk level differed.** Gemini rated the plan set LOW risk, while the agent rated it MEDIUM and Codex identified several HIGH-risk seams. The difference appears to come from Gemini focusing on architectural intent, while the agent and Codex focused on implementation ambiguities in writer reuse, assignment algorithms, and release packaging.
- **Zip determinism was treated differently.** Gemini called non-byte-identical zip timestamps a LOW concern; the agent also considered fixed timestamps deferred; Codex focused more on `netstandard2.1` hashing compatibility than byte-identical zips.
- **Extent of pre-execution replanning.** Gemini suggested lighter enhancements such as shared bootstrap logic and exit-code docs. the agent and Codex recommended concrete plan edits before execution, especially for Plan 04 and Plan 06.

### Recommended Planning Follow-Up

- Re-run `/gsd-plan-phase 5 --reviews` before execution so the planner can incorporate the shared high-priority concerns.
- Prioritize revisions to Plan 06 writer reuse, Plan 04 strategy semantics/shared eligibility, Plan 02 catalog composition/profile lookup, Plan 05 undo/input/scope policy, and Plan 08 CLI package inclusion/artifact smoke tests.
