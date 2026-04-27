---
phase: 04
slug: profile-extensibility-and-controlled-customization
status: passed
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-27
updated: 2026-04-27
input_state: A
gaps_found: 0
gaps_resolved: 0
gaps_escalated: 0
---

# Phase 04 - Validation Strategy

Per-phase Nyquist validation audit for Profile Extensibility and Controlled Customization.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| Framework | xUnit v3 + FluentAssertions + Avalonia.Headless.XUnit |
| Config file | `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| Quick run command | `dotnet test --filter FullyQualifiedName~Profile` |
| Full suite command | `dotnet test` |
| Latest full-suite result | PASS - 433 passed, 0 failed, 0 skipped |

---

## Sampling Rate

| Event | Required Validation |
|-------|---------------------|
| After each task commit | Run the focused command named in that task's `<automated>` verification block. |
| After each plan wave | Run the combined focused command for all modified Phase 4 test classes in the wave. |
| Before phase verification | Run `dotnet test` and `dotnet build BS2BG.sln`. |
| Feedback latency target | One focused test class per task, then full suite before sign-off. |

---

## Requirement Coverage

| Requirement | Status | Primary Test Evidence |
|-------------|--------|-----------------------|
| EXT-01 | COVERED | `ProfileDefinitionServiceTests`, `UserProfileStoreTests`, `TemplateProfileCatalogFactoryTests`, `ProfileManagerViewModelTests`, `MainWindowHeadlessTests` |
| EXT-02 | COVERED | `ProfileDefinitionServiceTests`, `ProfileEditorViewModelTests`, `ProfileManagerViewModelTests`, `MainWindowHeadlessTests` |
| EXT-03 | COVERED | `ProjectFileServiceCustomProfileTests`, `MainWindowViewModelTests`, `MainWindowViewModelProfileRecoveryTests` |
| EXT-04 | COVERED | `ProfileRecoveryDiagnosticsServiceTests`, `ProfileDiagnosticsServiceTests`, `DiagnosticsViewModelTests`, `ProfileManagerViewModelTests` |
| EXT-05 | COVERED | `ProjectFileServiceCustomProfileTests`, `MainWindowViewModelTests`, `ProfileManagerViewModelTests`, `TemplatesViewModelTests` |

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Automated Command | File Exists | Status |
|---------|------|------|-------------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | EXT-01/EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` | Yes | COVERED |
| 04-01-02 | 01 | 1 | EXT-01/EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` | Yes | COVERED |
| 04-02-01 | 02 | 2 | EXT-01 | `dotnet test --filter FullyQualifiedName~TemplateProfileCatalogFactoryTests` | Yes | COVERED |
| 04-02-02 | 02 | 2 | EXT-01 | `dotnet test --filter FullyQualifiedName~UserProfileStoreTests` | Yes | COVERED |
| 04-02-03 | 02 | 2 | EXT-01 | `dotnet test --filter FullyQualifiedName~TemplateProfileCatalogFactoryTests` | Yes | COVERED |
| 04-03-01 | 03 | 2 | EXT-03/EXT-05 | `dotnet test --filter FullyQualifiedName~ProjectFileServiceCustomProfileTests` | Yes | COVERED |
| 04-03-02 | 03 | 2 | EXT-03/EXT-05 | `dotnet test --filter "FullyQualifiedName~ProjectFileServiceCustomProfileTests|FullyQualifiedName~ProjectFileServiceTests"` | Yes | COVERED |
| 04-04-01 | 04 | 3 | EXT-04 | `dotnet test --filter FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests` | Yes | COVERED |
| 04-04-02 | 04 | 3 | EXT-04 | `dotnet test --filter FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests` | Yes | COVERED |
| 04-04-03 | 04 | 3 | EXT-04 | `dotnet test --filter "FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests|FullyQualifiedName~ProfileDiagnosticsServiceTests|FullyQualifiedName~ProjectValidationServiceTests"` | Yes | COVERED |
| 04-05-01 | 05 | 3 | EXT-01/EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` | Yes | COVERED |
| 04-05-02 | 05 | 3 | EXT-01/EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileEditorViewModelTests` | Yes | COVERED |
| 04-05-03 | 05 | 3 | EXT-01/EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` | Yes | COVERED |
| 04-06-01 | 06 | 4 | EXT-03/EXT-04/EXT-05 | `dotnet test --filter FullyQualifiedName~MainWindowViewModelProfileRecoveryTests` | Yes | COVERED |
| 04-06-02 | 06 | 4 | EXT-03/EXT-04/EXT-05 | `dotnet test --filter FullyQualifiedName~MainWindowViewModelProfileRecoveryTests` | Yes | COVERED |
| 04-07-01 | 07 | 5 | EXT-01/EXT-03/EXT-04/EXT-05 | `dotnet test --filter "FullyQualifiedName~DiagnosticsViewModelTests|FullyQualifiedName~ProfileManagerViewModelTests"` | Yes | COVERED |
| 04-07-02 | 07 | 5 | EXT-01/EXT-03/EXT-04/EXT-05 | `dotnet test --filter "FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~ProfileManagerViewModelTests"` | Yes | COVERED |
| 04-08-01 | 08 | 6 | EXT-01/EXT-02/EXT-04/EXT-05 | `dotnet test --filter FullyQualifiedName~AppShellTests` | Yes | COVERED |
| 04-08-02 | 08 | 6 | EXT-01/EXT-02/EXT-04/EXT-05 | `dotnet test --filter FullyQualifiedName~MainWindowHeadlessTests` | Yes | COVERED |
| 04-09-01 | 09 | 7 | EXT-01/EXT-05 | `dotnet test --filter "FullyQualifiedName~ProfileManagerViewModelTests|FullyQualifiedName~MainWindowHeadlessTests"` | Yes | COVERED |
| 04-09-02 | 09 | 7 | EXT-01/EXT-02/EXT-05 | `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` | Yes | COVERED |
| 04-10-01 | 10 | 8 | EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileEditorViewModelTests` | Yes | COVERED |
| 04-10-02 | 10 | 8 | EXT-02 | `dotnet test --filter "FullyQualifiedName~ProfileEditorViewModelTests|FullyQualifiedName~MainWindowHeadlessTests"` | Yes | COVERED |
| 04-11-01 | 11 | 7 | EXT-03/EXT-05 | `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~ProjectFileServiceCustomProfileTests"` | Yes | COVERED |
| 04-11-02 | 11 | 7 | EXT-03/EXT-05 | `dotnet test --filter FullyQualifiedName~MainWindowViewModelProfileRecoveryTests` | Yes | COVERED |
| 04-12-01 | 12 | 9 | EXT-01/EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` | Yes | COVERED |
| 04-12-02 | 12 | 9 | EXT-01/EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` | Yes | COVERED |
| 04-12-03 | 12 | 9 | EXT-01 | `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` | Yes | COVERED |
| 04-13-01 | 13 | 9 | EXT-02 | `dotnet test --filter FullyQualifiedName~ProfileEditorViewModelTests` | Yes | COVERED |
| 04-13-02 | 13 | 9 | EXT-02 | `dotnet test --filter "FullyQualifiedName~ProfileEditorViewModelTests|FullyQualifiedName~MainWindowHeadlessTests"` | Yes | COVERED |

---

## Post-Review Regression Coverage

| Finding | Requirement | Test Evidence | Status |
|---------|-------------|---------------|--------|
| CR-01: recovery remap action not implemented | EXT-04/EXT-05 | `ProfileManagerViewModelTests.RecoveryRemapActionPromptsForInstalledProfileAndDelegatesReferenceRemap` | COVERED |
| CR-02: malformed `CustomProfiles` section aborts project load | EXT-03/EXT-05 | `ProjectFileServiceCustomProfileTests.LoadWithDiagnosticsReportsMalformedCustomProfilesSectionAndKeepsLegacyProjectData` | COVERED |
| CR-03: multi-file profile import duplicate overwrite | EXT-01/EXT-02 | `ProfileManagerViewModelTests.ImportProfileBatchRejectsDuplicateNamesAfterEarlierBatchSave` | COVERED |
| WR-01: delete command missing from Profiles UI | EXT-01 | `MainWindowHeadlessTests.ProfilesWorkspaceTabAndRequiredControlsArePresent` | COVERED |
| WR-02: blank slider validation message misleading | EXT-02 | `ProfileEditorViewModelTests.DuplicateAndBlankSliderRowsAreRejected` | COVERED |

---

## Generated Test Files

No new test files were generated during this audit because all detected Phase 4 coverage gaps already had regression tests in the current tree. Existing generated Phase 4 test coverage lives in:

| Test File | Coverage Area |
|-----------|---------------|
| `tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs` | Strict custom-profile validation/export |
| `tests/BS2BG.Tests/UserProfileStoreTests.cs` | AppData profile store discovery/save/delete |
| `tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs` | Source-tagged catalog composition |
| `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs` | Optional `CustomProfiles` project serialization and diagnostics |
| `tests/BS2BG.Tests/ProfileRecoveryDiagnosticsServiceTests.cs` | Missing-profile recovery diagnostics and exact-name resolution |
| `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` | Profile import/copy/save/export/delete/recovery workflows |
| `tests/BS2BG.Tests/ProfileEditorViewModelTests.cs` | Profile editor validation, authoring, filtering, and save data preservation |
| `tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs` | Project-open conflict transactions and recovery state |
| `tests/BS2BG.Tests/MainWindowViewModelTests.cs` | GUI project save context and sharing behavior |
| `tests/BS2BG.Tests/DiagnosticsViewModelTests.cs` | Diagnostics recovery action rows |
| `tests/BS2BG.Tests/TemplatesViewModelTests.cs` | Undo-aware profile remap behavior |
| `tests/BS2BG.Tests/AppShellTests.cs` | Profiles workspace shell navigation and search routing |
| `tests/BS2BG.Tests/MainWindowHeadlessTests.cs` | Profiles workspace UI bindings and accessibility smoke coverage |

---

## Manual-Only Verifications

| Behavior | Requirement | Status | Evidence |
|----------|-------------|--------|----------|
| Profiles workspace visual layout and copy | EXT-01/EXT-02/EXT-04/EXT-05 | Closed | Human visual checkpoint approved in `04-08-SUMMARY.md`; current audit found no remaining manual-only gaps. |

---

## Validation Audit 2026-04-27

| Metric | Count |
|--------|-------|
| Input state | State A |
| PLAN files read | 13 |
| SUMMARY files read | 13 |
| Requirements audited | 5 |
| Task rows audited | 30 |
| Gaps found | 0 |
| Resolved by this audit | 0 |
| Escalated | 0 |
| Latest full suite | 433 passed |

---

## Validation Sign-Off

- [x] Nyquist config checked and enabled.
- [x] Input state detected as State A.
- [x] PLAN and SUMMARY artifacts read.
- [x] Requirement-to-task map rebuilt.
- [x] Test infrastructure detected.
- [x] Gaps classified as none remaining.
- [x] Full suite green with `dotnet test`.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** Nyquist-compliant.
