---
phase: 03-validation-and-diagnostics
reviewed: 2026-04-27T04:54:17Z
depth: standard
files_reviewed: 28
files_reviewed_list:
  - src/BS2BG.Core/Diagnostics/DiagnosticSeverity.cs
  - src/BS2BG.Core/Diagnostics/DiagnosticFinding.cs
  - src/BS2BG.Core/Diagnostics/ProjectValidationReport.cs
  - src/BS2BG.Core/Diagnostics/ProjectValidationService.cs
  - src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs
  - src/BS2BG.Core/Diagnostics/ExportPreviewService.cs
  - src/BS2BG.Core/Diagnostics/ExportPreviewResult.cs
  - src/BS2BG.Core/Import/NpcImportResult.cs
  - src/BS2BG.Core/Import/NpcTextParser.cs
  - src/BS2BG.Core/Import/NpcImportPreviewResult.cs
  - src/BS2BG.Core/Import/NpcImportPreviewService.cs
  - src/BS2BG.Core/IO/WriteOutcomeLedger.cs
  - src/BS2BG.Core/IO/AtomicWriteException.cs
  - src/BS2BG.Core/IO/AtomicFileWriter.cs
  - src/BS2BG.Core/Serialization/ProjectFileService.cs
  - src/BS2BG.Core/Export/BodyGenIniExportWriter.cs
  - src/BS2BG.Core/Export/BosJsonExportWriter.cs
  - src/BS2BG.Core/Formatting/SliderProfile.cs
  - src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs
  - src/BS2BG.App/ViewModels/DiagnosticFindingViewModel.cs
  - src/BS2BG.App/ViewModels/MorphsViewModel.cs
  - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
  - src/BS2BG.App/ViewModels/Workflow/NpcImportPreviewViewModel.cs
  - src/BS2BG.App/ViewModels/Workflow/ExportPreviewViewModel.cs
  - src/BS2BG.App/ViewModels/Workflow/FileOperationLedgerViewModel.cs
  - src/BS2BG.App/Services/DiagnosticsReportFormatter.cs
  - src/BS2BG.App/Services/IAppDialogService.cs
  - src/BS2BG.App/Services/WindowAppDialogService.cs
  - src/BS2BG.App/AppBootstrapper.cs
  - src/BS2BG.App/Views/MainWindow.axaml
  - src/BS2BG.App/Views/MainWindow.axaml.cs
findings:
  critical: 1
  warning: 2
  info: 0
  total: 3
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-04-27T04:54:17Z  
**Depth:** standard  
**Files Reviewed:** 31  
**Status:** issues_found

## Summary

Reviewed the Phase 03 diagnostics, import preview, export preview, atomic ledger, ReactiveUI ViewModel, and Avalonia shell changes against the Phase plans, UI spec, and project ReactiveUI/Avalonia conventions. The main shipping blocker is that the UI ignores the Core export preview's batch-risk flag, so create-new multi-file exports proceed without the required partial-output-risk confirmation.

## Critical Issues

### CR-01: Multi-file export batch risk is computed but ignored before writing

**Classification:** BLOCKER  
**File:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:512-513`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:584-585`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:733-745`  
**Issue:** `ExportPreviewService` marks every multi-file export as batch risk (`files.Count > 1`), but `MainWindowViewModel.RequiresExportConfirmation` only checks `WillOverwrite`. As a result, routine create-new BodyGen exports write `templates.ini` and `morphs.ini` without the Phase 03-required confirmation for multi-file partial-output risk, and the preview summary explicitly says no confirmation is required. This violates Plan 03-06 and the UI spec requirement that existing targets **or multi-file partial-output risk** require confirmation before disk writes.  
**Fix:** Gate on `preview.HasBatchRisk` (or split overwrite vs batch-risk copy if desired) and update the summary to avoid claiming no confirmation is required when batch risk is present.

```csharp
private void ApplyExportPreview(string kind, ExportPreviewResult preview)
{
    ExportPreviewFiles.Clear();
    foreach (var file in preview.Files)
        ExportPreviewFiles.Add(new ExportPreviewViewModel(kind, file));

    HasExportPreview = ExportPreviewFiles.Count > 0;
    ExportPreviewSummary = preview.Files.Any(file => file.WillOverwrite)
        ? "Existing files will be overwritten. Confirm only after reviewing the paths and snippets below."
        : preview.HasBatchRisk
            ? "Multiple files will be written. Confirm after reviewing the paths and snippets below."
            : "New files will be created at the paths below. No overwrite confirmation is required.";
}

private static bool RequiresExportConfirmation(ExportPreviewResult preview) => preview.HasBatchRisk;
```

## Warnings

### WR-01: Opening or creating a project leaves stale Diagnostics/export preview/ledger state visible

**Classification:** WARNING  
**File:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:365-371`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:415-423`  
**Issue:** `NewProjectAsync` and `TryOpenProjectPathAsync` replace the backing `ProjectModel`, but they do not clear Phase 03 presentation state (`Diagnostics.Findings`, `ProfileSliderDiagnostics`, `ExportPreviewFiles`, `LastFileOperationLedger`, or NPC import preview rows). The Diagnostics tab can therefore show findings, file paths, and ledger outcomes from the previous project until the user manually reruns diagnostics or previews, which is misleading for readiness decisions.  
**Fix:** Add reset/clear methods for diagnostics and preview state, and call them after successful new/open operations.

```csharp
// After project.ReplaceWith(...), selection reset, and undo clear:
Diagnostics.ClearReport();
ClearExportPreview();
ClearFileOperationLedger();
Morphs.ClearNpcImportPreviewState();
```

### WR-02: Unbundled profile fallback is counted twice in Diagnostics findings

**Classification:** WARNING  
**File:** `src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs:89-96`, `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs:135-138`  
**Issue:** `ProfileDiagnosticsService.Analyze` already emits an Info finding for each unbundled saved profile, and `DiagnosticsViewModel.RefreshDiagnosticsAsync` appends those findings and then appends a second fallback-detail finding from the same summary. Projects with an unbundled profile get duplicated profile fallback rows and inflated `InfoCount`, making the severity totals less trustworthy.  
**Fix:** Keep one source of fallback findings. Prefer moving the UI-SPEC wording into the Core finding detail and remove `CreateProfileFallbackFindings`, or exclude `profileReport.Findings` when synthesizing UI-specific fallback details.

```csharp
var findings = projectReport.Findings
    .Concat(profileReport.Findings) // with UI-SPEC fallback wording produced once by Core
    .Select(finding => new DiagnosticFindingViewModel(finding))
    .OrderBy(finding => AreaSortIndex(finding.Area))
    .ThenBy(finding => SeveritySortIndex(finding.Severity))
    .ThenBy(finding => finding.Title, StringComparer.OrdinalIgnoreCase)
    .ToArray();
```

---

_Reviewed: 2026-04-27T04:54:17Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_
