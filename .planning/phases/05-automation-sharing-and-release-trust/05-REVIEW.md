---
phase: 05-automation-sharing-and-release-trust
reviewed: 2026-04-28T05:38:30Z
depth: standard
advisory: true
files_reviewed: 3
files_reviewed_list:
  - src/BS2BG.Cli/Program.cs
  - tests/BS2BG.Tests/CliGenerationTests.cs
  - .planning/phases/05-automation-sharing-and-release-trust/05-REVIEW.md
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
---

# Phase 05: Advisory Code Review Report

**Reviewed:** 2026-04-28T05:38:30Z  
**Depth:** standard  
**Files Reviewed:** 3  
**Status:** clean

## Summary

Re-ran the advisory status check for Phase 05 after commit `bf4373d4`, focusing on the prior CR-01 bundle missing-project privacy issue. The CLI now converts expected project-load exceptions to fixed, path-free messages before writing stderr, and the regression test asserts that neither the temporary directory nor the missing project path appears in the missing-project failure output.

No remaining issues were found in the reviewed scope. The prior CR-01 audit trail is preserved below and marked resolved.

## Verification

- Reviewed `src/BS2BG.Cli/Program.cs`: `GetProjectLoadFailureMessage` maps `FileNotFoundException`/`DirectoryNotFoundException` to `The project file was not found.` without using `exception.Message` or `FileName`; malformed JSON and generic read failures are also normalized.
- Reviewed `tests/BS2BG.Tests/CliGenerationTests.cs`: `ProgramMainBundleMapsMissingProjectToUsageErrorWithoutStackTrace` now asserts stderr does not contain `directory.Path` or `missingProjectPath`.
- Ran `dotnet test "tests/BS2BG.Tests/BS2BG.Tests.csproj" --filter "FullyQualifiedName~CliGenerationTests.ProgramMainBundleMapsMissingProjectToUsageErrorWithoutStackTrace"` — passed.

## Resolved Findings

### CR-01: Existing bundle zip is deleted before replacement succeeds — RESOLVED

**File:** `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`

**Original Issue:** Earlier Phase 05 review found that overwrite mode deleted an existing bundle before the replacement archive was fully written, creating a data-loss risk if zip creation or final file creation failed.

**Resolution:** Plan 05-10 now writes the replacement archive to a same-directory temp file and commits only after the archive is closed, using `File.Replace` or `File.Move`. Regression coverage injects a deterministic final-commit failure and verifies the previous bundle bytes remain intact.

**Status:** Resolved in commit `214b7976`.

### CR-02: Bundle generation ignores embedded/local custom profiles when producing output bytes — RESOLVED

**File:** `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`; `src/BS2BG.App/AppBootstrapper.cs`; `src/BS2BG.Cli/Program.cs`

**Original Issue:** Earlier Phase 05 review found that portable bundles could include referenced custom profile JSON files while generating `templates.ini` and BoS JSON through a stale or bundled-only profile catalog.

**Resolution:** Plan 05-10 now resolves the bundle profile set once per request and uses that request-scoped catalog for missing-profile validation, profile zip entries, template generation, and BoS JSON output. The App singleton registration documents that local/project custom profiles are supplied per request through `BuildProjectSaveContext()`.

**Status:** Resolved in commits `214b7976` and `ddb51a22`.

### WR-01: CLI bundle command can throw unhandled exceptions instead of returning stable automation exit codes — RESOLVED

**File:** `src/BS2BG.Cli/Program.cs`; `tests/BS2BG.Tests/CliGenerationTests.cs`

**Original Issue:** Earlier Phase 05 review found that bundle command setup could throw before a `PortableProjectBundleOutcome` was produced, bypassing the documented automation exit-code contract.

**Resolution:** Plan 05-10 wraps expected project-load, malformed JSON, missing profile asset, and filesystem failures into stable `AutomationExitCode.UsageError` or `AutomationExitCode.IoFailure` responses with concise stderr. Follow-up commit `bf4373d4` also ensures missing-project stderr does not leak private local paths.

**Status:** Resolved in commits `ddb51a22` and `bf4373d4`.

### CR-01: CLI bundle missing-project errors leak private local paths — RESOLVED

**File:** `src/BS2BG.Cli/Program.cs:120, 243-263`; `tests/BS2BG.Tests/CliGenerationTests.cs:417-435`

**Original Issue:** The bundle command intentionally mapped missing projects to `AutomationExitCode.UsageError`, but it created `FileNotFoundException` with the full `projectPath` and then printed `exception.Message` verbatim through the project-load failure path. On .NET this message includes the missing file path (for example, `Could not find file 'C:\Users\...\project.jbs2bg'`). That path could land in CI or support logs, violating the Phase 05 trust requirement that bundle workflows avoid private local paths and the 05-10 threat-model goal of concise failure messages without private roots. The earlier tests asserted no stack trace/type names, but did not assert that the user path was absent.

**Resolution:** `Program.cs` now writes only a generic message selected by exception type via `GetProjectLoadFailureMessage`, so the `FileNotFoundException.FileName` and raw exception text are not emitted for missing project files. The missing-project CLI regression test now checks both the temp directory and full missing project path are absent from stderr while preserving the usage-error exit code and no-stack-trace assertions.

**Status:** Resolved in commit `bf4373d4`.

---

_Reviewed: 2026-04-28T05:38:30Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_
