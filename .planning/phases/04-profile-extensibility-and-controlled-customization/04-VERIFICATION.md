---
phase: 04-profile-extensibility-and-controlled-customization
verified: 2026-04-27T09:55:35Z
status: gaps_found
score: 1/5 must-haves verified
overrides_applied: 0
gaps:
  - truth: "User can import, copy, export, and validate local JSON profile files without corrupting bundled profiles or existing projects."
    status: failed
    reason: "Copy-as-custom clears the selected bundled profile before reading it, and Profiles workspace rows are rendered with non-selectable ItemsControls, so user-visible copy/export/edit workflows cannot reliably target the intended row."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs"
        issue: "CopyBundledProfile sets SelectedProfile = null before calling SelectedProfileNameForCopy() and SelectedProfileSliderProfileForCopy(), producing an empty editor instead of a bundled-profile copy."
      - path: "src/BS2BG.App/Views/MainWindow.axaml"
        issue: "Profile source rows use ItemsControl without SelectedItem binding or row selection command, so the UI cannot select arbitrary bundled/custom/embedded/missing rows."
    missing:
      - "Capture the selected row before clearing selection in CopyBundledProfile."
      - "Make profile rows selectable or add explicit row commands that update ProfileManagerViewModel.SelectedProfile."
  - truth: "User can edit supported profile metadata and slider tables through workflows that reject malformed or ambiguous profile data."
    status: failed
    reason: "The editor drops Game metadata, exposes no add/remove row controls for blank/custom profile table authoring, and row value edits do not trigger validation/gating updates."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs"
        issue: "FromEntry passes string.Empty for Game; row property changes are not subscribed after construction, so value edits do not automatically refresh IsValid/ValidationRows."
      - path: "src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs"
        issue: "ProfileManagerEntryViewModel.ToCustomProfileDefinition exports/saves Game as string.Empty."
      - path: "src/BS2BG.App/Views/MainWindow.axaml"
        issue: "Defaults, Multipliers, and Inverted sections display existing rows only; no add/remove row controls are wired."
    missing:
      - "Preserve Game metadata from stored/custom/embedded profile definitions through editor and export/save paths."
      - "Add validated add/remove controls or commands for Defaults, Multipliers, and Inverted rows."
      - "Subscribe to row property changes so validation and Save canExecute track current edits."
  - truth: "User can save projects that reference custom profiles while preserving legacy compatibility fields for older .jbs2bg consumers."
    status: failed
    reason: "Core serialization supports ProjectSaveContext, but the GUI save path calls SaveToString(project) without context, so referenced local custom profiles absent from ProjectModel.CustomProfiles are not embedded on normal app save."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/MainWindowViewModel.cs"
        issue: "SaveProjectInternalAsync line 894 uses projectFileService.SaveToString(project) instead of the overload with a ProjectSaveContext built from local/runtime custom profiles."
      - path: "src/BS2BG.Core/Serialization/ProjectFileService.cs"
        issue: "The required SaveToString(ProjectModel, ProjectSaveContext?) resolver exists and embeds context profiles, but app wiring bypasses it."
    missing:
      - "Build a case-insensitive save context from local custom/project custom profiles and call SaveToString(project, context) in GUI save."
      - "Add an app-level save test proving referenced local custom profiles serialize under CustomProfiles."
  - truth: "User can bundle or copy project-specific profiles when sharing a project with another machine."
    status: failed
    reason: "Project sharing depends on referenced profile embedding, and normal GUI saves omit local custom definitions because no save context is supplied. Selected JSON export is also undermined by non-selectable profile rows and metadata loss."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/MainWindowViewModel.cs"
        issue: "GUI project save path does not embed referenced local custom profiles."
      - path: "src/BS2BG.App/Views/MainWindow.axaml"
        issue: "Custom/embedded profile rows cannot be selected from the UI, limiting selected-profile JSON export to auto-selected state."
      - path: "src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs"
        issue: "ExportSelectedProfileAsync reports success only after write, but ToCustomProfileDefinition drops Game metadata."
    missing:
      - "Use ProjectSaveContext on GUI saves."
      - "Wire row selection and preserve metadata for selected-profile export."
  - truth: "Embedded/local same-name profile conflicts require explicit, unambiguous decisions before local data changes."
    status: failed
    reason: "Rename validation removes the embedded profile name from a single occupied-name set; for embedded/local conflicts with the same display name, this also removes local occupancy and can allow Rename Project Copy to keep or choose an existing local custom profile name."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/MainWindowViewModel.cs"
        issue: "ValidateRenameDecisions uses one HashSet for bundled/local/embedded names and occupied.Remove(conflict.Embedded.Name), which can remove the local same-name conflict too."
    missing:
      - "Validate rename choices against local profile names separately or track occupied-name counts/source identities."
      - "Add tests for renaming to the original conflicted local name and another local custom profile name."
---

# Phase 4: profile-extensibility-and-controlled-customization Verification Report

**Phase Goal:** Users can create, validate, share, and recover local profile definitions without silent fallback or damage to bundled and legacy-compatible profile data.
**Verified:** 2026-04-27T09:55:35Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can import, copy, export, and validate local JSON profile files without corrupting bundled profiles or existing projects. | ✗ FAILED | `ProfileDefinitionService` and `UserProfileStore` exist, but `CopyBundledProfile` clears `SelectedProfile` before reading it (`ProfileManagerViewModel.cs:315-328`), and rows are not selectable in `MainWindow.axaml:1432-1514`. |
| 2 | User can edit supported profile metadata and slider tables through workflows that reject malformed or ambiguous profile data. | ✗ FAILED | Validation service exists, but `FromEntry`/`ToCustomProfileDefinition` drop `Game` metadata (`ProfileEditorViewModel.cs:103-104`, `ProfileManagerViewModel.cs:499-500`), row property changes do not revalidate, and UI has no add/remove row controls (`MainWindow.axaml:1622-1656`). |
| 3 | User can save projects that reference custom profiles while preserving legacy compatibility fields for older `.jbs2bg` consumers. | ✗ FAILED | Core `ProjectSaveContext` exists (`ProjectFileService.cs:27-32, 132-137, 209-235`), but GUI save calls `projectFileService.SaveToString(project)` without context (`MainWindowViewModel.cs:889-895`). |
| 4 | User can resolve missing custom profile references through clear diagnostics rather than silent fallback. | ✓ VERIFIED | `ProfileRecoveryDiagnosticsService` emits neutral `MissingCustomProfile` diagnostics and exact-name action options (`ProfileRecoveryDiagnosticsService.cs:73-104`); Diagnostics/Profile manager recovery routing is present per `04-07` source changes. |
| 5 | User can bundle or copy project-specific profiles when sharing a project with another machine. | ✗ FAILED | Normal app saves omit referenced local custom definitions due missing `ProjectSaveContext`; selected export is weakened by non-selectable rows and dropped `Game` metadata. |

**Score:** 1/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/BS2BG.Core/Generation/ProfileDefinitionService.cs` | Strict custom profile JSON validation/export | ✓ VERIFIED | Parses/export stable profile JSON with Version/Name/Game/Defaults/Multipliers/Inverted. |
| `src/BS2BG.App/Services/UserProfileStore.cs` | AppData custom profile discovery/storage | ✓ VERIFIED | Discovers `%APPDATA%/jBS2BG/profiles`, validates JSON, writes via `AtomicFileWriter`. |
| `src/BS2BG.Core/Serialization/ProjectFileService.cs` | Optional `CustomProfiles` project serialization | ⚠️ PARTIAL | Core resolver works, but GUI save path is not wired to `ProjectSaveContext`. |
| `src/BS2BG.Core/Diagnostics/ProfileRecoveryDiagnosticsService.cs` | Missing-profile diagnostics and exact-match resolution | ✓ VERIFIED | Neutral recovery diagnostics and internal-display-name matching implemented. |
| `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` | Profile manager workflows | ✗ FAILED | Copy-as-custom bug, metadata loss, direct export write without recovery handling, and row selection dependency issues. |
| `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs` | Metadata/table editing with validation gating | ✗ FAILED | Existing rows can be edited, but Game is not preserved and row value edits do not revalidate automatically. |
| `src/BS2BG.App/Views/MainWindow.axaml` | First-class Profiles workspace UI | ✗ FAILED | Profiles tab exists with compiled-binding labels, but profile rows are non-selectable and table add/remove controls are missing. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `ProfileDefinitionService.ValidateProfileJson` | custom profile catalog/store inclusion | explicit token validation | ✓ WIRED | `UserProfileStore.DiscoverProfiles` and import workflow call validator before inclusion. |
| `UserProfileStore.SaveProfile` | atomic local JSON write | `AtomicFileWriter.WriteAtomic` | ✓ WIRED | Save path writes deterministic profile JSON atomically. |
| `ProjectFileService.SaveToString(project, context)` | referenced custom profile embedding | `ProjectSaveContext.AvailableCustomProfilesByName` | ⚠️ PARTIAL | Core link exists, app save bypasses context overload. |
| `MainWindowViewModel.SaveProjectInternalAsync` | project serializer context | expected `ProjectSaveContext` | ✗ NOT_WIRED | Calls `SaveToString(project)` only. |
| `MainWindow.axaml Profiles rows` | `ProfileManagerViewModel.SelectedProfile` | expected selected row binding/command | ✗ NOT_WIRED | Uses `ItemsControl`; no `SelectedItem` or row command. |
| `ProfileEditorViewModel row edits` | validation/save gating | expected row property subscriptions | ⚠️ PARTIAL | Collection changes trigger validation; existing row property changes do not. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `MainWindow.axaml` Profiles list | `BundledProfiles`, `CustomProfiles`, `EmbeddedProjectProfiles`, `MissingProfiles` | `ProfileManagerViewModel.RefreshProfileEntries()` from catalog/project | Yes | ⚠️ HOLLOW ACTION TARGET — rows render real data but cannot update selection. |
| `ProjectFileService` `CustomProfiles` | referenced custom profile definitions | `ProjectModel.CustomProfiles` or `ProjectSaveContext` | Yes in Core | ⚠️ HOLLOW APP FLOW — GUI save does not provide local profile context. |
| `ProfileEditorViewModel` `Game` | editor metadata | `FromEntry(entry, ...)` | No | ✗ HOLLOW_PROP — call site hardcodes `string.Empty`, export/save emits empty metadata. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Full regression suite | `dotnet test` | Known gate result supplied by user: 406 passed, 0 failed | ✓ PASS (external gate) |
| Schema drift gate | project schema drift check | Known gate result supplied by user: no drift detected | ✓ PASS (external gate) |
| Source-level blocker verification | direct source inspection | Verified CR-01 through CR-06 code paths still present | ✗ FAIL |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| EXT-01 | 04-01, 04-02, 04-05, 04-07, 04-08 | Import, copy, export, and validate local JSON profile files without corrupting bundled profiles/projects. | ✗ BLOCKED | Core validation/storage exists, but copy-as-custom is broken and rows are not selectable for user-targeted operations. |
| EXT-02 | 04-01, 04-05, 04-08 | Edit supported metadata and slider tables through validated workflows. | ✗ BLOCKED | Metadata is dropped; no add/remove row controls; row edits do not automatically revalidate. |
| EXT-03 | 04-03, 04-06, 04-07 | Save projects referencing custom profiles while preserving legacy compatibility fields. | ✗ BLOCKED | Legacy fields preserved in Core, but GUI saves do not pass `ProjectSaveContext`, so referenced local profiles are omitted. |
| EXT-04 | 04-04, 04-06, 04-07, 04-08 | Resolve missing custom profile references through clear diagnostics rather than silent fallback. | ✓ SATISFIED | Recovery diagnostics are neutral/actionable and exact-match helpers ignore filenames. |
| EXT-05 | 04-03, 04-06, 04-07, 04-08 | Bundle or copy project-specific profiles when sharing a project. | ✗ BLOCKED | GUI project sharing omits referenced local profile definitions; selected export has UI selection/metadata gaps. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---:|---|---|---|
| `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` | 894 | `SaveToString(project)` without save context | 🛑 Blocker | Shared projects saved from the app can reference local custom profiles without embedding definitions. |
| `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` | 690 | Single occupied-name set remove | 🛑 Blocker | Rename conflict validation can allow local custom profile name collisions. |
| `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` | 319 | Clears selected row before copying | 🛑 Blocker | Copy bundled profile opens an empty editor instead of a copy. |
| `src/BS2BG.App/Views/MainWindow.axaml` | 1432 | `ItemsControl` rows with no selection wiring | 🛑 Blocker | Users cannot choose the profile row that edit/export/delete/copy actions should apply to. |
| `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs` | 103 | Hardcoded `string.Empty` metadata | 🛑 Blocker | Editing/exporting existing profiles loses `Game` metadata. |
| `src/BS2BG.App/Views/MainWindow.axaml` | 1622 | Display-only table sections | 🛑 Blocker | Blank/custom profiles cannot author slider rows through the UI. |
| `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs` | 66 | Collection-only validation updates | ⚠️ Warning | Row value edits can leave validation and save gating stale. |
| `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` | 365 | Direct file write without local error status | ⚠️ Warning | Export failures bubble without actionable profile-export failure copy. |

### Human Verification Required

None. The Plan 04-08 human visual/accessibility checkpoint was already approved by the user, but automated/source verification found blockers that do not require additional human judgment.

### Gaps Summary

Phase 04 is not goal-achieved. Core profile validation, AppData storage, project serialization primitives, and missing-profile diagnostics exist, but several critical App/UI links are broken. The most serious blockers are: normal GUI project saves do not embed referenced local custom profiles; the Profiles tab renders rows without a selection mechanism; copy-as-custom loses the selected bundled source; editing/exporting drops `Game` metadata; blank/custom table authoring lacks add/remove controls; and rename conflict validation can approve ambiguous local-profile collisions. These gaps block EXT-01, EXT-02, EXT-03, and EXT-05, so the phase must not proceed as passed.

---

_Verified: 2026-04-27T09:55:35Z_
_Verifier: the agent (gsd-verifier)_
