---
phase: 01
slug: profile-correctness-and-trust
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-26
---

# Phase 01 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | xUnit v3 + FluentAssertions + Avalonia.Headless.XUnit |
| **Config file** | `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| **Quick run command** | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` |
| **Full suite command** | `dotnet test` |
| **Estimated runtime** | Repository-dependent; targeted filters are intended for per-task feedback |

---

## Sampling Rate

- **After every task commit:** Run the task-specific `dotnet test --filter ...` command named in the plan.
- **After every plan wave:** Run `dotnet test` unless a plan explicitly narrows the check to a faster cross-plan filter and the executor records why.
- **Before `/gsd-verify-work`:** Full suite must be green.
- **Max feedback latency:** Use targeted filters during tasks; full suite before phase completion.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | PROF-01, PROF-04 | T-01-01 / T-01-02 | Bundled FO4 profile data is local, deterministic, and not silently reused from Skyrim profiles | unit/integration | `dotnet test --filter "FullyQualifiedName~TemplateProfileCatalogFactoryTests|FullyQualifiedName~TemplateGenerationServiceTests"` | ✅ | ⬜ pending |
| 01-01-02 | 01 | 1 | PROF-01, PROF-04 | T-01-01 / T-01-02 | App output includes the distinct FO4 JSON file | unit/build | `dotnet test --filter FullyQualifiedName~TemplateProfileCatalogFactoryTests` | ✅ | ⬜ pending |
| 01-02-01 | 02 | 1 | PROF-02, PROF-03 | T-01-03 | Unbundled profile names remain data and fallback math is explicit to callers | unit | `dotnet test --filter "FullyQualifiedName~ProjectFileServiceTests|FullyQualifiedName~TemplateProfileCatalogTests"` | ✅ | ⬜ pending |
| 01-02-02 | 02 | 1 | PROF-02 | T-01-03 | Legacy `isUUNP` mapping remains compatible | unit | `dotnet test --filter FullyQualifiedName~ProjectFileServiceTests` | ✅ | ⬜ pending |
| 01-03-01 | 03 | 2 | PROF-03, PROF-04 | T-01-04 | Imports use selected profile and do not infer from untrusted paths or slider names | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | ✅ | ⬜ pending |
| 01-03-02 | 03 | 2 | PROF-02, PROF-03 | T-01-04 | Neutral fallback text is visible only for unresolved saved profiles and does not block generation | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | ✅ | ⬜ pending |
| 01-04-01 | 04 | 3 | PROF-03, PROF-05 | T-01-05 | UI exposes neutral fallback info without warnings or FO4 experimental labels | headless UI | `dotnet test --filter FullyQualifiedName~AppShellTests` | ✅ | ⬜ pending |
| 01-04-02 | 04 | 3 | PROF-05 | — | Release-facing profile note exists outside the main workflow and does not change app semantics | docs/test | `dotnet test --filter FullyQualifiedName~AppShellTests` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. New tests are created by the implementation plans before production code changes.

---

## Manual-Only Verifications

All phase behaviors have automated verification through unit or headless UI tests. Optional manual smoke: run the app, import an XML preset, select `Fallout 4 CBBE`, and confirm no experimental/warning label appears in the Templates toolbar.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency handled through targeted `dotnet test --filter` runs
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** draft pending execution
