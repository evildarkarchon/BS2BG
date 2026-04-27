---
phase: 01
slug: profile-correctness-and-trust
status: verified
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-26
updated: 2026-04-26
---

# Phase 01 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | xUnit v3 + FluentAssertions + Avalonia.Headless.XUnit |
| **Config file** | `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| **Quick run command** | `dotnet test --filter "FullyQualifiedName~TemplateProfileCatalogFactoryTests|FullyQualifiedName~TemplateGenerationServiceTests|FullyQualifiedName~ProjectFileServiceTests|FullyQualifiedName~TemplateProfileCatalogTests|FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~AppShellTests|FullyQualifiedName~SliderMathFormatterTests|FullyQualifiedName~ExportWriterTests|FullyQualifiedName~MorphCoreTests"` |
| **Full suite command** | `dotnet test` |
| **Estimated runtime** | Focused Phase 1 suite and full suite each complete in seconds on the current workspace |

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
| 01-01-01 | 01 | 1 | PROF-01, PROF-04 | T-01-01 / T-01-02 | Bundled FO4 profile data is local, deterministic, and not silently reused from Skyrim profiles | unit/integration | `dotnet test --filter "FullyQualifiedName~TemplateProfileCatalogFactoryTests|FullyQualifiedName~TemplateGenerationServiceTests"` | ✅ | ✅ green |
| 01-01-02 | 01 | 1 | PROF-01, PROF-04 | T-01-01 / T-01-02 | App output includes the distinct FO4 JSON file | unit/build | `dotnet test --filter FullyQualifiedName~TemplateProfileCatalogFactoryTests` | ✅ | ✅ green |
| 01-02-01 | 02 | 1 | PROF-02, PROF-03 | T-01-03 | Unbundled profile names remain data and fallback math is explicit to callers | unit | `dotnet test --filter "FullyQualifiedName~ProjectFileServiceTests|FullyQualifiedName~TemplateProfileCatalogTests"` | ✅ | ✅ green |
| 01-02-02 | 02 | 1 | PROF-02 | T-01-03 | Legacy `isUUNP` mapping remains compatible | unit | `dotnet test --filter FullyQualifiedName~ProjectFileServiceTests` | ✅ | ✅ green |
| 01-03-01 | 03 | 2 | PROF-03, PROF-04 | T-01-04 | Imports use selected profile and do not infer from untrusted paths or slider names | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | ✅ | ✅ green |
| 01-03-02 | 03 | 2 | PROF-02, PROF-03 | T-01-04 | Neutral fallback text is visible only for unresolved saved profiles and does not block generation | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | ✅ | ✅ green |
| 01-04-01 | 04 | 3 | PROF-03, PROF-05 | T-01-05 | UI exposes neutral fallback info without warnings or FO4 experimental labels | headless UI | `dotnet test --filter FullyQualifiedName~AppShellTests` | ✅ | ✅ green |
| 01-04-02 | 04 | 3 | PROF-05 | — | Release-facing profile note exists outside the main workflow and does not change app semantics | docs/test | `dotnet test --filter FullyQualifiedName~AppShellTests` | ✅ | ✅ green |
| 01-05-01 | 05 | 4 | PROF-02, PROF-03, PROF-04 | T-01-13 / T-01-14 / T-01-15 | Unbundled saved profile names keep selector state blank until explicit bundled adoption | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | ✅ | ✅ green |
| 01-05-02 | 05 | 4 | PROF-02, PROF-03, PROF-04 | T-01-13 / T-01-14 / T-01-15 | Fallback calculation profile drives preview, missing-default, inspector, and BoS JSON paths without rewriting saved names | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | ✅ | ✅ green |
| 01-06-01 | 06 | 4 | PROF-01, PROF-04 | T-01-16 / T-01-18 | BoS JSON is protected by profile-specific Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE assertions | unit | `dotnet test --filter FullyQualifiedName~SliderMathFormatterTests` | ✅ | ✅ green |
| 01-06-02 | 06 | 4 | PROF-01, PROF-04 | T-01-17 / T-01-18 | BodyGen export combines profile-specific templates with profile-independent morph output | unit/integration | `dotnet test --filter "FullyQualifiedName~ExportWriterTests|FullyQualifiedName~MorphCoreTests"` | ✅ | ✅ green |
| 01-07-01 | 07 | 4 | PROF-03, PROF-05 | T-01-19 / T-01-20 / T-01-21 | Roadmap contract records neutral fallback and release-facing FO4 context instead of prohibited warning UX | docs/sdk | `gsd-sdk query roadmap.get-phase "1"` | ✅ | ✅ green |
| 01-07-02 | 07 | 4 | PROF-03, PROF-05 | T-01-19 / T-01-20 / T-01-21 | Requirements contract records the accepted D-05 through D-08 override for PROF-03 and PROF-05 | docs/sdk | `gsd-sdk query init.plan-phase "1"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. Implementation plans created the required tests before or alongside production changes; the Nyquist audit found no remaining automated coverage gaps.

---

## Manual-Only Verifications

All phase behaviors have automated verification through unit, headless UI, or SDK contract checks. Optional manual smoke remains visual-only: run the app, open/select a preset with an unbundled saved profile such as `Community CBBE`, and confirm the neutral fallback panel is readable in light and dark themes with no experimental/warning label in the Templates toolbar.

---

## Validation Audit 2026-04-26

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Map rows added | 6 |
| Automated checks run | 5 |

| Check | Result |
|-------|--------|
| Focused Phase 1 suite | ✅ Passed 136/136 |
| Full test suite | ✅ Passed 286/286 |
| Sacred expected fixtures | ✅ No changes under `tests/fixtures/expected` |
| Roadmap Phase 1 contract | ✅ `found: true` |
| Requirement IDs | ✅ `PROF-01, PROF-02, PROF-03, PROF-04, PROF-05` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency handled through targeted `dotnet test --filter` runs
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** verified by Nyquist audit on 2026-04-26
