---
phase: 04
slug: profile-extensibility-and-controlled-customization
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-27
---

# Phase 04 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | xUnit v3 + FluentAssertions + Avalonia.Headless.XUnit |
| **Config file** | `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| **Quick run command** | `dotnet test --filter FullyQualifiedName~Profile` |
| **Full suite command** | `dotnet test` |
| **Estimated runtime** | focused filters under 60 seconds; full suite project-standard |

---

## Sampling Rate

- **After every task commit:** Run the focused command named in that task's `<automated>` verification.
- **After every plan wave:** Run `dotnet test --filter "FullyQualifiedName~Profile|FullyQualifiedName~ProjectFileService|FullyQualifiedName~TemplatesViewModel|FullyQualifiedName~DiagnosticsViewModel|FullyQualifiedName~MainWindow"`.
- **Before `/gsd-verify-work`:** Full `dotnet test` must be green.
- **Max feedback latency:** one focused test class per task.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | EXT-01/EXT-02 | T-04-01-01 | malformed/ambiguous profile JSON rejected before catalog inclusion | unit | `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` | ❌ W0 | ⬜ pending |
| 04-01-02 | 01 | 1 | EXT-01/EXT-02 | T-04-01-02 | duplicate names cannot shadow bundled/custom profiles | unit | `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` | ❌ W0 | ⬜ pending |
| 04-02-01 | 02 | 2 | EXT-01 | T-04-02-02 | catalog rejects case-insensitive duplicate names | unit | `dotnet test --filter FullyQualifiedName~TemplateProfileCatalogFactoryTests` | ✅ existing extended | ⬜ pending |
| 04-02-02 | 02 | 2 | EXT-01 | T-04-02-01/T-04-02-03 | user profile store validates and writes atomically | unit | `dotnet test --filter FullyQualifiedName~UserProfileStoreTests` | ❌ W0 | ⬜ pending |
| 04-03-01 | 03 | 2 | EXT-03/EXT-05 | T-04-03-03 | optional project section preserves legacy fields | unit | `dotnet test --filter FullyQualifiedName~ProjectFileServiceCustomProfileTests` | ❌ W0 | ⬜ pending |
| 04-03-02 | 03 | 2 | EXT-03/EXT-05 | T-04-03-02 | only referenced custom profiles are embedded | unit | `dotnet test --filter "FullyQualifiedName~ProjectFileServiceCustomProfileTests|FullyQualifiedName~ProjectFileServiceTests"` | ❌ W0 | ⬜ pending |
| 04-04-01 | 04 | 2 | EXT-04 | T-04-04-01 | missing custom profiles are visible neutral diagnostics | unit | `dotnet test --filter FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests` | ❌ W0 | ⬜ pending |
| 04-04-02 | 04 | 2 | EXT-04 | T-04-04-02 | recovery identity ignores filenames and matches internal display name only | unit | `dotnet test --filter FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests` | ❌ W0 | ⬜ pending |
| 04-05-01 | 05 | 3 | EXT-01/EXT-02 | T-04-05-01/T-04-05-02 | bundled profiles are read-only; imports validate before write | ViewModel unit | `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` | ❌ W0 | ⬜ pending |
| 04-05-02 | 05 | 3 | EXT-01/EXT-02 | T-04-05-03 | save is validation-gated and uses profile store | ViewModel unit | `dotnet test --filter FullyQualifiedName~ProfileEditorViewModelTests` | ❌ W0 | ⬜ pending |
| 04-06-01 | 06 | 3 | EXT-03/EXT-04/EXT-05 | T-04-06-01 | embedded/local conflicts prompt explicit choice | ViewModel unit | `dotnet test --filter FullyQualifiedName~MainWindowViewModelProfileRecoveryTests` | ❌ W0 | ⬜ pending |
| 04-06-03 | 06 | 3 | EXT-04 | T-04-06-02/T-04-06-03 | recovery remap is explicit and undo-aware | ViewModel unit | `dotnet test --filter "FullyQualifiedName~DiagnosticsViewModelTests|FullyQualifiedName~TemplatesViewModelTests"` | ✅ existing extended | ⬜ pending |
| 04-07-02 | 07 | 4 | EXT-01/EXT-02/EXT-04/EXT-05 | T-04-07-01/T-04-07-02 | Profiles UI is source-aware and compiled-bound | headless UI | `dotnet test --filter FullyQualifiedName~MainWindowHeadlessTests` | ✅ existing extended | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs` — covers EXT-01/EXT-02 parse/validate/export rules.
- [ ] `tests/BS2BG.Tests/UserProfileStoreTests.cs` — covers AppData store, atomic write, discovery, and duplicate filename conventions.
- [ ] `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs` — covers EXT-03/EXT-05 embedded section and legacy fields.
- [ ] `tests/BS2BG.Tests/ProfileRecoveryDiagnosticsServiceTests.cs` — covers EXT-04 exact-match recovery and neutral unresolved state.
- [ ] `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` and `tests/BS2BG.Tests/ProfileEditorViewModelTests.cs` — covers App authoring workflows.
- [ ] `tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs` — covers embedded conflict decisions and project-open recovery.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Profiles workspace visual layout and copy | EXT-01/EXT-02/EXT-04/EXT-05 | Avalonia visual density, source-label clarity, and neutral copy require human confirmation | Complete Plan 07 checkpoint steps after automated headless tests pass. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Focused feedback commands under 60 seconds
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
