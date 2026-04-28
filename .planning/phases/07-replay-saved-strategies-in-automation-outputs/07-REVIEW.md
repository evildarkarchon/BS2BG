---
phase: 07-replay-saved-strategies-in-automation-outputs
reviewed: 2026-04-28T09:28:29Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - src/BS2BG.App/AppBootstrapper.cs
  - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
  - src/BS2BG.Cli/Program.cs
  - src/BS2BG.Core/Automation/AssignmentStrategyReplayContracts.cs
  - src/BS2BG.Core/Automation/AssignmentStrategyReplayService.cs
  - src/BS2BG.Core/Automation/HeadlessGenerationService.cs
  - src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs
  - src/BS2BG.Core/Bundling/PortableProjectBundleService.cs
  - src/BS2BG.Core/Morphs/MorphAssignmentService.cs
  - tests/BS2BG.Tests/AssignmentStrategyReplayServiceTests.cs
  - tests/BS2BG.Tests/CliGenerationTests.cs
  - tests/BS2BG.Tests/PortableBundleServiceTests.cs
findings:
  critical: 0
  warning: 2
  info: 0
  total: 2
status: issues_found
---

# Phase 07: Code Review Report

**Reviewed:** 2026-04-28T09:28:29Z
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

Reviewed the replay service, headless generation path, portable bundle path, CLI wiring, GUI bundle integration, and related tests. No source files were modified. The implementation blocks unsafe output when replay leaves NPCs unassigned, but two automation/reporting contract defects remain: BoS-only bundle runs misreport saved strategies as absent, and replay blockers are not represented in the returned validation reports.

## Warnings

### WR-01: BoS-only bundle reports falsely say no saved strategy exists

**File:** `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:380-383`
**Issue:** `FormatReplayReport` receives only `AssignmentStrategyReplayResult`. For `OutputIntent.BosJson`, `AssignmentStrategyReplayService.PrepareForBodyGen` returns `Replayed = false` even when `request.Project.AssignmentStrategy` is present, because BoS output intentionally does not need BodyGen morph replay. The bundle report then says `No saved assignment strategy; generated from existing project assignments.`, which is false for saved-strategy projects. The test at `tests/BS2BG.Tests/PortableBundleServiceTests.cs:562-585` locks in this misleading behavior instead of distinguishing “no strategy” from “strategy skipped because intent excludes BodyGen”.
**Fix:** Pass request context or a replay skip reason into report formatting, and update tests to assert the skipped-intent message.

```csharp
private static string FormatReplayReport(
    AssignmentStrategyReplayResult replayResult,
    PortableProjectBundleRequest request)
{
    if (!replayResult.Replayed)
        return request.Project.AssignmentStrategy is null
            ? "No saved assignment strategy; generated from existing project assignments."
            : "Saved assignment strategy was not replayed because the output intent does not include BodyGen.";

    // existing success/blocked formatting...
}
```

### WR-02: Replay-blocked automation results return validation reports with no replay blocker

**File:** `src/BS2BG.Core/Automation/HeadlessGenerationService.cs:66-71`, `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:209-218`
**Issue:** When replay blocks generation because NPCs have no eligible preset, both services return a validation-blocked outcome but populate `ValidationReport` by calling `ProjectValidationService.Validate(...)`. That validator does not know about assignment-strategy replay blockers, so API consumers can receive `AutomationExitCode.ValidationBlocked` / `PortableProjectBundleOutcome.ValidationBlocked` with a validation report that has `BlockerCount == 0`. CLI text happens to include replay details, but callers that rely on the structured report can misclassify the project as validation-clean.
**Fix:** Convert replay blockers into structured `DiagnosticFinding` blocker entries or extend the result contract with a structured replay report, and add tests asserting blocked replay results expose `ValidationReport.BlockerCount > 0` (or the new structured replay blocker collection).

```csharp
private static ProjectValidationReport CreateReplayBlockedReport(AssignmentStrategyReplayResult replayResult)
{
    return new ProjectValidationReport(replayResult.BlockedNpcs.Select(blocked =>
        new DiagnosticFinding(
            DiagnosticSeverity.Blocker,
            "Assignment strategy replay",
            "NPC has no eligible preset",
            "NPC '" + blocked.Npc.Name + "' cannot be assigned: " + blocked.Reason))
        .ToArray());
}
```

---

_Reviewed: 2026-04-28T09:28:29Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
