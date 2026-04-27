---
phase: 03-validation-and-diagnostics
fixed_at: 2026-04-27T04:56:14Z
review_path: .planning/phases/03-validation-and-diagnostics/03-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 03: Code Review Fix Report

**Fixed at:** 2026-04-27T04:56:14Z
**Source review:** `.planning/phases/03-validation-and-diagnostics/03-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

## Fixed Issues

### CR-01: Multi-file export batch risk is computed but ignored before writing

**Files modified:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `tests/BS2BG.Tests/MainWindowViewModelTests.cs`
**Commit:** ee3e39f5
**Applied fix:** Export confirmation now gates on `ExportPreviewResult.HasBatchRisk`, and multi-file preview copy no longer claims no confirmation is required.

### WR-01: Opening or creating a project leaves stale Diagnostics/export preview/ledger state visible

**Files modified:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `tests/BS2BG.Tests/MainWindowViewModelTests.cs`
**Commit:** 256001e8
**Applied fix:** New/open project flows clear diagnostics, export previews, file-operation ledger rows, and NPC import preview state after replacing project data.

### WR-02: Unbundled profile fallback is counted twice in Diagnostics findings

**Files modified:** `src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs`, `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs`, `tests/BS2BG.Tests/DiagnosticsViewModelTests.cs`, `tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs`
**Commit:** ba8b9953
**Applied fix:** Core profile diagnostics now emits the UI-ready fallback detail once, and the ViewModel no longer synthesizes a duplicate fallback finding.

## Skipped Issues

None.

---

_Fixed: 2026-04-27T04:56:14Z_  
_Fixer: the agent (gsd-code-fixer)_  
_Iteration: 1_
