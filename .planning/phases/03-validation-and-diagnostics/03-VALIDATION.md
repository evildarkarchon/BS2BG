---
phase: 03
slug: validation-and-diagnostics
status: audited
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-26
last_audited: 2026-04-26
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for Phase 03 diagnostics, previews, and outcome reporting.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | xUnit v3 3.2.2 + FluentAssertions 8.9.0 + Avalonia.Headless.XUnit 12.0.1 |
| **Config file** | `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| **Quick run command** | `dotnet test --filter "FullyQualifiedName~ProjectValidationServiceTests\|FullyQualifiedName~ProfileDiagnosticsServiceTests\|FullyQualifiedName~NpcImportPreviewServiceTests\|FullyQualifiedName~ExportPreviewServiceTests\|FullyQualifiedName~AtomicFileWriterOutcomeTests\|FullyQualifiedName~DiagnosticsViewModelTests\|FullyQualifiedName~MorphsViewModelTests\|FullyQualifiedName~MainWindowViewModelTests\|FullyQualifiedName~AppShellTests\|FullyQualifiedName~ExportWriterTests"` |
| **Full suite command** | `dotnet test` |
| **Estimated runtime** | ~10 seconds for focused Phase 3 validation tests; full suite project-dependent |

---

## Sampling Rate

- **After every task commit:** Run the task-specific `dotnet test --filter FullyQualifiedName~{ChangedSubjectTests}` command.
- **After every plan wave:** Run `dotnet test`.
- **Before `/gsd-verify-work`:** Full suite must be green.
- **Max feedback latency:** one focused test run per production task.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | DIAG-01 | T-03-01-01 | Validation reads project state without mutating it | unit | `dotnet test --filter FullyQualifiedName~ProjectValidationServiceTests` | ✅ | ✅ green |
| 03-01-02 | 01 | 1 | DIAG-02 | T-03-01-02 | Profile fallback diagnostics stay informational and avoid mismatch scoring | unit | `dotnet test --filter FullyQualifiedName~ProfileDiagnosticsServiceTests` | ✅ | ✅ green |
| 03-02-01 | 02 | 1 | DIAG-03 | T-03-02-01 | NPC import preview preserves parser diagnostics and no-mutation semantics | unit | `dotnet test --filter FullyQualifiedName~NpcImportPreviewServiceTests` | ✅ | ✅ green |
| 03-03-01 | 03 | 1 | DIAG-04 | T-03-03-01 | Export preview uses existing generation output and path rules without writing | unit | `dotnet test --filter FullyQualifiedName~ExportPreviewServiceTests` | ✅ | ✅ green |
| 03-03-02 | 03 | 1 | DIAG-05 | T-03-03-02 | Atomic failures expose file outcome states without overpromising rollback | unit | `dotnet test --filter FullyQualifiedName~AtomicFileWriterOutcomeTests` | ✅ | ✅ green |
| 03-04-01 | 04 | 2 | DIAG-01, DIAG-02 | T-03-04-01 | App diagnostics report can be refreshed and copied without data mutation | ViewModel | `dotnet test --filter FullyQualifiedName~DiagnosticsViewModelTests` | ✅ | ✅ green |
| 03-05-01 | 05 | 2 | DIAG-03 | T-03-05-01 | Previewed NPC imports commit only after explicit command | ViewModel | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | ✅ | ✅ green |
| 03-06-01 | 06 | 2 | DIAG-04 | T-03-06-01 | Risk confirmation is required for overwrite or batch partial-output risk while create-new previews remain read-only | ViewModel | `dotnet test --filter FullyQualifiedName~MainWindowViewModelTests` | ✅ | ✅ green |
| 03-07-01 | 07 | 3 | DIAG-05 | T-03-07-01 | Save/export failure status includes written/restored/skipped/left-untouched states | ViewModel | `dotnet test --filter FullyQualifiedName~MainWindowViewModelTests` | ✅ | ✅ green |
| 03-08-01 | 08 | 4 | DIAG-01, DIAG-02, DIAG-03, DIAG-04, DIAG-05 | T-03-08-01 | Diagnostics UI exposes accessible, compiled-bound report and preview surfaces | headless UI | `dotnet test --filter "FullyQualifiedName~AppShellTests\|FullyQualifiedName~DiagnosticsViewModelTests"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `tests/BS2BG.Tests/ProjectValidationServiceTests.cs` — DIAG-01 read-only project validation coverage.
- [x] `tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs` — DIAG-02 profile coverage, default injection, multiplier, inversion, and neutral fallback coverage.
- [x] `tests/BS2BG.Tests/NpcImportPreviewServiceTests.cs` — DIAG-03 parser diagnostics, duplicate classification, fallback encoding, and no-mutation preview coverage.
- [x] `tests/BS2BG.Tests/ExportPreviewServiceTests.cs` — DIAG-04 export preview path, overwrite/create, snippet, batch-risk, and no-write coverage.
- [x] `tests/BS2BG.Tests/AtomicFileWriterOutcomeTests.cs` — DIAG-05 restored, skipped, left-untouched, and incomplete rollback outcome coverage.
- [x] `tests/BS2BG.Tests/DiagnosticsViewModelTests.cs` — Diagnostics refresh/copy/navigation tests for DIAG-01 and DIAG-02.
- [x] `tests/BS2BG.Tests/MorphsViewModelTests.cs` — NPC preview commit and assignment-effect tests for DIAG-03.
- [x] `tests/BS2BG.Tests/MainWindowViewModelTests.cs` — export preview, confirmation, and file ledger tests for DIAG-04 and DIAG-05.
- [x] `tests/BS2BG.Tests/AppShellTests.cs` — Diagnostics workspace and accessible action exposure tests for DIAG-01 through DIAG-05.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual density, splitter sizing, and copy tone in the Diagnostics tab | DIAG-01 through DIAG-05 | Headless tests can assert controls and copy, but not practical desktop readability | Run `dotnet run --project src/BS2BG.App/BS2BG.App.csproj`, open Diagnostics, preview import/export scenarios, confirm copy matches UI-SPEC text. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency targeted below one focused test run per task
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-04-26

---

## Validation Audit 2026-04-26

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

### Requirement Coverage

| Requirement | Status | Automated Evidence |
|-------------|--------|--------------------|
| DIAG-01 | COVERED | `ProjectValidationServiceTests`, `DiagnosticsViewModelTests`, `AppShellTests` |
| DIAG-02 | COVERED | `ProfileDiagnosticsServiceTests`, `DiagnosticsViewModelTests`, `AppShellTests` |
| DIAG-03 | COVERED | `NpcImportPreviewServiceTests`, `MorphsViewModelTests`, `AppShellTests` |
| DIAG-04 | COVERED | `ExportPreviewServiceTests`, `MainWindowViewModelTests`, `AppShellTests` |
| DIAG-05 | COVERED | `AtomicFileWriterOutcomeTests`, `ExportWriterTests`, `MainWindowViewModelTests`, `AppShellTests` |

### Audit Notes

- State A audit performed against existing `03-VALIDATION.md`, all eight plan summaries, `03-VERIFICATION.md`, and current test files.
- No Nyquist gaps were found, so no new test files were generated by this audit.
- The documented `-x` test switch was removed from validation commands because Phase 3 execution summaries record it as unsupported by this .NET/MSBuild environment.
- Focused audit command passed: `dotnet test --filter "FullyQualifiedName~ProjectValidationServiceTests|FullyQualifiedName~ProfileDiagnosticsServiceTests|FullyQualifiedName~NpcImportPreviewServiceTests|FullyQualifiedName~ExportPreviewServiceTests|FullyQualifiedName~AtomicFileWriterOutcomeTests|FullyQualifiedName~DiagnosticsViewModelTests|FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~AppShellTests|FullyQualifiedName~ExportWriterTests"` — 147/147 passed.
