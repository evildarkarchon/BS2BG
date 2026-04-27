---
phase: 03
slug: validation-and-diagnostics
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-26
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for Phase 03 diagnostics, previews, and outcome reporting.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | xUnit v3 3.2.2 + FluentAssertions 8.9.0 + Avalonia.Headless.XUnit 12.0.1 |
| **Config file** | `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| **Quick run command** | `dotnet test --filter FullyQualifiedName~Diagnostics -x` |
| **Full suite command** | `dotnet test` |
| **Estimated runtime** | ~60 seconds for focused commands; full suite project-dependent |

---

## Sampling Rate

- **After every task commit:** Run the task-specific `dotnet test --filter FullyQualifiedName~{ChangedSubjectTests} -x` command.
- **After every plan wave:** Run `dotnet test`.
- **Before `/gsd-verify-work`:** Full suite must be green.
- **Max feedback latency:** one focused test run per production task.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | DIAG-01 | T-03-01-01 | Validation reads project state without mutating it | unit | `dotnet test --filter FullyQualifiedName~ProjectValidationServiceTests -x` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | DIAG-02 | T-03-01-02 | Profile fallback diagnostics stay informational and avoid mismatch scoring | unit | `dotnet test --filter FullyQualifiedName~ProfileDiagnosticsServiceTests -x` | ❌ W0 | ⬜ pending |
| 03-02-01 | 02 | 1 | DIAG-03 | T-03-02-01 | NPC import preview preserves parser diagnostics and no-mutation semantics | unit | `dotnet test --filter FullyQualifiedName~NpcImportPreviewServiceTests -x` | ❌ W0 | ⬜ pending |
| 03-03-01 | 03 | 1 | DIAG-04 | T-03-03-01 | Export preview uses existing generation output and path rules without writing | unit | `dotnet test --filter FullyQualifiedName~ExportPreviewServiceTests -x` | ❌ W0 | ⬜ pending |
| 03-03-02 | 03 | 1 | DIAG-05 | T-03-03-02 | Atomic failures expose file outcome states without overpromising rollback | unit | `dotnet test --filter FullyQualifiedName~AtomicFileWriterOutcomeTests -x` | ❌ W0 | ⬜ pending |
| 03-04-01 | 04 | 2 | DIAG-01, DIAG-02 | T-03-04-01 | App diagnostics report can be refreshed and copied without data mutation | ViewModel | `dotnet test --filter FullyQualifiedName~DiagnosticsViewModelTests -x` | ❌ W0 | ⬜ pending |
| 03-05-01 | 05 | 2 | DIAG-03 | T-03-05-01 | Previewed NPC imports commit only after explicit command | ViewModel | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests -x` | ✅ | ⬜ pending |
| 03-06-01 | 06 | 2 | DIAG-04 | T-03-06-01 | Risk confirmation is required only for overwrites or batch partial-output risk | ViewModel | `dotnet test --filter FullyQualifiedName~MainWindowViewModelTests -x` | ✅ | ⬜ pending |
| 03-07-01 | 07 | 3 | DIAG-05 | T-03-07-01 | Save/export failure status includes written/restored/skipped/left-untouched states | ViewModel | `dotnet test --filter FullyQualifiedName~MainWindowViewModelTests -x` | ✅ | ⬜ pending |
| 03-08-01 | 08 | 4 | DIAG-01, DIAG-02, DIAG-03, DIAG-04, DIAG-05 | T-03-08-01 | Diagnostics UI exposes accessible, compiled-bound report and preview surfaces | headless UI | `dotnet test --filter "FullyQualifiedName~AppShellTests|FullyQualifiedName~DiagnosticsViewModelTests" -x` | ⚠️ partial | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `tests/BS2BG.Tests/ProjectValidationServiceTests.cs` — stubs and failing tests for DIAG-01.
- [ ] `tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs` — stubs and failing tests for DIAG-02.
- [ ] `tests/BS2BG.Tests/NpcImportPreviewServiceTests.cs` — stubs and failing tests for DIAG-03.
- [ ] `tests/BS2BG.Tests/ExportPreviewServiceTests.cs` — stubs and failing tests for DIAG-04.
- [ ] `tests/BS2BG.Tests/AtomicFileWriterOutcomeTests.cs` — stubs and failing tests for DIAG-05.
- [ ] `tests/BS2BG.Tests/DiagnosticsViewModelTests.cs` — ViewModel report/copy/navigation tests.
- [ ] `tests/BS2BG.Tests/AppShellTests.cs` — extend existing shell tests for Diagnostics tab exposure.

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
