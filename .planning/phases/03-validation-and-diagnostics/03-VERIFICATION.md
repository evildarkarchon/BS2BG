---
phase: 03-validation-and-diagnostics
verified: 2026-04-27T05:02:22Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 1
overrides:
  - must_have: "User can inspect profile diagnostics for slider coverage, unknown sliders, injected defaults, multipliers, inversions, and likely profile mismatches."
    reason: "Phase 3 CONTEXT D-06 explicitly narrows DIAG-02 to concrete coverage/unknown/default/multiplier/inversion/fallback diagnostics and excludes likely-profile-mismatch scoring/heuristics."
    accepted_by: "Phase 3 CONTEXT D-06 user decision"
    accepted_at: "2026-04-26T00:00:00Z"
---

# Phase 3: Validation and Diagnostics Verification Report

**Phase Goal:** Users can inspect project health, import effects, and export consequences before committing risky changes.
**Verified:** 2026-04-27T05:02:22Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run a read-only validation report that identifies profile, preset, target, NPC assignment, reference, and export-readiness issues. | ✓ VERIFIED | `ProjectValidationService.Validate(ProjectModel, TemplateProfileCatalog)` reads project collections, emits Project/Profiles/Templates/Morphs/NPCs/Export findings, checks empty presets, empty names, unbundled profiles, morph/NPC assignment state, stale preset references, and export readiness. `DiagnosticsViewModel.RefreshDiagnosticsAsync` consumes it without dirty/version mutation. |
| 2 | User can inspect profile diagnostics for slider coverage, unknown sliders, injected defaults, multipliers, inversions, and likely profile mismatches. | ✓ VERIFIED (override applied) | `ProfileDiagnosticsService.Analyze` computes known/unknown slider counts, injected defaults, multiplier and inversion facts, affected preset count, slider drilldown rows, and neutral fallback details. Override applied for the literal mismatch-heuristic portion per `03-CONTEXT.md` D-06/D-07/D-08. |
| 3 | User can preview NPC import results, invalid lines, duplicates, charset fallback, and assignment effects before committing import changes. | ✓ VERIFIED | `NpcImportPreviewService` wraps `NpcTextParser.ParseFile/ParseText`, returns parsed rows, rows-to-add, existing duplicates, parser diagnostics, fallback encoding fields, and does not mutate existing NPC collections. `MorphsViewModel.PreviewNpcImportCommand` populates temporary rows; `ImportPreviewedNpcsCommand` is the only preview import path that calls `AddNpcsToDatabase`; assignment-changing commands update `LastAssignmentEffectSummary`. |
| 4 | User can preview export destinations and exact output effects before overwriting files or risking partial output. | ✓ VERIFIED | `ExportPreviewService` previews `templates.ini`, `morphs.ini`, and BoS JSON target paths with create/overwrite state, snippets from real generation output, sanitized unique BoS names, and `HasBatchRisk`. `MainWindowViewModel` exposes preview commands and gates writes through `ConfirmExportOverwriteAsync` when `preview.HasBatchRisk`; review fix CR-01 is present. |
| 5 | User receives save/export failure messages that identify which files were written, restored, skipped, or left untouched. | ✓ VERIFIED | `AtomicFileWriter` tracks `FileWriteOutcome` states and throws `AtomicWriteException`; writers and project save use atomic write paths. `MainWindowViewModel.ReportFileOperationFailure` finds the ledger, populates `LastFileOperationLedger`, and writes `File operation incomplete` status with written/restored/skipped/left-untouched guidance. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs` | Read-only project health findings | ✓ VERIFIED | Exists, substantive, reads `ProjectModel`, emits grouped readiness findings. |
| `src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs` | Profile diagnostics facts | ✓ VERIFIED | Exists, substantive, computes profile summary and slider drilldown from catalog/profile tables. |
| `src/BS2BG.Core/Import/NpcImportPreviewService.cs` | No-mutation NPC import preview | ✓ VERIFIED | Exists, substantive, uses parser results and duplicate classification without committing rows. |
| `src/BS2BG.Core/Diagnostics/ExportPreviewService.cs` | Read-only export preview facts | ✓ VERIFIED | Exists, substantive, previews paths/snippets without calling writer `Write` APIs. |
| `src/BS2BG.Core/IO/WriteOutcomeLedger.cs` / `AtomicWriteException.cs` | File outcome ledger | ✓ VERIFIED | Exists with Written/Restored/Skipped/LeftUntouched/Incomplete states and exception-carried entries. |
| `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs` | ReactiveUI diagnostics presentation | ✓ VERIFIED | Refresh/copy commands, counts, grouped findings, selected detail, profile drilldown, clear state. |
| `src/BS2BG.App/ViewModels/MorphsViewModel.cs` | NPC preview/commit and assignment effects | ✓ VERIFIED | Preview and commit commands wired; assignment effect summaries present. |
| `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` | Export preview, confirmation, ledger, diagnostics workspace | ✓ VERIFIED | Diagnostics workspace, preview commands, batch-risk confirmation, ledger status, project-state clearing. |
| `src/BS2BG.App/Views/MainWindow.axaml` | First-class Diagnostics tab UI | ✓ VERIFIED | `Header="Diagnostics"`, compiled-bound templates, report/detail/NPC preview/export preview/ledger surfaces, automation names. |
| Tests under `tests/BS2BG.Tests/` | Regression coverage | ✓ VERIFIED | Full `dotnet test --no-restore` passed 337/337. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `ProjectValidationService` | `ProjectModel` | `Validate(ProjectModel project, ...)` | ✓ WIRED | Reads presets, custom targets, morphed NPCs, and references. |
| `ProfileDiagnosticsService` | `TemplateProfileCatalog.ContainsProfile/GetProfile` | fallback/profile table lookup | ✓ WIRED | Uses `ContainsProfile` and `GetProfile` to produce neutral fallback and table diagnostics. |
| `NpcImportPreviewService` | `NpcTextParser.ParseFile/ParseText` | parser result wrapping | ✓ WIRED | Preview methods call parser and classify `NpcImportResult`. |
| `MorphsViewModel` | `NpcImportPreviewService` | `PreviewNpcImportCommand` | ✓ WIRED | Command calls `PreviewFile` and populates `NpcImportPreviewRows`. |
| `MorphsViewModel` | `AddNpcsToDatabase` | `ImportPreviewedNpcsCommand` only | ✓ WIRED | Preview does not call add; commit collects `CanImport` rows and calls `AddNpcsToDatabase`. |
| `ExportPreviewService` | generation services/path rules | preview snippets and filenames | ✓ WIRED | BodyGen preview uses generated text; BoS preview calls `TemplateGenerationService.PreviewBosJson` and mirrors writer naming. |
| `MainWindowViewModel` | `ExportPreviewService` / `IAppDialogService` | preview preflight and confirmation | ✓ WIRED | Exports compute preview before writes and call `ConfirmExportOverwriteAsync` when `HasBatchRisk`. |
| `AtomicWriteException` | `MainWindowViewModel` | ledger catch/formatting | ✓ WIRED | `FindAtomicWriteException` extracts direct/inner/aggregate exceptions and formats rows/status. |
| export/project writers | `AtomicFileWriter` | atomic write paths | ✓ WIRED | BodyGen uses `WriteAtomicPair`; BoS and project save use `WriteAtomicBatch`. |
| `MainWindow.axaml` | `MainWindowViewModel.Diagnostics` | compiled bindings | ✓ WIRED | Diagnostics tab binds to `Diagnostics.RefreshDiagnosticsCommand`, `Diagnostics.CopyReportCommand`, findings, and drilldown data. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `DiagnosticsViewModel` | `Findings`, counts, `ProfileSliderDiagnostics` | `ProjectValidationService.Validate` + `ProfileDiagnosticsService.Analyze` over live `ProjectModel` | Yes | ✓ FLOWING |
| `MorphsViewModel` | `NpcImportPreviewRows`, summary | `NpcImportPreviewService.PreviewFile` over selected files and current `NpcDatabase` | Yes | ✓ FLOWING |
| `MainWindowViewModel` | `ExportPreviewFiles`, summary | `ExportPreviewService.PreviewBodyGen/PreviewBosJson` over generated output/current presets and selected target folder | Yes | ✓ FLOWING |
| `MainWindowViewModel` | `LastFileOperationLedger` | `AtomicWriteException.Entries` from atomic save/export failures | Yes | ✓ FLOWING |
| `MainWindow.axaml` | Diagnostics tab lists/buttons | Bound to `Diagnostics`, `Morphs`, export preview, and ledger ViewModel properties | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Full regression suite | `dotnet test --no-restore` | Passed 337/337 | ✓ PASS |
| Golden fixture drift | `git diff -- tests/fixtures/expected` | No output / no changes | ✓ PASS |
| Plan artifact checks | `gsd-sdk query verify.artifacts` for plans 03-01 through 03-08 | All artifact groups passed | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| DIAG-01 | 03-01, 03-04, 03-08 | Read-only validation report for profile, preset, target, NPC assignment, reference, export readiness issues | ✓ SATISFIED | Core validation service, Diagnostics ViewModel refresh/copy, and Diagnostics tab are wired and tested. |
| DIAG-02 | 03-01, 03-04, 03-08 | Profile diagnostics for slider coverage, unknown sliders, injected defaults, multipliers, inversions, likely profile mismatch indicators | ✓ SATISFIED WITH OVERRIDE | Coverage/default/multiplier/inversion/fallback implemented; mismatch heuristics intentionally narrowed by D-06 and recorded as an override. |
| DIAG-03 | 03-02, 03-05, 03-08 | NPC import preview including parsed rows, invalid lines, duplicates, charset fallback, assignment effects before commit | ✓ SATISFIED | Core preview service plus Morphs ViewModel preview/commit split and assignment-effect summaries. |
| DIAG-04 | 03-03, 03-06, 03-08 | Export destination/effect preview before overwrite or partial-output risk | ✓ SATISFIED | Core preview plus shell preview commands and batch-risk/overwrite confirmation; review CR-01 fix verified. |
| DIAG-05 | 03-03, 03-07, 03-08 | Save/export failure messages identify written/restored/skipped/left-untouched files | ✓ SATISFIED | Atomic outcome ledger, writer/save propagation, App ledger rows and failure copy. |

No orphaned Phase 3 requirement IDs were found in `REQUIREMENTS.md`: DIAG-01 through DIAG-05 are all claimed by Phase 3 plans and accounted for above.

### Advisory Review Fix Verification

| Review Finding | Status | Evidence |
|---|---|---|
| CR-01 batch-risk ignored | ✓ FIXED | `ExportPreviewService.HasRisk` returns true for multi-file or overwrite; `MainWindowViewModel.RequiresExportConfirmation` returns `preview.HasBatchRisk`; preview summary no longer claims no confirmation for batch risk. |
| WR-01 stale diagnostics/preview/ledger state on new/open | ✓ FIXED | `ClearProjectPresentationState` calls `Diagnostics.ClearReport`, `ClearExportPreview`, `ClearFileOperationLedger`, and `Morphs.ClearNpcImportPreviewState`; called after new/open project replacement. |
| WR-02 duplicate synthesized fallback finding | ✓ FIXED | `DiagnosticsViewModel.RefreshDiagnosticsAsync` concatenates project/profile report findings and no longer synthesizes an extra fallback finding from summary; test asserts a single `Profile fallback detail` row. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---:|---|---|---|
| `src/BS2BG.App/ViewModels/MorphsViewModel.cs` | 1820 | `return null` | ℹ️ Info | Legitimate nullable helper return (`Guid? TryGetTargetRowId`), not a stub. |

### Human Verification Required

None outstanding. Plan 03-08's blocking human visual checkpoint was completed with user response `approved`, and the prompt context confirms approval.

### Gaps Summary

No blocking gaps found. The only literal deviation is DIAG-02's likely-profile-mismatch heuristic wording; this is intentionally narrowed by Phase 3 context D-06 and documented as an applied override. Phase goal is achieved.

---

_Verified: 2026-04-27T05:02:22Z_
_Verifier: the agent (gsd-verifier)_
