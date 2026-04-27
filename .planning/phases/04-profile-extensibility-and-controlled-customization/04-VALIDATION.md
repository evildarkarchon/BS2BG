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
| **Framework** | xUnit v3 3.2.2 + FluentAssertions 8.9.0 + Avalonia.Headless.XUnit 12.0.1 |
| **Config file** | `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| **Quick run command** | `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` |
| **Full suite command** | `dotnet test` |
| **Estimated runtime** | focused tests < 60 seconds; full suite project-dependent |

---

## Sampling Rate

- **After every task commit:** Run the focused command for the touched test class, e.g. `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests`.
- **After every plan wave:** Run `dotnet test --filter "FullyQualifiedName~Profile|FullyQualifiedName~ProjectFileService|FullyQualifiedName~TemplatesViewModel|FullyQualifiedName~DiagnosticsViewModel"`.
- **Before `/gsd-verify-work`:** `dotnet test` must be green.
- **Max feedback latency:** < 60 seconds for focused test feedback.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | EXT-01, EXT-02 | T-04-01 | Malformed, duplicate, ambiguous, or nonnumeric profile JSON is rejected before catalog inclusion while blank finite profiles remain valid. | unit | `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` | ❌ W0 | ⬜ pending |
| 04-02-01 | 02 | 1 | EXT-01 | T-04-02 | User-local custom profile writes are atomic and cannot overwrite bundled profile files. | unit/service | `dotnet test --filter FullyQualifiedName~UserProfileStoreTests` | ❌ W0 | ⬜ pending |
| 04-03-01 | 03 | 2 | EXT-01, EXT-02 | T-04-03 | Catalog composition rejects case-insensitive duplicate profile names across bundled and custom profiles. | unit | `dotnet test --filter "FullyQualifiedName~TemplateProfileCatalogFactoryTests|FullyQualifiedName~ProfileDefinitionServiceTests"` | existing + ❌ W0 | ⬜ pending |
| 04-04-01 | 04 | 2 | EXT-03, EXT-05 | T-04-04 | Project save embeds only referenced custom profiles and preserves legacy `SliderPresets`, `CustomMorphTargets`, `MorphedNPCs`, `isUUNP`, and `Profile` fields. | unit/integration | `dotnet test --filter FullyQualifiedName~ProjectFileServiceCustomProfileTests` | ❌ W0 | ⬜ pending |
| 04-05-01 | 05 | 3 | EXT-04 | T-04-05 | Missing custom profile references remain non-blocking but visible, and exact display-name imports resolve unresolved references. | unit/ViewModel | `dotnet test --filter "FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests|FullyQualifiedName~TemplatesViewModel"` | existing + ❌ W0 | ⬜ pending |
| 04-06-01 | 06 | 3 | EXT-01, EXT-02, EXT-05 | T-04-06 | Profile manager/editor commands validate before save/import/export and expose conflict choices without silent replacement. | ViewModel/headless UI | `dotnet test --filter "FullyQualifiedName~ProfileManagerViewModelTests|FullyQualifiedName~ProfileEditorViewModelTests"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs` — covers EXT-01/EXT-02 parse, validation, duplicate names, blank profile, broad finite floats, and export round-trip rules.
- [ ] `tests/BS2BG.Tests/UserProfileStoreTests.cs` — covers local AppData profile folder behavior, atomic writes, discovery, sanitized filenames, and bundled-file protection.
- [ ] `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs` — covers EXT-03/EXT-05 optional embedded `CustomProfiles` section and legacy field preservation.
- [ ] `tests/BS2BG.Tests/ProfileRecoveryDiagnosticsServiceTests.cs` — covers EXT-04 exact-match recovery, conflict detection, and neutral unresolved state.
- [ ] `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` and/or `tests/BS2BG.Tests/ProfileEditorViewModelTests.cs` — covers App profile management and validation-gated commands if new ViewModels are created.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Profile manager/editor UI is discoverable, compiled-bound, and presents import/copy/export/edit/recovery actions with neutral wording. | EXT-01, EXT-02, EXT-04, EXT-05 | Visual layout and interaction affordance verification needs human review in the desktop UI. | Run `dotnet run --project src/BS2BG.App/BS2BG.App.csproj`, open the profile-management surface, verify bundled profiles are read-only, custom profiles expose edit/export actions, malformed imports show validation diagnostics, and unresolved profile recovery actions are visible without blocking generation. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Focused feedback latency target < 60 seconds
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
