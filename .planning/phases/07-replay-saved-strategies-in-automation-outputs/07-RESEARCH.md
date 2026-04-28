# Phase 07: Replay Saved Strategies in Automation Outputs - Research

**Researched:** 2026-04-28  
**Domain:** .NET Core automation orchestration, deterministic assignment replay, CLI/bundle output generation  
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Replay Trigger And Scope
- **D-01:** Replay a saved assignment strategy automatically whenever an automation request includes BodyGen morph output and `ProjectModel.AssignmentStrategy` is present. CLI `generate --intent bodygen`, CLI `generate --intent all`, bundle BodyGen output, and bundle `all` output should replay; BoS-only automation should not replay because it does not generate `morphs.ini`.
- **D-02:** Do not require a new opt-in flag for saved strategy replay. Reproducibility must come from saved project data, so users who saved a strategy should not need to remember an extra CLI or bundle switch.
- **D-03:** If no saved assignment strategy exists, keep current automation behavior: generate morph output from the project's existing NPC/custom-target assignments.

#### Bundle Project State
- **D-04:** Keep `project/project.jbs2bg` inside portable bundles as the original saved/source project state, with the saved strategy configuration intact. Do not rewrite that bundle project file to contain replayed NPC assignments just because outputs were generated.
- **D-05:** Replayed assignments used for generated `morphs.ini` should be request-scoped working state. Bundle generation must avoid mutating the caller's project model or serializing replay side effects back into the bundled project entry.

#### Blocked NPC Policy
- **D-06:** If saved strategy replay leaves any NPC with no eligible preset, CLI and bundle BodyGen generation must fail before writing output files or zip entries. Treat these as automation blockers, not warnings, because partial or stale `morphs.ini` output would undermine reproducibility.
- **D-07:** Blocked strategy replay must not silently fall back to all presets and must not leave stale prior assignments in generated output. Existing Phase 5 strategy eligibility rules and random-provider abstractions remain authoritative.

#### Replay Visibility
- **D-08:** Successful strategy replay should be visible as concise summary counts: strategy kind, assigned NPC count, and zero blocked rows. Do not print or bundle per-NPC assignment listings on successful paths by default.
- **D-09:** Failure paths must include actionable blocked-NPC details in CLI output and bundle preview/report text so users can identify which rule, race filter, weight, or bucket configuration needs repair before rerunning automation.

### the agent's Discretion
- Exact helper/seam names are flexible, but keep the orchestration Core-only and reusable between `HeadlessGenerationService` and `PortableProjectBundleService`.
- Exact wording of success summaries and failure details is flexible as long as scripts still receive stable nonzero exit codes on blocked replay and users can identify blocked NPC rows.
- Exact test fixture construction is flexible, but tests must prove output reproducibility from saved strategy data rather than pre-mutated assignments.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within Phase 7 automation replay scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTO-02 | User can create a portable project bundle containing the `.jbs2bg` file, generated outputs, profile JSON copies, and a validation report without private local paths. [VERIFIED: .planning/REQUIREMENTS.md] | Use `PortableProjectBundleService.BuildPlan`/`AddGeneratedOutputEntries` with a cloned/request-scoped project for BodyGen output and keep `project/project.jbs2bg` serialized from the original request project. [VERIFIED: src/BS2BG.Core/Bundling/PortableProjectBundleService.cs] |
| AUTO-03 | User can apply deterministic assignment strategy presets through seams that remain testable and do not bypass existing random-provider abstractions. [VERIFIED: .planning/REQUIREMENTS.md] | Reuse `MorphAssignmentService.ApplyStrategy`/`AssignmentStrategyService.Apply`; do not create alternate RNG or assignment logic. [VERIFIED: src/BS2BG.Core/Morphs/MorphAssignmentService.cs; src/BS2BG.Core/Morphs/AssignmentStrategyService.cs] |
</phase_requirements>

## Project Constraints (from AGENTS.md)

- Core code must remain UI-free and must not reference Avalonia/App services; automation seams for this phase belong in `src/BS2BG.Core`. [VERIFIED: AGENTS.md]
- Do not change byte-sensitive writers or formatter math (`JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, `BosJsonExportWriter.cs`) unless explicitly approved. [VERIFIED: AGENTS.md]
- Output parity is load-bearing: BodyGen INI uses CRLF; BoS JSON uses LF with no trailing newline; golden expected fixtures must not be regenerated to hide failures. [VERIFIED: AGENTS.md]
- Use PowerShell/.NET commands on Windows; build with `dotnet build BS2BG.sln` and test with `dotnet test`. [VERIFIED: AGENTS.md]
- New tests use xUnit v3 and FluentAssertions style, not bare `Assert.*`. [VERIFIED: AGENTS.md; tests/BS2BG.Tests/BS2BG.Tests.csproj]
- New or substantially rewritten C# methods should have XML doc comments; comments explaining non-obvious WHY should be preserved or added. [VERIFIED: C:/Users/evild/.config/opencode/AGENTS.md]

## Summary

Phase 7 is not a new library problem; it is a Core orchestration problem. The existing code already has deterministic strategy execution (`AssignmentStrategyService`), a provider-compatible application seam (`MorphAssignmentService.ApplyStrategy`), CLI/headless generation (`HeadlessGenerationService`), and bundle generation (`PortableProjectBundleService`). [VERIFIED: src/BS2BG.Core/Morphs/AssignmentStrategyService.cs; src/BS2BG.Core/Morphs/MorphAssignmentService.cs; src/BS2BG.Core/Automation/HeadlessGenerationService.cs; src/BS2BG.Core/Bundling/PortableProjectBundleService.cs]

The implementation should add one reusable Core helper/seam that prepares request-scoped BodyGen morph state before generation. It should clone or otherwise isolate the working project where caller-visible mutation is forbidden, apply the saved strategy only when BodyGen output is included, block on no-eligible NPCs before writes/zip entries, and then feed the existing `MorphGenerationService` and writers. [VERIFIED: 07-CONTEXT.md; src/BS2BG.Core/Generation/MorphGenerationService.cs]

The main unknown-to-watch is stale in-memory assignment state: tests must deliberately construct projects whose saved assignments are empty or wrong while `AssignmentStrategy` is valid, then assert CLI and bundle `morphs.ini` bytes reflect replayed strategy output. Existing success tests that only prove file existence or already-mutated assignments will not catch the Phase 7 bug. [VERIFIED: 07-CONTEXT.md; tests/BS2BG.Tests/CliGenerationTests.cs; tests/BS2BG.Tests/PortableBundleServiceTests.cs]

**Primary recommendation:** Add a small `AutomationAssignmentStrategyReplayService`-style Core seam that returns a replay result plus a request-scoped project for BodyGen generation; compose it in `HeadlessGenerationService` and `PortableProjectBundleService` before any output write. [VERIFIED: 07-CONTEXT.md]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Decide whether replay is needed | Core automation orchestration | CLI/App callers pass intent | `OutputIntent` already expresses BodyGen/Bos/All and Core services own generation behavior. [VERIFIED: src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs] |
| Apply saved deterministic strategy | Core domain/service layer | — | `MorphAssignmentService.ApplyStrategy` and `AssignmentStrategyService` are Core and preserve the random-provider seam. [VERIFIED: src/BS2BG.Core/Morphs/MorphAssignmentService.cs] |
| Prevent mutation of bundle source project | Core bundling orchestration | ProjectModel clone helper | Bundle service serializes `request.Project` into `project/project.jbs2bg`; generated BodyGen output must use a working model. [VERIFIED: 07-CONTEXT.md; src/BS2BG.Core/Bundling/PortableProjectBundleService.cs] |
| CLI exit code and stdout/stderr | CLI composition | Core result contracts | `Program.WriteResult` maps `HeadlessGenerationResult` to stdout/stderr and process exit code. [VERIFIED: src/BS2BG.Cli/Program.cs] |
| Bundle reports/preview failure text | Core bundling | CLI/App rendering | `PortableProjectBundleResult` and validation/report text are Core-facing stable outcome contracts. [VERIFIED: src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs] |
| Byte output | Existing Core generation/export writers | — | Existing `MorphGenerationService` and INI writer own output text and line endings; Phase 7 should only change input assignments. [VERIFIED: src/BS2BG.Core/Generation/MorphGenerationService.cs; AGENTS.md] |

## Standard Stack

### Core

| Library / Platform | Version | Purpose | Why Standard |
|--------------------|---------|---------|--------------|
| .NET SDK | 10.0.203 | Build/test runtime for app, CLI, and tests | Installed SDK matches project `net10.0` targets. [VERIFIED: dotnet --version; src/BS2BG.Cli/BS2BG.Cli.csproj] |
| C# / netstandard Core | C# 13 / netstandard2.1 | Portable domain, generation, serialization, and automation services | Core remains UI-free and portable. [VERIFIED: src/BS2BG.Core/BS2BG.Core.csproj; AGENTS.md] |
| C# / CLI | C# 14 / net10.0 | `BS2BG.Cli` automation entry point | Existing CLI project is dedicated and references Core only. [VERIFIED: src/BS2BG.Cli/BS2BG.Cli.csproj; tests/BS2BG.Tests/CliGenerationTests.cs] |
| System.CommandLine | 2.0.7 | CLI parsing/actions/exit codes | Existing CLI uses `RootCommand`, `Option<T>`, `SetAction`, `Parse(args).Invoke()`; Microsoft docs define this as the current action invocation pattern. [VERIFIED: dotnet list package; CITED: https://learn.microsoft.com/dotnet/standard/commandline/how-to-parse-and-invoke] |
| System.IO.Compression | BCL / net10.0 | Portable bundle zip creation | Existing bundle service uses `ZipArchive.CreateEntry`; Microsoft docs note entries are relative archive paths and duplicate names are allowed unless caller rejects them. [VERIFIED: src/BS2BG.Core/Bundling/PortableProjectBundleService.cs; CITED: https://learn.microsoft.com/en-us/dotnet/api/system.io.compression.ziparchive.createentry] |
| System.Text.Json | 10.0.7 | Project/profile/manifest JSON | Existing project and bundle serializers use it. [VERIFIED: dotnet list package; src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| xUnit v3 | 3.2.2 | Unit/integration test framework | Add service-level, CLI `Program.Main`, and bundle tests. [VERIFIED: dotnet list package; tests/BS2BG.Tests/BS2BG.Tests.csproj] |
| FluentAssertions | 8.9.0 | Readable assertions and byte/collection equality | Use `Should().Equal(...)`, `Should().Contain(...)`, `Should().Be(...)`; Context7 docs show collection equality APIs. [VERIFIED: dotnet list package; CITED: /fluentassertions/fluentassertions] |
| Microsoft.NET.Test.Sdk | 18.4.0 | Test discovery/execution | Existing test project uses it. [VERIFIED: dotnet list package] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Reusable Core replay seam | Inline replay separately in CLI and bundle services | Inline replay risks divergent blocking, summary, and mutation semantics across automation paths. [VERIFIED: 07-CONTEXT.md] |
| `MorphAssignmentService.ApplyStrategy` | Direct `AssignmentStrategyService.Apply` | Direct static application can satisfy seeded strategies, but the locked decision says not to bypass the existing random-provider abstraction; `MorphAssignmentService` is the existing provider-compatible seam. [VERIFIED: 07-CONTEXT.md; src/BS2BG.Core/Morphs/MorphAssignmentService.cs] |
| Working `ProjectModel` clone for bundle output | Mutate `request.Project` then serialize | Mutating request project would violate D-04/D-05 and could serialize replay side effects into `project/project.jbs2bg`. [VERIFIED: 07-CONTEXT.md] |
| Existing writers | Custom `morphs.ini` text builder | Custom output would bypass byte-sensitive writer/generation contracts. [VERIFIED: AGENTS.md; src/BS2BG.Core/Generation/MorphGenerationService.cs] |

**Installation:** No new packages are recommended. [VERIFIED: dotnet list package]

```powershell
dotnet restore BS2BG.sln
```

**Version verification:** Package versions were verified with `dotnet list "BS2BG.sln" package`; project target versions were verified from `*.csproj` and `Directory.Packages.props`. [VERIFIED: dotnet list package; Directory.Packages.props]

## Architecture Patterns

### System Architecture Diagram

```text
Saved .jbs2bg project / in-memory bundle project
        |
        v
Build request-scoped profile catalog (existing Phase 6 pattern)
        |
        v
Validate project + unresolved custom profiles
        |
        v
Does request include BodyGen? ---- no ----> Generate BoS/project bundle without replay
        |
       yes
        |
        v
Is ProjectModel.AssignmentStrategy present? ---- no ----> Use existing assignments
        |
       yes
        |
        v
Create request-scoped working ProjectModel when mutation must not leak
        |
        v
Apply strategy through MorphAssignmentService.ApplyStrategy
        |
        v
Any blocked NPC rows? ---- yes ----> Return ValidationBlocked/MissingProfile-style blocker before writes
        |
       no
        |
        v
Generate templates.ini + morphs.ini through existing services/writers
        |
        v
CLI files / bundle zip entries + concise replay summary
```

### Recommended Project Structure

```text
src/BS2BG.Core/
├── Automation/                 # Headless request/result contracts and CLI-facing generation orchestration
│   └── AssignmentStrategyReplayService.cs  # recommended new reusable Core seam
├── Bundling/                   # Portable bundle planning/zip creation, consumes same replay seam
├── Morphs/                     # Existing strategy execution and provider-compatible assignment seam
└── Models/                     # Existing ProjectModel clone/ReplaceWith support

tests/BS2BG.Tests/
├── AssignmentStrategyReplayServiceTests.cs # direct replay helper edge cases
├── CliGenerationTests.cs                   # CLI output/replay/exit-code regressions
└── PortableBundleServiceTests.cs           # bundle output/project-state/replay regressions
```

### Pattern 1: Request-Scoped Working State

**What:** Build a working `ProjectModel` for replay and output while preserving source project state where required. [VERIFIED: 07-CONTEXT.md; src/BS2BG.Core/Models/ProjectModel.cs]  
**When to use:** Always for bundle BodyGen generation; optionally for CLI if a single shared helper simplifies behavior and avoids dirty/change-version side effects. [VERIFIED: 07-CONTEXT.md]  
**Example:**

```csharp
// Source: ProjectModel.ReplaceWith clone behavior in src/BS2BG.Core/Models/ProjectModel.cs
var workingProject = new ProjectModel();
workingProject.ReplaceWith(sourceProject);
var replay = replayService.PrepareForBodyGen(workingProject, OutputIntent.BodyGen);
if (replay.BlockedNpcs.Count > 0) return BlockedBeforeWrites(replay);
var morphsText = morphGenerationService.GenerateMorphs(replay.Project).Text;
```

### Pattern 2: Provider-Compatible Strategy Replay

**What:** Apply the saved strategy through `MorphAssignmentService.ApplyStrategy`, which delegates to `AssignmentStrategyService` with the configured `IRandomAssignmentProvider`. [VERIFIED: src/BS2BG.Core/Morphs/MorphAssignmentService.cs]  
**When to use:** Any automation replay path that must not bypass the random-provider seam. [VERIFIED: 07-CONTEXT.md]

```csharp
// Source: src/BS2BG.Core/Morphs/MorphAssignmentService.cs
var result = morphAssignmentService.ApplyStrategy(workingProject, workingProject.AssignmentStrategy);
if (result.BlockedNpcs.Count != 0)
{
    return AssignmentStrategyReplayResult.Blocked(
        workingProject,
        result.AssignedCount,
        result.BlockedNpcs);
}
```

### Pattern 3: Validation-Then-Overwrite-Then-Write Gate Ordering

**What:** Detect replay blockers before overwrite preflight and before any write/zip-entry creation, mirroring existing validation and missing-profile blockers. [VERIFIED: src/BS2BG.Core/Automation/HeadlessGenerationService.cs; src/BS2BG.Core/Bundling/PortableProjectBundleService.cs]  
**When to use:** CLI BodyGen/all and bundle BodyGen/all. [VERIFIED: 07-CONTEXT.md]

```csharp
// Source: HeadlessGenerationService already blocks validation before PlanTargets/write.
var replay = replayService.PrepareForBodyGen(project, request.Intent);
if (replay.BlockedNpcs.Count > 0)
    return new HeadlessGenerationResult(
        AutomationExitCode.ValidationBlocked,
        FormatReplayBlockedMessage(replay),
        Array.Empty<string>(),
        validationReport);
```

### Pattern 4: Direct Helper Tests Before Integration Tests

**What:** First cover replay decisions and blocked details on the helper, then cover CLI/bundle output bytes. [VERIFIED: .planning/phases/06-compose-custom-profiles-in-headless-generation/06-LEARNINGS.md]  
**When to use:** The helper has stateful branching (intent, strategy present/absent, mutation isolation, blocked NPCs). [VERIFIED: 07-CONTEXT.md]

### Anti-Patterns to Avoid

- **Replaying in `Program.cs`:** CLI parsing should compose Core services and print results; generation decisions belong in Core. [VERIFIED: src/BS2BG.Cli/Program.cs]
- **Applying replay after `morphs.ini` generation:** This preserves the bug because output text has already been produced from stale assignments. [VERIFIED: src/BS2BG.Core/Automation/HeadlessGenerationService.cs]
- **Mutating bundle `request.Project`:** This violates D-04/D-05 and can dirty/change caller state or serialize replayed assignments. [VERIFIED: 07-CONTEXT.md]
- **Treating `ProjectValidationService` caution as enough:** Existing no-eligible strategy findings are Caution for GUI/plain validation; Phase 7 automation must elevate replay blockers for BodyGen output. [VERIFIED: src/BS2BG.Core/Diagnostics/ProjectValidationService.cs; 07-CONTEXT.md]
- **Adding a new random algorithm:** Existing deterministic provider vectors are pinned by tests; do not use `new Random(seed)` or custom RNG in the automation seam. [VERIFIED: tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Strategy eligibility and assignment | New race/weight/bucket evaluator | `MorphAssignmentService.ApplyStrategy` / `AssignmentStrategyService.ComputeEligibility` | Existing service implements stable NPC ordering, rule semantics, seeded provider behavior, and blocked rows. [VERIFIED: src/BS2BG.Core/Morphs/AssignmentStrategyService.cs] |
| Random replay | `System.Random(seed)` or ad-hoc PRNG | Existing `DeterministicAssignmentRandomProvider` via assignment service | Tests pin deterministic provider vectors and forbid `new Random(seed)`. [VERIFIED: tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs] |
| `morphs.ini` formatting | String-building in replay helper | `MorphGenerationService.GenerateMorphs` + `BodyGenIniExportWriter` | Byte-sensitive output is already centralized. [VERIFIED: AGENTS.md; src/BS2BG.Core/Generation/MorphGenerationService.cs] |
| Bundle zip path validation | Raw `ZipArchive.CreateEntry` paths | Existing `BundlePathScrubber.NormalizeEntryPath` and duplicate rejection | .NET allows duplicate entry names and non-relative-looking strings can create extraction issues; existing scrubber prevents bundle path bugs. [CITED: https://learn.microsoft.com/en-us/dotnet/api/system.io.compression.ziparchive.createentry; VERIFIED: src/BS2BG.Core/Bundling/BundlePathScrubber.cs] |
| Atomic bundle commit | Direct overwrite writes | Existing temp zip + `File.Replace`/`File.Move` pattern | Microsoft docs define `File.Replace` as replacing destination contents with source contents; existing tests protect final-commit failure behavior. [CITED: https://learn.microsoft.com/en-us/dotnet/api/system.io.file.replace; VERIFIED: tests/BS2BG.Tests/PortableBundleServiceTests.cs] |
| CLI parsing/exit code plumbing | Manual arg parser | Existing System.CommandLine command tree | Docs show `Parse(args).Invoke()` returns action exit codes and parse errors return nonzero. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/how-to-parse-and-invoke] |

**Key insight:** The risky complexity is not assignment math itself; it is orchestration ordering and state ownership. Reuse existing assignment/generation/writer services and make the replay seam responsible only for “which project state is safe to generate from?” [VERIFIED: 07-CONTEXT.md]

## Common Pitfalls

### Pitfall 1: Replaying Into the Source Bundle Project
**What goes wrong:** `project/project.jbs2bg` in the zip contains replayed assignments instead of the original saved state. [VERIFIED: 07-CONTEXT.md]  
**Why it happens:** `PortableProjectBundleService` currently serializes `request.Project` and generates outputs from `request.Project` in one plan. [VERIFIED: src/BS2BG.Core/Bundling/PortableProjectBundleService.cs]  
**How to avoid:** Serialize the original project entry before or independently from generating BodyGen output from a clone. [VERIFIED: 07-CONTEXT.md]  
**Warning signs:** A bundle regression test loads `project/project.jbs2bg` from the zip and finds NPC assignments added/changed by replay.

### Pitfall 2: Blocked Strategy Replay Still Writes Old Assignments
**What goes wrong:** An NPC with no eligible preset leaves stale prior assignments in `morphs.ini`. [VERIFIED: 07-CONTEXT.md]  
**Why it happens:** `AssignmentStrategyService.Apply` skips blocked rows; if the row had a stale assignment before replay, it can remain unless automation treats blocked rows as fatal before output. [VERIFIED: src/BS2BG.Core/Morphs/AssignmentStrategyService.cs]  
**How to avoid:** If `BlockedNpcs.Count > 0`, return a validation-blocked outcome before writer calls; do not attempt partial cleanup/fallback. [VERIFIED: 07-CONTEXT.md]  
**Warning signs:** Tests show `morphs.ini` includes a preset for a blocked row or output files exist after blocked replay.

### Pitfall 3: BoS-Only Path Accidentally Replays
**What goes wrong:** `--intent bos` or bundle BoS-only mutates project assignment state despite not using morphs. [VERIFIED: 07-CONTEXT.md]  
**Why it happens:** Replay helper is called unconditionally instead of behind `OutputIntent.BodyGen`/`All`. [VERIFIED: src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs]  
**How to avoid:** Centralize an `IncludesBodyGen(OutputIntent)` check inside the replay seam or at its only call sites. [VERIFIED: src/BS2BG.Core/Automation/HeadlessGenerationService.cs]

### Pitfall 4: Summary Text Breaks Script Expectations
**What goes wrong:** CLI success output becomes verbose per-NPC output or failure returns success with text-only warnings. [VERIFIED: 07-CONTEXT.md]  
**Why it happens:** Replay visibility is mixed with diagnostics/report formatting without stable exit-code mapping. [VERIFIED: src/BS2BG.Cli/Program.cs]  
**How to avoid:** Keep success as concise counts in result messages; map blockers to `AutomationExitCode.ValidationBlocked` / `PortableProjectBundleOutcome.ValidationBlocked`. [VERIFIED: 07-CONTEXT.md; src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs]

### Pitfall 5: Integration Tests Use Already-Correct Assignments
**What goes wrong:** Tests pass even if replay is never called. [VERIFIED: 07-CONTEXT.md]  
**Why it happens:** Existing CLI/bundle tests often construct projects with assigned custom targets or matching assignments. [VERIFIED: tests/BS2BG.Tests/CliGenerationTests.cs; tests/BS2BG.Tests/PortableBundleServiceTests.cs]  
**How to avoid:** Build fixtures where saved NPC assignments are empty/wrong and only `AssignmentStrategy` can produce expected `morphs.ini`. [VERIFIED: 07-CONTEXT.md]

## Code Examples

Verified patterns from project and official sources:

### Reusable Replay Result Contract

```csharp
// Source: follows existing result-record style in HeadlessGenerationContracts.cs and PortableProjectBundleContracts.cs.
public sealed record AssignmentStrategyReplayResult(
    ProjectModel Project,
    bool Replayed,
    AssignmentStrategyKind? StrategyKind,
    int AssignedCount,
    IReadOnlyList<AssignmentStrategyBlockedNpc> BlockedNpcs)
{
    public bool IsBlocked => BlockedNpcs.Count > 0;
}
```

### Core Replay Helper Shape

```csharp
// Source: uses MorphAssignmentService.ApplyStrategy from src/BS2BG.Core/Morphs/MorphAssignmentService.cs.
public AssignmentStrategyReplayResult PrepareForBodyGen(ProjectModel sourceProject, OutputIntent intent, bool cloneBeforeReplay)
{
    if (sourceProject is null) throw new ArgumentNullException(nameof(sourceProject));

    var project = cloneBeforeReplay ? CloneProject(sourceProject) : sourceProject;
    if (intent is not (OutputIntent.BodyGen or OutputIntent.All) || project.AssignmentStrategy is null)
    {
        return new AssignmentStrategyReplayResult(project, false, null, 0, Array.Empty<AssignmentStrategyBlockedNpc>());
    }

    var result = morphAssignmentService.ApplyStrategy(project, project.AssignmentStrategy);
    return new AssignmentStrategyReplayResult(project, true, project.AssignmentStrategy.Kind, result.AssignedCount, result.BlockedNpcs);
}
```

### CLI Test Fixture Must Prove Replay, Not Existing Assignment

```csharp
// Source: test style from tests/BS2BG.Tests/CliGenerationTests.cs and FluentAssertions collection docs.
var project = CreateStrategyProjectWithWrongExistingNpcAssignments();
project.AssignmentStrategy = new AssignmentStrategyDefinition(
    1,
    AssignmentStrategyKind.RoundRobin,
    null,
    Array.Empty<AssignmentStrategyRule>());
var projectPath = SaveProject(directory.Path, project);

var result = CreateHeadlessService().Run(new HeadlessGenerationRequest(
    projectPath,
    outputDirectory,
    OutputIntent.BodyGen,
    Overwrite: false,
    OmitRedundantSliders: false));

result.ExitCode.Should().Be(AutomationExitCode.Success);
File.ReadAllText(Path.Combine(outputDirectory, "morphs.ini"))
    .Should().Contain("Aela=PresetB").And.NotContain("StalePreset");
```

### Bundle Test Must Assert Project Entry Stayed Original

```csharp
// Source: Zip archive assertions follow tests/BS2BG.Tests/PortableBundleServiceTests.cs.
using var archive = ZipFile.OpenRead(bundlePath);
var bundledProjectJson = ReadEntryText(archive, "project/project.jbs2bg");
bundledProjectJson.Should().Contain("AssignmentStrategy");
bundledProjectJson.Should().Contain("StalePreset", "bundle project state stays as saved source data");
ReadEntryText(archive, "outputs/bodygen/morphs.ini")
    .Should().NotContain("StalePreset", "generated output uses request-scoped replay state");
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| CLI/bundle generated from already-mutated in-memory assignments | Automation should reconstruct deterministic assignments from saved `AssignmentStrategy` before BodyGen output | Phase 7 context gathered 2026-04-28 [VERIFIED: 07-CONTEXT.md] | Saved projects become reproducible automation inputs instead of relying on GUI/session mutation history. |
| GUI/plain validation emits no-eligible strategy rows as caution | BodyGen automation treats blocked replay as fatal before writes | Phase 7 locked D-06 [VERIFIED: 07-CONTEXT.md] | Prevents stale or partial `morphs.ini` output. |
| Bundle output and project entry both sourced directly from `request.Project` | Bundle project entry remains source state; generated BodyGen uses working replay state | Phase 7 locked D-04/D-05 [VERIFIED: 07-CONTEXT.md] | Bundles can show reproducible outputs without rewriting shared project data. |
| Hand-coded CLI handlers/old `Command.Handler` style | `System.CommandLine` `SetAction(ParseResult => int)` and `Parse(args).Invoke()` | System.CommandLine 2.0 docs [CITED: https://learn.microsoft.com/dotnet/standard/commandline/how-to-parse-and-invoke] | Existing CLI code is already aligned; no parser refactor needed. |

**Deprecated/outdated:**
- Do not add an opt-in replay flag; D-02 explicitly rejects it. [VERIFIED: 07-CONTEXT.md]
- Do not add richer strategy types, weighted group improvements, or plugin/xEdit race lookup; Phase 7 scope excludes new strategy semantics. [VERIFIED: 07-CONTEXT.md; .planning/REQUIREMENTS.md]
- Do not update golden expected fixtures for this phase; expected fixture regeneration is explicitly out of scope for hiding failures. [VERIFIED: AGENTS.md]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Always use the helper result's `Project` and clone for both CLI and bundle unless performance tests show a problem; this preserves a single mental model. | Open Questions | Extra cloning could be unnecessary overhead or could hide intended CLI loaded-project mutation semantics. |
| A2 | Add replay summary to result/preview-facing report text only; do not alter manifest schema unless a spec explicitly requires it. | Open Questions | Users or tests may expect summary details in a different surface such as manifest metadata or CLI stdout. |

These assumptions are recommendations for planner consideration, not locked facts.

## Open Questions

1. **Should CLI replay mutate the loaded project instance or always use a clone?**
   - What we know: Bundle must not mutate the caller/source project; CLI loads a fresh project per request, so mutation is less risky. [VERIFIED: 07-CONTEXT.md; src/BS2BG.Core/Automation/HeadlessGenerationService.cs]
   - What's unclear: Whether the planner prefers one helper mode (`cloneBeforeReplay`) or always-clone for simpler invariants.
   - Recommendation: Always use the helper result's `Project` and clone for both CLI and bundle unless performance tests show a problem; this preserves a single mental model. [ASSUMED]
2. **Where should replay summary live in bundle artifacts?**
   - What we know: D-08 requires concise summary counts and D-09 requires failure details in bundle preview/report text. [VERIFIED: 07-CONTEXT.md]
   - What's unclear: Whether success summary should be added to validation report text, manifest metadata, CLI stdout only, or a new report line.
   - Recommendation: Add summary to result/preview-facing report text only; do not alter manifest schema unless a spec explicitly requires it. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| .NET SDK | Build/test/CLI execution | ✓ | 10.0.203 | None needed. [VERIFIED: dotnet --version] |
| NuGet restore via .NET SDK | Package resolution | ✓ | Project restored successfully during `dotnet list package` | None needed. [VERIFIED: dotnet list package] |
| PowerShell | Windows command execution | ✓ | Tool shell is PowerShell (`pwsh`) | None needed. [VERIFIED: execution environment] |

**Missing dependencies with no fallback:** None. [VERIFIED: dotnet --version; dotnet list package]

**Missing dependencies with fallback:** None. [VERIFIED: dotnet --version; dotnet list package]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | xUnit v3 3.2.2 + FluentAssertions 8.9.0 [VERIFIED: dotnet list package] |
| Config file | `tests/BS2BG.Tests/BS2BG.Tests.csproj` [VERIFIED: tests/BS2BG.Tests/BS2BG.Tests.csproj] |
| Quick run command | `dotnet test --filter "FullyQualifiedName~AssignmentStrategyReplayServiceTests|FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"` |
| Full suite command | `dotnet test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| AUTO-03 | Core seam replays saved strategy deterministically through provider abstraction and blocks no-eligible rows | unit | `dotnet test --filter FullyQualifiedName~AssignmentStrategyReplayServiceTests` | ❌ Wave 0 |
| AUTO-03 | CLI BodyGen/all replay saved strategy before `morphs.ini` write and BoS-only does not replay | integration | `dotnet test --filter FullyQualifiedName~CliGenerationTests` | ✅ existing class; new tests needed |
| AUTO-02 | Bundle BodyGen/all replay saved strategy for generated output while `project/project.jbs2bg` remains original | integration | `dotnet test --filter FullyQualifiedName~PortableBundleServiceTests` | ✅ existing class; new tests needed |
| AUTO-02/AUTO-03 | Blocked replay fails before output files/zip entries and reports blocked NPC details | integration | `dotnet test --filter "FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"` | ✅ existing classes; new tests needed |

### Sampling Rate
- **Per task commit:** `dotnet test --filter "FullyQualifiedName~AssignmentStrategyReplayServiceTests|FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"`
- **Per wave merge:** `dotnet test`
- **Phase gate:** Full suite green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `tests/BS2BG.Tests/AssignmentStrategyReplayServiceTests.cs` — covers direct replay decision matrix and clone/no-mutation behavior for AUTO-03.
- [ ] Add CLI replay regressions to `tests/BS2BG.Tests/CliGenerationTests.cs` — covers stale/empty assignments and blocked replay.
- [ ] Add bundle replay regressions to `tests/BS2BG.Tests/PortableBundleServiceTests.cs` — covers generated bytes, unchanged project entry, and blocked zip creation.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | Offline local CLI/desktop utility has no auth surface. [VERIFIED: .planning/codebase/ARCHITECTURE.md] |
| V3 Session Management | no | No sessions or accounts. [VERIFIED: .planning/PROJECT.md] |
| V4 Access Control | no | Local file operations only; preserve existing overwrite and path-scrubbing gates. [VERIFIED: src/BS2BG.Core/Bundling/BundlePathScrubber.cs] |
| V5 Input Validation | yes | Reuse project load diagnostics, strategy validation, bundle path normalization, and existing result codes. [VERIFIED: src/BS2BG.Core/Serialization/ProjectFileService.cs; src/BS2BG.Core/Bundling/BundlePathScrubber.cs] |
| V6 Cryptography | yes (checksums only) | Existing SHA-256 manifest/checksum code; do not add custom crypto. [VERIFIED: src/BS2BG.Core/Bundling/PortableProjectBundleService.cs] |

### Known Threat Patterns for local automation/bundles

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Private local path leakage in bundle report/manifest | Information Disclosure | Continue using `BundlePathScrubber` on report/manifest surfaces. [VERIFIED: src/BS2BG.Core/Bundling/BundlePathScrubber.cs] |
| Zip entry traversal/absolute path entries | Tampering | Continue normalizing/rejecting bundle entry paths before `ZipArchive.CreateEntry`. [VERIFIED: src/BS2BG.Core/Bundling/BundlePathScrubber.cs; CITED: https://learn.microsoft.com/en-us/dotnet/api/system.io.compression.ziparchive.createentry] |
| Stale morph assignments emitted after blocked replay | Tampering | Treat blocked replay as fatal before writes/entries. [VERIFIED: 07-CONTEXT.md] |
| Partial overwrite on failure | Tampering/Denial of Service | Preserve existing temp zip + final commit and Core atomic writer patterns. [VERIFIED: src/BS2BG.Core/Bundling/PortableProjectBundleService.cs; src/BS2BG.Core/IO/AtomicFileWriter.cs] |

## Sources

### Primary (HIGH confidence)
- `.planning/phases/07-replay-saved-strategies-in-automation-outputs/07-CONTEXT.md` — locked Phase 7 scope, trigger, bundle-state, blocker, and visibility decisions.
- `.planning/REQUIREMENTS.md` — AUTO-02 and AUTO-03 requirement mapping.
- `.planning/ROADMAP.md` — Phase 7 goal and success criteria.
- `AGENTS.md` — project stack, sacred files, test/build rules, Core/App boundaries.
- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` — current CLI/headless orchestration and write order.
- `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` — current bundle plan, project serialization, output entry generation, zip creation.
- `src/BS2BG.Core/Morphs/AssignmentStrategyService.cs` and `MorphAssignmentService.cs` — deterministic replay semantics and provider seam.
- `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs`, `CliGenerationTests.cs`, `PortableBundleServiceTests.cs` — current regression patterns and gaps.
- `dotnet list "BS2BG.sln" package` — package versions and resolved dependencies.
- Microsoft Learn System.CommandLine parsing/invocation docs — https://learn.microsoft.com/dotnet/standard/commandline/how-to-parse-and-invoke
- Microsoft Learn ZipArchive.CreateEntry docs — https://learn.microsoft.com/en-us/dotnet/api/system.io.compression.ziparchive.createentry
- Microsoft Learn File.Replace docs — https://learn.microsoft.com/en-us/dotnet/api/system.io.file.replace
- Context7 `/fluentassertions/fluentassertions` — collection equality/assertion examples.

### Secondary (MEDIUM confidence)
- `.planning/phases/06-compose-custom-profiles-in-headless-generation/06-LEARNINGS.md` — prior automation pattern lessons for request-scoped state and direct tests before integration tests.
- `.planning/codebase/STACK.md`, `ARCHITECTURE.md`, `CONVENTIONS.md` — codebase map generated 2026-04-26 and verified against current files where relevant.

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — package versions were verified via `dotnet list package`, project files, and official docs where applicable.
- Architecture: HIGH — key services and tests were read directly, and Phase 7 decisions are explicit.
- Pitfalls: HIGH — pitfalls are derived from existing code paths plus locked phase decisions.
- External ecosystem/SOTA: MEDIUM — minimal external ecosystem is involved; official Microsoft docs verify CLI/zip/file APIs, but the phase is primarily codebase-specific.

**Research date:** 2026-04-28  
**Valid until:** 2026-05-28 for project-specific architecture; re-check package/API docs if System.CommandLine or .NET SDK versions change.
