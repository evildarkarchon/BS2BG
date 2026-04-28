---
phase: 05-automation-sharing-and-release-trust
reviewed: 2026-04-28T05:08:00Z
depth: standard
files_reviewed: 45
files_reviewed_list:
  - BS2BG.sln
  - Directory.Packages.props
  - docs/release/BODYGEN-BODYSLIDE-BOS-SETUP.md
  - docs/release/QA-CHECKLIST.md
  - docs/release/README.md
  - docs/release/UNSIGNED-BUILD.md
  - src/BS2BG.App/AppBootstrapper.cs
  - src/BS2BG.App/Services/AssignmentStrategyKindDisplayConverter.cs
  - src/BS2BG.App/Services/IFileDialogService.cs
  - src/BS2BG.App/Services/WindowFileDialogService.cs
  - src/BS2BG.App/ViewModels/AssignmentStrategyRuleRowViewModel.cs
  - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
  - src/BS2BG.App/ViewModels/MorphsViewModel.cs
  - src/BS2BG.App/Views/MainWindow.axaml
  - src/BS2BG.Cli/BS2BG.Cli.csproj
  - src/BS2BG.Cli/Program.cs
  - src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs
  - src/BS2BG.Core/Automation/HeadlessGenerationService.cs
  - src/BS2BG.Core/Bundling/BundlePathScrubber.cs
  - src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs
  - src/BS2BG.Core/Bundling/PortableProjectBundleService.cs
  - src/BS2BG.Core/Diagnostics/DiagnosticReportTextFormatter.cs
  - src/BS2BG.Core/Diagnostics/ProjectValidationService.cs
  - src/BS2BG.Core/Export/BosJsonExportPlanner.cs
  - src/BS2BG.Core/Export/BosJsonExportWriter.cs
  - src/BS2BG.Core/Generation/TemplateProfileCatalogFactory.cs
  - src/BS2BG.Core/Models/ProjectModel.cs
  - src/BS2BG.Core/Morphs/AssignmentStrategyContracts.cs
  - src/BS2BG.Core/Morphs/AssignmentStrategyService.cs
  - src/BS2BG.Core/Morphs/DeterministicAssignmentRandomProvider.cs
  - src/BS2BG.Core/Morphs/MorphAssignmentService.cs
  - src/BS2BG.Core/Serialization/ProjectFileService.cs
  - tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs
  - tests/BS2BG.Tests/BS2BG.Tests.csproj
  - tests/BS2BG.Tests/CliGenerationTests.cs
  - tests/BS2BG.Tests/M6UxViewModelTests.cs
  - tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs
  - tests/BS2BG.Tests/MainWindowViewModelTests.cs
  - tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs
  - tests/BS2BG.Tests/PortableBundleServiceTests.cs
  - tests/BS2BG.Tests/ReleaseDocsTests.cs
  - tests/BS2BG.Tests/ReleaseTrustTests.cs
  - tests/BS2BG.Tests/TemplateGenerationServiceTests.cs
  - tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs
  - tools/release/package-release.ps1
findings:
  critical: 2
  warning: 1
  info: 0
  total: 3
status: issues_found
---

# Phase 05: Code Review Report

**Reviewed:** 2026-04-28T05:08:00Z
**Depth:** standard
**Files Reviewed:** 45
**Status:** issues_found

## Summary

Reviewed the Phase 05 automation, bundling, strategy, release-trust, and documentation changes against the phase plans and requirements. The implementation has two release-blocking correctness/data-loss defects in portable bundle creation and one CLI robustness gap that should be fixed before trusting the new automation path.

## Critical Issues

### CR-01: Existing bundle zip is deleted before replacement succeeds

**File:** `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:107-114`

**Issue:** `Create` deletes an existing bundle when `Overwrite` is true and then creates the replacement directly at the final path. If `File.Create`, zip entry writes, disk space, antivirus locks, or any later archive operation fails, the catch block deletes the partial zip and returns `IoFailure`, but the user's previous bundle is already gone. This is a data-loss risk in the explicit overwrite path.

**Fix:** Write the archive to a temp file in the destination directory, then atomically replace/move it only after the zip is fully closed and validated. Keep the old file until the final commit step succeeds.

```csharp
var finalPath = Path.GetFullPath(request.BundlePath);
var parent = Path.GetDirectoryName(finalPath)!;
Directory.CreateDirectory(parent);
var tempPath = Path.Combine(parent, "." + Path.GetFileName(finalPath) + "." + Guid.NewGuid().ToString("N") + ".tmp");
try
{
    using (var zipStream = File.Create(tempPath))
    using (var archive = new ZipArchive(zipStream, ZipArchiveMode.Create))
    {
        // write entries to tempPath
    }

    if (File.Exists(finalPath))
        File.Replace(tempPath, finalPath, destinationBackupFileName: null);
    else
        File.Move(tempPath, finalPath);
}
finally
{
    if (File.Exists(tempPath)) TryDeleteFile(tempPath);
}
```

### CR-02: Bundle generation ignores embedded/local custom profiles when producing output bytes

**File:** `src/BS2BG.Cli/Program.cs:181-190`, `src/BS2BG.App/AppBootstrapper.cs:45-52`, `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:28-29, 221-231`

**Issue:** `PortableProjectBundleService` captures a single `TemplateProfileCatalog` in its constructor and uses that catalog for validation/generation. The CLI constructs it from `new TemplateProfileCatalogFactory().Create()`, which contains only bundled profiles; the App registers the service with `ITemplateProfileCatalogService.Current` at startup, before later project-profile overlays or custom profile changes. However bundle creation separately resolves and includes referenced custom profiles in `profiles/`. For projects whose presets use those custom profiles, the bundled `templates.ini` and BoS JSON are generated through `profileCatalog.GetProfile(...)` fallback instead of the referenced custom profile. The zip can therefore include the correct profile JSON but incorrect generated outputs.

**Fix:** Make bundle planning build a request-scoped generation catalog that includes bundled profiles plus referenced embedded/project/local custom profiles from `request.Project.CustomProfiles` and `request.SaveContext`, and pass that catalog to validation, `GenerateTemplates`, and `BosJsonExportWriter`. In the App, avoid registering `PortableProjectBundleService` with a stale catalog snapshot; inject a catalog provider or construct the request-scoped catalog at preview/create time.

## Warnings

### WR-01: CLI bundle command can throw unhandled exceptions instead of returning stable automation exit codes

**File:** `src/BS2BG.Cli/Program.cs:115-118, 179-183`

**Issue:** The `bundle` action calls `CreateBundleServiceAndRequest`, `Preview`, and `Create` without the expected user-input error handling used by `HeadlessGenerationService.Run`. Missing project files, malformed `.jbs2bg` JSON, unreadable files, or missing install-relative profile JSON can throw before a `PortableProjectBundleOutcome` is produced, bypassing the documented `0/2/3/4` automation contract and emitting an implementation exception path.

**Fix:** Wrap project loading/service composition in the bundle command or move it behind a Core automation service that returns `AutomationExitCode.UsageError`, `ValidationBlocked`, `OverwriteRefused`, or `IoFailure` consistently.

---

_Reviewed: 2026-04-28T05:08:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
