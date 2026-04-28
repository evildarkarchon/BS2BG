---
phase: 06
slug: compose-custom-profiles-in-headless-generation
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-28
updated: 2026-04-28
source_state: reconstructed-from-summary
---

# Phase 06 - Validation Strategy

> Per-phase validation contract reconstructed from `06-01-PLAN.md`, `06-01-SUMMARY.md`, and the live test suite.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | xUnit v3 + FluentAssertions |
| **Config file** | `tests/BS2BG.Tests/BS2BG.Tests.csproj` |
| **Quick run command** | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~RequestScopedProfileCatalogComposerTests"` |
| **Full suite command** | `dotnet test` |
| **Estimated runtime** | ~8 seconds for Phase 6 targeted coverage; full suite runtime varies by machine |

---

## Sampling Rate

- **After every task commit:** Run the task-specific targeted command from the verification map.
- **After every plan wave:** Run `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~RequestScopedProfileCatalogComposerTests|FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"`.
- **Before `/gsd-verify-work`:** Full suite must be green with `dotnet test`.
- **Max feedback latency:** ~8 seconds for the Phase 6 targeted suite on the audited run.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | AUTO-01 | T-06-01-01 / T-06-01-03 | Composer resolves only referenced non-bundled custom profiles, filters bundled/blank/unreferenced names, deduplicates case-insensitively, and proves duplicate project definitions use the first eligible project-owned definition. | unit | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~RequestScopedProfileCatalogComposerTests"` | yes: `tests/BS2BG.Tests/RequestScopedProfileCatalogComposerTests.cs`, `tests/BS2BG.Tests/TestProfiles.cs` | green |
| 06-01-02 | 01 | 1 | AUTO-01 | T-06-01-01 / T-06-01-04 | Portable bundles use the shared request-scoped composer for validation, generated outputs, profile entries, missing-profile checks, and sanitized filename collision handling. | unit/integration | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~RequestScopedProfileCatalogComposerTests|FullyQualifiedName~PortableBundleServiceTests"` | yes: `tests/BS2BG.Tests/RequestScopedProfileCatalogComposerTests.cs`, `tests/BS2BG.Tests/PortableBundleServiceTests.cs` | green |
| 06-01-03 | 01 | 1 | AUTO-01 | T-06-01-02 / T-06-01-03 | CLI/headless generation loads a project, builds one request-scoped catalog from embedded custom profiles, blocks unresolved custom profiles, and writes BodyGen/BoS bytes through existing Core writers without external profile lookup. | service/CLI integration | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~RequestScopedProfileCatalogComposerTests|FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"` | yes: `tests/BS2BG.Tests/CliGenerationTests.cs`, `tests/BS2BG.Tests/PortableBundleServiceTests.cs` | green |

*Status: pending | green | red | flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements.

---

## Manual-Only Verifications

All phase behaviors have automated verification.

---

## Gap Audit

| Requirement | Initial Status | Gap | Resolution | Status |
|-------------|----------------|-----|------------|--------|
| AUTO-01 | partial | Duplicate project `CustomProfiles` definitions with the same name/casing were handled by implementation but lacked direct automated coverage. | Added `BuildForProjectUsesFirstEligibleProjectDuplicateDefinitionCaseInsensitively` in `RequestScopedProfileCatalogComposerTests.cs`. | resolved |

---

## Validation Audit 2026-04-28

| Metric | Count |
|--------|-------|
| Gaps found | 1 |
| Resolved | 1 |
| Escalated | 0 |

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 10 seconds for Phase 6 targeted suite
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-04-28
