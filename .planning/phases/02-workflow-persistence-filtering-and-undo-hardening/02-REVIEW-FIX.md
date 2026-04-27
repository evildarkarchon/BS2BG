---
phase: 02-workflow-persistence-filtering-and-undo-hardening
fixed_at: 2026-04-27T02:11:50Z
review_path: .planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 02: Code Review Fix Report

**Fixed at:** 2026-04-27T02:11:50Z
**Source review:** .planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4
- Fixed: 4
- Skipped: 0

## Fixed Issues

### CR-01: BLOCKER — Omit preference saves can wipe remembered folder channels

**Files modified:** `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `tests/BS2BG.Tests/TemplatesViewModelTests.cs`
**Commit:** 6efc6b36
**Applied fix:** Reloaded latest preferences before saving the omit-redundant-sliders preference and added coverage for preserving updated folder channels.

### CR-02: BLOCKER — Free-text filters leave routine bulk operations defaulting to All

**Files modified:** `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`
**Commit:** b6ae9057
**Applied fix:** Free-text NPC search now switches routine bulk scope from All to Visible when filters are active, with focused bulk-operation coverage.

### CR-03: BLOCKER — Scoped NPC clearing is disabled when the selected scope has targets but visible rows are empty

**Files modified:** `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`
**Commit:** 1b8985c6
**Applied fix:** Clear NPC command availability now resolves the currently selected scope instead of relying only on visible rows.

### WR-01: WARNING — Scoped clear button still advertises visible-only behavior

**Files modified:** `src/BS2BG.App/Views/MainWindow.axaml`, `tests/BS2BG.Tests/M6UxAppShellTests.cs`
**Commit:** 0fa82ce1
**Applied fix:** Updated the clear button automation name to scope-neutral copy and asserted it in the UI shell test.

---

_Fixed: 2026-04-27T02:11:50Z_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1_
