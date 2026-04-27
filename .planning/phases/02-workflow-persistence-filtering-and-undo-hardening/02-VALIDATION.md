---
phase: 02
slug: workflow-persistence-filtering-and-undo-hardening
status: green
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-26
---

# Phase 02 - Validation Strategy

Per-phase Nyquist validation contract reconstructed from Phase 02 plans and execution summaries.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | .NET 10 xUnit v3 + FluentAssertions + Avalonia.Headless.XUnit |
| **Config file** | `tests/BS2BG.Tests/BS2BG.Tests.csproj`; ReactiveUI test bootstrap in `tests/BS2BG.Tests/TestModuleInitializer.cs` |
| **Quick run command** | `dotnet test --filter "FullyQualifiedName~UserPreferencesServiceTests|FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~M6UxAppShellTests|FullyQualifiedName~M6UxViewModelTests|FullyQualifiedName~NpcFilterStateTests"` |
| **Full suite command** | `dotnet test` |
| **Estimated runtime** | ~10 seconds focused, ~20 seconds full suite |

---

## Sampling Rate

- **After every task commit:** Run the task-specific `dotnet test --filter ...` command from the plan.
- **After every plan wave:** Run the focused Phase 02 command above.
- **Before `/gsd-verify-work`:** Full suite must be green or any skipped/escalated tests must be documented below.
- **Max feedback latency:** ~20 seconds.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | WORK-01 | T-02-01-01 / T-02-01-02 | Local workflow preference load/save remains compatible and best-effort. | unit | `dotnet test --filter FullyQualifiedName~UserPreferencesServiceTests` | yes | green |
| 02-01-02 | 01 | 1 | WORK-01 | T-02-01-01 / T-02-01-02 | Omit Redundant Sliders hydrates/saves locally and never enters project serialization. | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | yes | green |
| 02-02-01 | 02 | 2 | WORK-01 | T-02-02-01 / T-02-02-02 | Project/export folder preferences round-trip independently and legacy files default safely. | unit | `dotnet test --filter FullyQualifiedName~UserPreferencesServiceTests` | yes | green |
| 02-02-02 | 02 | 2 | WORK-01 | T-02-02-01 / T-02-02-02 | Project/export picker hints are advisory; invalid hints and save failures do not block selections. | unit | `dotnet test --filter FullyQualifiedName~MainWindowViewModelTests` | yes | green |
| 02-03-01 | 03 | 3 | WORK-01 | T-02-03-01 / T-02-03-02 | BodySlide XML import folders round-trip/default and invalid hints fall back without blocking import. | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | yes | green |
| 02-03-02 | 03 | 3 | WORK-01 | T-02-03-01 / T-02-03-02 | NPC text import folders round-trip/default, invalid hints fall back, and filter state is not serialized. | unit | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | yes | green |
| 02-04-01 | 04 | 1 | WORK-02, WORK-05 | T-02-04-02 | Stable NPC row IDs are generated in App workflow state and excluded from Core/project serialization. | unit | `dotnet test --filter FullyQualifiedName~NpcFilterStateTests` | yes | green |
| 02-04-02 | 04 | 1 | WORK-02, WORK-05 | T-02-04-01 | Required checklist/global search predicates are pure and cover all NPC filter columns. | unit | `dotnet test --filter FullyQualifiedName~NpcFilterStateTests` | yes | green |
| 02-05-01 | 05 | 2 | WORK-02, WORK-05 | T-02-05-01 | Morphed and imported NPC visible projections filter through stable keyed row wrappers. | unit | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | yes | green |
| 02-05-02 | 05 | 2 | WORK-02, WORK-05 | T-02-05-01 / T-02-05-02 | Hidden selection survives filters and checklist filters apply immediately while free-text search is debounced. | unit | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | yes | green |
| 02-06-01 | 06 | 3 | WORK-02 | T-02-06-01 / T-02-06-02 | Every required NPC filter popup exposes accessible automation names, search copy, clear controls, and light-dismiss behavior. | headless UI | `dotnet test --filter FullyQualifiedName~M6UxAppShellTests` | yes | green |
| 02-06-02 | 06 | 3 | WORK-02 | T-02-06-01 / T-02-06-02 | Active badges and filtered-empty copy are visible without implying data loss. | headless UI | `dotnet test --filter FullyQualifiedName~M6UxAppShellTests` | yes | green |
| 02-07-01 | 07 | 4 | WORK-03, WORK-05 | T-02-07-01 | Scope resolver materializes all, visible, selected, and visible-empty stable row IDs before mutation. | unit | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | yes | green |
| 02-07-02 | 07 | 4 | WORK-03, WORK-05 | T-02-07-01 / T-02-07-02 | Scoped bulk operations avoid hidden rows, confirm destructive all-scope changes, and cancellation leaves rows untouched. | unit | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | yes | green |
| 02-07-03 | 07 | 4 | WORK-03 | T-02-07-02 | Scope selector and Fill Visible Empty copy are present in Morphs UI. | headless UI | `dotnet test --filter FullyQualifiedName~M6UxAppShellTests` | yes | green |
| 02-08-01 | 08 | 2 | WORK-04, WORK-05 | T-02-08-02 | Undo history is bounded, prunes oldest entries, clears redo on new records, and reports shell status. | unit | `dotnet test --filter FullyQualifiedName~M6UxViewModelTests` | yes | green |
| 02-08-02 | 08 | 2 | WORK-04, WORK-05 | T-02-08-01 | Template undo uses value snapshots for import, rename, duplicate, remove, clear, profile selection, and bulk slider percent paths. | unit | `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | yes | green |
| 02-09-01 | 09 | 5 | WORK-03, WORK-04, WORK-05 | T-02-09-01 | Custom target and NPC row operations replay from scalar snapshots instead of detached live references. | unit | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | yes | green |
| 02-09-02 | 09 | 5 | WORK-03, WORK-04, WORK-05 | T-02-09-01 / T-02-09-02 | Scoped bulk assignment undo records one operation, resolves by stable IDs/preset names, and respects bounded history pruning. | unit | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | yes | green |

*Status: green = covered by passing automated tests.*

---

## Wave 0 Requirements

Existing infrastructure covers all Phase 02 validation requirements. No new test framework, fixtures, or config files were required.

---

## Generated Nyquist Tests

| Gap | Generated Coverage | Test File |
|-----|--------------------|-----------|
| Project/export picker invalid hints and save failures | Invalid `ProjectFolder` hint fallback; preference save failures still return project/export picker results. | `tests/BS2BG.Tests/MainWindowViewModelTests.cs` |
| Import-folder defaults, round-trip, and invalid hints | Legacy import folder defaults; independent import folder round-trip; XML/NPC picker invalid remembered-folder fallback. | `tests/BS2BG.Tests/UserPreferencesServiceTests.cs`, `tests/BS2BG.Tests/TemplatesViewModelTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs` |
| Imported NPC filtering and debounce semantics | Imported `VisibleNpcDatabase` projection after debounce; checklist filters apply immediately while free-text waits for debounce. | `tests/BS2BG.Tests/MorphsViewModelTests.cs` |
| Filter UI accessibility/dismissal | Filter buttons expose automation names and popups are light-dismissable. | `tests/BS2BG.Tests/M6UxAppShellTests.cs` |
| Destructive all-scope row clear | All-scope row clear prompts with destructive copy; cancellation leaves model and visible rows untouched. | `tests/BS2BG.Tests/MorphsViewModelTests.cs` |
| Template snapshot edge paths | Rename, duplicate, and bulk slider percent undo snapshot regressions pass. | `tests/BS2BG.Tests/TemplatesViewModelTests.cs` |
| Morph/NPC scoped undo edge paths | Custom target assignment replay skips renamed preset references; repeated scoped bulk operations respect bounded pruning. | `tests/BS2BG.Tests/MorphsViewModelTests.cs` |

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| None. | - | The previously skipped bulk slider percent undo regression is now active and passes. | - |

---

## Validation Audit 2026-04-26

| Metric | Count |
|--------|-------|
| Gaps found | 7 |
| Resolved | 7 |
| Escalated | 0 |

| Command | Result |
|---------|--------|
| `dotnet test --filter "FullyQualifiedName~UserPreferencesServiceTests|FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~M6UxAppShellTests|FullyQualifiedName~M6UxViewModelTests|FullyQualifiedName~NpcFilterStateTests"` | Passed: 136, Skipped: 0, Failed: 0 |
| `dotnet test` | Passed: 301, Skipped: 0, Failed: 0 |

### Gap Closure 2026-04-26

| Command | Result |
|---------|--------|
| `dotnet test --filter FullyQualifiedName~BulkSliderPercentUndoRestoresOperationTimeValuesAfterInterveningMutation` | Passed: 1, Skipped: 0, Failed: 0 |
| `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` | Passed: 29, Skipped: 0, Failed: 0 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify coverage.
- [x] Sampling continuity: no 3 consecutive tasks lack automated verification.
- [x] Wave 0 covers all missing references.
- [x] No watch-mode flags.
- [x] Feedback latency < 20 seconds for focused validation.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** green 2026-04-26; all known Phase 02 Nyquist gaps are covered by passing automated tests.
