---
phase: 04-profile-extensibility-and-controlled-customization
verified: 2026-04-27T11:17:26Z
status: gaps_found
score: 3/5 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 1/5
  gaps_closed:
    - "Selectable Profiles rows are wired through ListBox SelectedItem to ProfileManagerViewModel.SelectedProfile."
    - "Copy-as-custom captures the selected bundled source before clearing selection."
    - "Game metadata is preserved through editor construction and selected JSON export."
    - "Profile table add/remove controls and live row validation are implemented for Defaults, Multipliers, and Inverted rows."
    - "GUI project saves call ProjectFileService.SaveToString(project, ProjectSaveContext) with local/project custom profiles."
    - "Embedded/local conflict rename validation uses source-specific occupancy checks."
  gaps_remaining:
    - "Created and copied custom profiles cannot be saved from the visible Profiles UI because manager SaveProfileCommand still requires a selected LocalCustom catalog row."
    - "Declining unsaved-edit discard after row selection leaves SelectedProfile on the newly clicked row while the editor still shows the old row."
    - "Catalog/search refresh rebuilds selection and editor without preserving or prompting for unsaved edits."
  regressions: []
gaps:
  - truth: "User can import, copy, export, and validate local JSON profile files without corrupting bundled profiles or existing projects."
    status: failed
    reason: "Profile row targeting and metadata gaps were mostly closed, but review CR-02 remains: a declined unsaved-edit selection change leaves SelectedProfile on the newly clicked row, so row-scoped delete/export/save commands can target the wrong profile while the editor still represents the old one. Review WR-02 also remains for unhandled import/export file I/O failures."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs"
        issue: "TrySelectProfileAsync only raises PropertyChanged when discard is declined; it does not restore the previously committed SelectedProfile after the ListBox one-way-to-source binding has already set a new row. Import/export methods call File.ReadAllTextAsync/File.WriteAllTextAsync without recoverable status handling."
      - path: "src/BS2BG.App/Views/MainWindow.axaml"
        issue: "Rows are now selectable, but direct SelectedItem binding mutates SelectedProfile before unsaved-discard confirmation."
    missing:
      - "Track committed profile selection separately and roll SelectedProfile back when discard is declined, or route row selection through a command that prompts before mutating SelectedProfile."
      - "Handle expected profile import/export IOException/UnauthorizedAccessException failures with actionable StatusMessage while preserving selection/editor state."
  - truth: "User can edit supported profile metadata and slider tables through workflows that reject malformed or ambiguous profile data."
    status: failed
    reason: "Profile table authoring and live validation are implemented, but review CR-01 and CR-03 remain: blank/copied editor candidates cannot be saved from the visible Save Profile button, and search/catalog refresh can silently replace an unsaved editor buffer."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs"
        issue: "SaveProfileCommand can execute only when SelectedProfile.SourceKind == LocalCustom, while CreateBlankProfile and CopyBundledProfile set SelectedProfile = null before creating the editable candidate. RefreshProfileEntries rebuilds SelectedProfile and Editor on SearchText/CatalogChanged without unsaved-change protection."
      - path: "src/BS2BG.App/Views/MainWindow.axaml"
        issue: "The visible Save Profile button binds to ProfileManagerViewModel.SaveProfileCommand, so created/copied unsaved local candidates have no saveable UI path."
    missing:
      - "Gate manager SaveProfileCommand on a valid dirty editor candidate, using SelectedProfile?.FilePath only for existing local custom profiles."
      - "Preserve the active editor across SearchText/catalog refresh when it has unsaved changes, or prompt before replacing it."
      - "Add regression tests for create blank/copy -> valid name -> Save Profile executable, declined row selection rollback, search refresh preserving unsaved edits, and catalog refresh preserving unsaved edits."
---

# Phase 4: Profile Extensibility and Controlled Customization Verification Report

**Phase Goal:** Users can create, validate, share, and recover local profile definitions without silent fallback or damage to bundled and legacy-compatible profile data.  
**Verified:** 2026-04-27T11:17:26Z  
**Status:** gaps_found  
**Re-verification:** Yes — after gap-closure plans 04-09, 04-10, and 04-11 plus advisory review in `04-REVIEW.md`.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | User can import, copy, export, and validate local JSON profile files without corrupting bundled profiles or existing projects. | ✗ FAILED | Row selection, copy source capture, and Game export are now present (`MainWindow.axaml:1432-1507`, `ProfileManagerViewModel.cs:315-329`, `ProfileManagerViewModel.cs:519-520`), but CR-02 remains: declined unsaved selection changes do not restore the old `SelectedProfile` (`ProfileManagerViewModel.cs:138-153`). WR-02 remains for unhandled profile JSON file I/O (`ProfileManagerViewModel.cs:210`, `292`, `366`). |
| 2 | User can edit supported profile metadata and slider tables through workflows that reject malformed or ambiguous profile data. | ✗ FAILED | Add/remove and live validation exist (`ProfileEditorViewModel.cs:66-83`, `188-208`, `238-285`; `MainWindow.axaml:1641-1714`), but CR-01 remains: visible Save Profile is disabled for create/copy candidates because manager save requires `SelectedProfile.SourceKind == LocalCustom` while create/copy clear selection (`ProfileManagerViewModel.cs:69-82`, `309-329`; `MainWindow.axaml:1581-1585`). CR-03 remains: refresh/search rebuilds the editor without unsaved protection (`ProfileManagerViewModel.cs:93-96`, `370-415`). |
| 3 | User can save projects that reference custom profiles while preserving legacy compatibility fields for older `.jbs2bg` consumers. | ✓ VERIFIED | GUI save now calls `projectFileService.SaveToString(project, BuildProjectSaveContext())` (`MainWindowViewModel.cs:888-908`); `BuildProjectSaveContext` includes local and project custom profiles case-insensitively (`MainWindowViewModel.cs:927-941`); Core serializer embeds only referenced non-bundled profiles while retaining legacy fields (`ProjectFileService.cs:193-263`, `400-418`). |
| 4 | User can resolve missing custom profile references through clear diagnostics rather than silent fallback. | ✓ VERIFIED | Recovery diagnostics and action routing remain implemented: exact-name recovery in `ProfileRecoveryDiagnosticsService`, project-copy activation in `ProfileManagerViewModel.cs:247-255`, and open status for visible fallback in `MainWindowViewModel.cs:527-532`. |
| 5 | User can bundle or copy project-specific profiles when sharing a project with another machine. | ✓ VERIFIED | Referenced local/project custom definitions flow into app saves via `ProjectSaveContext` (`MainWindowViewModel.cs:907`, `931-940`) and Core referenced-only filtering (`ProjectFileService.cs:209-243`). Selected local/embedded profile JSON export preserves `Name` and `Game` via `ToCustomProfileDefinition` (`ProfileManagerViewModel.cs:354-367`, `519-520`). |

**Score:** 3/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/BS2BG.Core/Generation/ProfileDefinitionService.cs` | Strict custom profile JSON validation/export | ✓ VERIFIED | Validates schema, duplicates, versions, finite numeric data, and exports deterministic JSON with `Name`/`Game`/tables. |
| `src/BS2BG.App/Services/UserProfileStore.cs` | AppData local custom profile discovery/storage | ✓ VERIFIED | Prior source and tests support validated AppData profile discovery and atomic save/delete. |
| `src/BS2BG.Core/Serialization/ProjectFileService.cs` | Legacy-compatible optional `CustomProfiles` serialization | ✓ VERIFIED | Optional `CustomProfiles` appended after legacy fields and filtered to referenced non-bundled profiles. |
| `src/BS2BG.Core/Diagnostics/ProfileRecoveryDiagnosticsService.cs` | Missing-profile diagnostics and exact-match resolution | ✓ VERIFIED | Neutral missing custom profile recovery semantics and internal-name exact matching are in place. |
| `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` | Profile manager workflows | ✗ FAILED | Selection/copy/export fixes landed, but create/copy save gating, declined-selection rollback, unsaved refresh preservation, and profile import/export I/O handling remain incomplete. |
| `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs` | Metadata/table editing with validation gating | ⚠️ PARTIAL | Add/remove and row validation are implemented; search still filters only Defaults (`VisibleDefaultRows`) while Multipliers/Inverted bind directly to full collections. |
| `src/BS2BG.App/Views/MainWindow.axaml` | First-class Profiles workspace UI | ⚠️ PARTIAL | Profiles tab, selectable rows, and table authoring controls exist; visible Save Profile command is still manager-gated in a way that excludes create/copy editor candidates. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `MainWindow.axaml` profile source rows | `ProfileManagerViewModel.SelectedProfile` | `ListBox SelectedItem` one-way-to-source | ✓ WIRED | Bundled/custom/embedded/missing lists all bind `SelectedItem` to `SelectedProfile`. |
| `ProfileManagerViewModel.CopyBundledProfile` | `ProfileEditorViewModel.FromProfile` | captured selected row before clearing | ✓ WIRED | `var selected = SelectedProfile` is captured before `SelectedProfile = null`; editor uses selected Name/Game/tables. |
| `ProfileEditorViewModel` row properties | validation/save state | `PropertyChanged` subscriptions | ✓ WIRED | Defaults, Multipliers, and Inverted row subscriptions call validation on edits. |
| `MainWindow.axaml` editor buttons | table commands | compiled-bound command bindings | ✓ WIRED | Add/remove command bindings exist for Defaults, Multipliers, and Inverted. |
| `MainWindowViewModel.SaveProjectInternalAsync` | `ProjectFileService.SaveToString(project, ProjectSaveContext)` | `BuildProjectSaveContext()` | ✓ WIRED | Normal GUI save calls the context-aware overload. |
| `MainWindowViewModel.ValidateRenameDecisions` | local profile names | source-specific occupancy sets | ✓ WIRED | Local names remain separately occupied; no single `occupied.Remove(conflict.Embedded.Name)` pattern remains. |
| `CreateBlankProfile` / `CopyBundledProfile` | visible `Save Profile` button | manager `SaveProfileCommand` | ✗ NOT_WIRED | Editor candidate is local and validatable, but save command canExecute is based on selected existing LocalCustom row and create/copy clear selection. |
| `ListBox SelectedItem` row selection | unsaved discard protection | `TrySelectProfileAsync` | ⚠️ PARTIAL | Prompt exists, but decline does not restore previous committed selection after binding mutation. |
| `SearchText` / `CatalogChanged` | editor preservation | `RefreshProfileEntries` | ✗ NOT_WIRED | Refresh replaces `SelectedProfile`/`Editor` without checking `Editor.HasUnsavedChanges`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| Profiles source lists | `BundledProfiles`, `CustomProfiles`, `EmbeddedProjectProfiles`, `MissingProfiles` | `catalogService.Current.Entries` plus project missing refs | Yes | ✓ FLOWING |
| Custom profile editor rows | `DefaultRows`, `MultiplierRows`, `InvertedRows` | Selected entry or blank/copy commands | Yes | ✓ FLOWING |
| Project `CustomProfiles` save JSON | referenced custom profile names | `ProjectModel.CustomProfiles` plus `ProjectSaveContext.AvailableCustomProfilesByName` | Yes | ✓ FLOWING |
| Created/copied editor candidate save | `Editor.BuildProfile(...)` | `SaveProfileCommand` from visible Profiles UI | No for create/copy | ✗ HOLLOW — candidate data exists but command gating prevents user save. |
| Unsaved editor buffer on search/catalog refresh | `Editor` | `RefreshProfileEntries()` | No preservation | ✗ HOLLOW — refresh recreates editor from catalog rows and can drop unsaved data. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Focused Phase 4 regression suite | `dotnet test --filter "FullyQualifiedName~ProfileManagerViewModelTests\|FullyQualifiedName~ProfileEditorViewModelTests\|FullyQualifiedName~MainWindowViewModelTests\|FullyQualifiedName~MainWindowViewModelProfileRecoveryTests\|FullyQualifiedName~MainWindowHeadlessTests\|FullyQualifiedName~ProjectFileServiceCustomProfileTests"` | 86 passed, 0 failed | ✓ PASS |
| Source-level CR-01 check | Inspect manager save gating and create/copy selection handling | `SaveProfileCommand` uses `selectedIsCustom`; create/copy set `SelectedProfile = null` | ✗ FAIL |
| Source-level CR-02 check | Inspect declined discard path | Decline branch raises `PropertyChanged` but does not restore previous row | ✗ FAIL |
| Source-level CR-03 check | Inspect refresh paths | `SearchText`/`CatalogChanged` call `RefreshProfileEntries`, which recreates `Editor` | ✗ FAIL |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| EXT-01 | 04-01, 04-02, 04-05, 04-07, 04-08, 04-09 | Import, copy, export, and validate local JSON profile files without corrupting bundled profiles/projects. | ✗ BLOCKED | Core validation/storage/export and selectable rows exist, but wrong-target selection after declined discard can affect row-scoped actions, and profile import/export I/O failures remain unhandled. |
| EXT-02 | 04-01, 04-05, 04-08, 04-09, 04-10 | Edit supported metadata and slider tables through validated workflows. | ✗ BLOCKED | Add/remove/live validation exists, but blank/copy custom profile candidates cannot be saved from the visible UI and unsaved edits can be silently replaced by search/catalog refresh. |
| EXT-03 | 04-03, 04-06, 04-07, 04-11 | Save projects referencing custom profiles while preserving legacy compatibility fields. | ✓ SATISFIED | GUI save context is wired, and Core serializer preserves legacy fields while appending optional referenced `CustomProfiles`. |
| EXT-04 | 04-04, 04-06, 04-07, 04-08 | Resolve missing custom profile references through clear diagnostics rather than silent fallback. | ✓ SATISFIED | Missing profile diagnostics/action routing remain explicit, neutral, and exact-name based. |
| EXT-05 | 04-03, 04-06, 04-07, 04-08, 04-09, 04-11 | Bundle or copy project-specific profiles when sharing a project. | ✓ SATISFIED | App saves embed referenced local/project custom profiles; selected local/embedded JSON export preserves metadata. |

### Code Review Finding Coverage

| Finding | Classification | Status | Evidence |
|---|---|---|---|
| CR-01: Created/copied profiles cannot be saved from Profiles UI | BLOCKER | ✗ OPEN | `SaveProfileCommand` requires selected local custom row; `CreateBlankProfile`/`CopyBundledProfile` clear selection before the user saves. |
| CR-02: Declining unsaved-edits prompt leaves `SelectedProfile` on newly clicked row | BLOCKER | ✗ OPEN | Decline branch in `TrySelectProfileAsync` only raises property changed; it does not restore a committed previous row. |
| CR-03: Catalog/search refresh silently replaces unsaved profile editor | BLOCKER | ✗ OPEN | `SearchText` and `CatalogChanged` call `RefreshProfileEntries`, which resets selection/editor without prompting. |
| WR-01: Profile editor search only filters Defaults rows | WARNING | ⚠️ OPEN | Only `VisibleDefaultRows` exists; AXAML binds Multipliers/Inverted directly to full row collections. |
| WR-02: Profile import/export file I/O failures escape without user-facing recovery status | WARNING | ⚠️ OPEN | Profile manager file reads/writes are direct awaited calls without local try/catch/status handling. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---:|---|---|---|
| `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` | 81 | `SaveProfileCommand` canExecute tied only to selected LocalCustom row | 🛑 Blocker | New blank/copied profile editor candidates cannot be saved from the visible UI. |
| `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` | 141-153 | Declined discard prompt does not restore selection | 🛑 Blocker | Row-scoped commands can operate on a newly clicked row while editor still shows old unsaved data. |
| `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` | 93-96, 370-415 | Refresh recreates editor from catalog/search without unsaved guard | 🛑 Blocker | Search/catalog refresh can silently drop unsaved profile edits. |
| `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs` | 463-468 | Only Defaults search projection exists | ⚠️ Warning | Filter profile sliders does not filter Multipliers or Inverted rows. |
| `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` | 210, 292, 366 | Direct profile import/export file I/O | ⚠️ Warning | Expected local I/O failures can escape without actionable Profiles workspace status. |

### Human Verification Required

None. The Phase 04 Profiles workspace visual checkpoint was previously approved, and the remaining gaps are source-verifiable.

### Gaps Summary

Plans 04-09, 04-10, and 04-11 closed the original source-level gaps around row selection, copy-as-custom source capture, Game metadata preservation, table add/remove/live validation, GUI project save embedding, and conflict rename validation. However, the advisory review found three user-facing blockers that remain present in the codebase. The phase goal is still not achieved because users can lose unsaved profile edits, can leave commands targeting a different row than the editor after declining discard, and cannot save newly created or copied custom profile candidates through the visible Profiles UI.

---

_Verified: 2026-04-27T11:17:26Z_  
_Verifier: the agent (gsd-verifier)_
