---
phase: 05
slug: automation-sharing-and-release-trust
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-27
updated: 2026-04-28
---

# Phase 05 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | xUnit v3 `3.2.2` + FluentAssertions `8.9.0`; Avalonia Headless `12.0.1` for UI tests. |
| **Config file** | `Directory.Packages.props`, `Directory.Build.props`, `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| **Quick run command** | `dotnet test BS2BG.sln --filter "FullyQualifiedName~CliGeneration`&#124;`FullyQualifiedName~PortableBundle`&#124;`FullyQualifiedName~AssignmentStrategy`&#124;`FullyQualifiedName~MorphsViewModelStrategy`&#124;`FullyQualifiedName~ReleaseTrust`&#124;`FullyQualifiedName~ReleaseDocs"` |
| **Full suite command** | `dotnet test BS2BG.sln` |
| **Estimated runtime** | Targeted Phase 5 validation completed in ~8 seconds after build; full suite remains the pre-release baseline. |

---

## Sampling Rate

- **After every task commit:** Run the task's targeted `dotnet test BS2BG.sln --filter ...` command.
- **After every plan wave:** Run `dotnet test BS2BG.sln`.
- **Before `/gsd-verify-work`:** Full suite must be green, plus release/package smoke if release artifacts changed.
- **Max feedback latency:** One task; no three consecutive implementation tasks lack automated tests.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | AUTO-01 | T-05-01-01 | CLI parse errors fail safely and no App/Avalonia reference enters CLI | unit/integration | `dotnet test BS2BG.sln --filter FullyQualifiedName~CliGeneration` | ✅ `tests/BS2BG.Tests/CliGenerationTests.cs` | ✅ green |
| 05-01-02 | 01 | 1 | AUTO-01 | T-05-01-02 | Typed Core request/exit contracts prevent stringly output intent drift | unit | `dotnet test BS2BG.sln --filter FullyQualifiedName~CliGeneration` | ✅ `tests/BS2BG.Tests/CliGenerationTests.cs` | ✅ green |
| 05-02-01 | 02 | 2 | AUTO-01 | T-05-02-01/T-05-02-02 | Validation blockers and overwrite refusal prevent unsafe writes | integration | `dotnet test BS2BG.sln --filter FullyQualifiedName~CliGeneration` | ✅ `tests/BS2BG.Tests/CliGenerationTests.cs` | ✅ green |
| 05-02-02 | 02 | 2 | AUTO-01 | T-05-02-01 | CLI command propagates service exit codes and messages | integration | `dotnet test BS2BG.sln --filter FullyQualifiedName~CliGeneration` | ✅ `tests/BS2BG.Tests/CliGenerationTests.cs` | ✅ green |
| 05-03-01 | 03 | 1 | AUTO-03 | T-05-03-02 | Race rules use imported race text only | unit | `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` | ✅ `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` | ✅ green |
| 05-03-02 | 03 | 1 | AUTO-03 | T-05-03-01 | Optional strategy JSON preserves legacy project compatibility | unit | `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` | ✅ `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` | ✅ green |
| 05-04-01 | 04 | 2 | AUTO-03 | T-05-04-01 | Deterministic algorithms use stable ordering/provider seam | unit | `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` | ✅ `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` | ✅ green |
| 05-04-02 | 04 | 2 | AUTO-03 | T-05-04-02 | No-eligible rules produce diagnostics instead of fallback | unit | `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` | ✅ `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` | ✅ green |
| 05-05-01 | 05 | 3 | AUTO-03 | T-05-05-01 | Strategy UI applies through service and undo snapshots | unit/headless | `dotnet test BS2BG.sln --filter FullyQualifiedName~MorphsViewModelStrategy` | ✅ `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs` | ✅ green |
| 05-05-02 | 05 | 3 | AUTO-03 | T-05-05-02 | Accessible text labels expose reproducibility and rule gap state | headless/manual | `dotnet test BS2BG.sln --filter FullyQualifiedName~MorphsViewModelStrategy` | ✅ `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`; visual checkpoint `05-05-SUMMARY.md` | ✅ green |
| 05-06-01 | 06 | 3 | AUTO-02 | T-05-06-01/T-05-06-02 | Bundle paths are relative and privacy-scrubbed | unit | `dotnet test BS2BG.sln --filter FullyQualifiedName~PortableBundle` | ✅ `tests/BS2BG.Tests/PortableBundleServiceTests.cs` | ✅ green |
| 05-06-02 | 06 | 3 | AUTO-02 | T-05-06-01/T-05-06-03 | Zip layout and overwrite safety are asserted | integration | `dotnet test BS2BG.sln --filter FullyQualifiedName~PortableBundle` | ✅ `tests/BS2BG.Tests/PortableBundleServiceTests.cs` | ✅ green |
| 05-07-01 | 07 | 4 | AUTO-01/AUTO-02 | T-05-07-01 | CLI bundle command preserves explicit intent/overwrite safety | integration | `dotnet test BS2BG.sln --filter FullyQualifiedName~PortableBundle` | ✅ `tests/BS2BG.Tests/PortableBundleServiceTests.cs` | ✅ green |
| 05-07-02 | 07 | 4 | AUTO-02 | T-05-07-02 | GUI preview shows privacy status before bundle write | headless/manual | `dotnet test BS2BG.sln --filter FullyQualifiedName~PortableBundle` | ✅ `tests/BS2BG.Tests/PortableBundleServiceTests.cs`; visual evidence `05-07-VISUAL-VERIFICATION.md` | ✅ green |
| 05-08-01 | 08 | 1 | AUTO-04 | T-05-08-01/T-05-08-02 | Release zip has required checksum/trust artifacts | unit/script | `dotnet test BS2BG.sln --filter FullyQualifiedName~ReleaseTrust` | ✅ `tests/BS2BG.Tests/ReleaseTrustTests.cs` | ✅ green |
| 05-08-02 | 08 | 1 | AUTO-04 | T-05-08-03 | SignTool absence does not fail unsigned checksum path | unit/script | `dotnet test BS2BG.sln --filter FullyQualifiedName~ReleaseTrust` | ✅ `tests/BS2BG.Tests/ReleaseTrustTests.cs` | ✅ green |
| 05-09-01 | 09 | 2 | AUTO-05 | T-05-09-01 | Docs include no-plugin-editing boundary and no in-app wizard | docs assertion | `dotnet test BS2BG.sln --filter FullyQualifiedName~ReleaseDocs` | ✅ `tests/BS2BG.Tests/ReleaseDocsTests.cs` | ✅ green |
| 05-09-02 | 09 | 2 | AUTO-05/AUTO-04 | T-05-09-02 | Release script packages setup docs | docs assertion | `dotnet test BS2BG.sln --filter FullyQualifiedName~ReleaseDocs` | ✅ `tests/BS2BG.Tests/ReleaseDocsTests.cs` | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `tests/BS2BG.Tests/CliGenerationTests.cs` — AUTO-01 CLI and headless generation coverage.
- [x] `tests/BS2BG.Tests/PortableBundleServiceTests.cs` — AUTO-02 bundle service, CLI, and GUI preview coverage.
- [x] `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` — AUTO-03 strategy contract/execution coverage.
- [x] `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs` — App/UI strategy coverage for AUTO-03.
- [x] `tests/BS2BG.Tests/ReleaseTrustTests.cs` — AUTO-04 release trust coverage with `ReleaseSmoke` gated heavyweight checks.
- [x] `tests/BS2BG.Tests/ReleaseDocsTests.cs` — AUTO-05 packaged docs coverage with `ReleaseSmoke` gated package inspection.
- [x] `src/BS2BG.Cli/BS2BG.Cli.csproj` — CLI project required before CLI tests compile.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Morphs strategy editor visual placement and text-visible trust states | AUTO-03 | Visual density/accessibility confirmation in Avalonia shell | Execute Plan 05 checkpoint steps. |
| Portable bundle preview visual layout and overwrite affordance | AUTO-02 | Visual preview/interaction confirmation in Avalonia shell | Execute Plan 07 checkpoint steps. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency bounded by targeted test filters
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** automated Nyquist audit complete, 2026-04-28.

---

## Validation Audit 2026-04-28

| Metric | Count |
|--------|-------|
| Requirements audited | 5 |
| Task rows audited | 18 |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

| Requirement | Coverage Status | Evidence |
|-------------|-----------------|----------|
| AUTO-01 | COVERED | `CliGenerationTests.cs`, `PortableBundleServiceTests.cs`; CLI/headless generation and bundle exit-code paths passed. |
| AUTO-02 | COVERED | `PortableBundleServiceTests.cs`; bundle layout, privacy, overwrite safety, request-scoped custom profiles, CLI/GUI surfaces passed. |
| AUTO-03 | COVERED | `AssignmentStrategyServiceTests.cs`, `MorphsViewModelStrategyTests.cs`; strategy persistence, deterministic execution, diagnostics, UI apply/undo passed. |
| AUTO-04 | COVERED | `ReleaseTrustTests.cs`, `ReleaseDocsTests.cs`; checksum/signing/package-doc assertions passed, with heavyweight smoke tests explicitly `ReleaseSmoke` gated. |
| AUTO-05 | COVERED | `ReleaseDocsTests.cs`; setup guide, no-plugin-editing boundary, and no in-app wizard assertions passed. |

**Audit command:** `dotnet test BS2BG.sln --filter "FullyQualifiedName~CliGeneration|FullyQualifiedName~PortableBundle|FullyQualifiedName~AssignmentStrategy|FullyQualifiedName~MorphsViewModelStrategy|FullyQualifiedName~ReleaseTrust|FullyQualifiedName~ReleaseDocs"`

**Result:** Passed — 102 passed, 3 skipped (`ReleaseSmoke` only), 0 failed. Analyzer warnings were emitted by existing Phase 5 source/test surfaces but did not affect validation pass/fail.

**Nyquist decision:** No missing automated requirement coverage was found, so the gap-approval gate and `gsd-nyquist-auditor` test-generation step were skipped per workflow Step 3.
