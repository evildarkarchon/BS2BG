---
phase: 01-profile-correctness-and-trust
verified: 2026-04-26T13:37:00Z
status: gaps_found
score: 10/13 must-haves verified
overrides_applied: 0
gaps:
  - truth: "User can see clear warnings for unknown, missing, inferred, mismatched, or experimental profile states before generation or export."
    status: failed
    reason: "The implementation intentionally exposes only neutral unresolved-profile information for an unbundled selected preset, explicitly avoids warning/mismatch/experimental language, and export paths do not perform profile-risk checks before writing. This does not satisfy the ROADMAP/PROF-03 warning contract."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/TemplatesViewModel.cs"
        issue: "RefreshProfileFallbackInformation displays neutral text only for unbundled SelectedPreset.ProfileName; no warning state for missing, inferred, mismatched, or experimental profiles."
      - path: "src/BS2BG.App/ViewModels/MainWindowViewModel.cs"
        issue: "ExportBodyGenInisAsync and ExportBosJsonAsync generate/write outputs without checking unresolved profile state or surfacing pre-export profile warnings."
      - path: "src/BS2BG.App/Views/MainWindow.axaml"
        issue: "Fallback panel is bound to neutral information state, not warning state."
    missing:
      - "Add/restore a clear warning or accepted override for the roadmap PROF-03 profile-risk contract."
      - "Cover unknown, missing, inferred, mismatched, and experimental/profile-confidence cases before generation/export, or explicitly revise/override the roadmap contract."
  - truth: "User can generate templates, morphs, and BoS JSON with bundled-profile behavior protected by profile-specific tests."
    status: partial
    reason: "Core generation/export uses profile catalogs, and FO4 template behavior is tested, but there is no profile-specific BoS JSON coverage for each bundled profile and no morph/export integration proof explaining or testing the morphs side of the roadmap criterion."
    artifacts:
      - path: "tests/BS2BG.Tests/TemplateGenerationServiceTests.cs"
        issue: "FO4-specific assertions cover template generation only."
      - path: "tests/BS2BG.Tests/SliderMathFormatterTests.cs"
        issue: "Existing BoS JSON fixture theories still load settings.json for fallout4-cbbe and skyrim-uunp scenarios, so they do not protect distinct bundled profile behavior for each profile."
      - path: "src/BS2BG.Core/Generation/MorphGenerationService.cs"
        issue: "Morph generation is profile-independent, but no test or documented assertion ties this to the Phase 1 profile-specific requirement."
    missing:
      - "Add profile-specific BoS JSON tests for Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE using the bundled catalog/profile data."
      - "Add explicit coverage that BodyGen export combines profile-specific templates with morph output, or document/override that morphs are profile-independent."
  - truth: "A selected preset with an unbundled saved profile keeps the original profile name until the user explicitly chooses a bundled profile."
    status: partial
    reason: "The user can adopt a different bundled profile, but cannot explicitly adopt the currently displayed fallback profile because the selector is already set to that value and OnSelectedProfileNameChangedReactive only writes the preset when the selected value changes."
    artifacts:
      - path: "src/BS2BG.App/ViewModels/TemplatesViewModel.cs"
        issue: "SetSelectedProfileNameFromPreset preselects the fallback profile; selecting that same ComboBox item again does not trigger profile adoption, leaving fallback information visible."
      - path: ".planning/phases/01-profile-correctness-and-trust/01-REVIEW.md"
        issue: "Code review warning WR-01 identifies this adoption-path gap."
    missing:
      - "Add an explicit adoption action/path for the displayed fallback profile or keep fallback calculation separate from selector selection until the user chooses a bundled value."
---

# Phase 1: Profile Correctness and Trust Verification Report

**Phase Goal:** Users can generate output with explicit, profile-specific semantics and visible warnings when profile confidence is incomplete.
**Verified:** 2026-04-26T13:37:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | FO4 CBBE is selectable as a distinct bundled profile | ✓ VERIFIED | `TemplateProfileCatalogFactory.cs:22-30` registers Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE; `settings_FO4_CBBE.json` exists; `BS2BG.App.csproj:30-32` copies it as app content. |
| 2 | FO4 defaults, multipliers, and inverted sliders are not reused from Skyrim profiles | ✓ VERIFIED | `settings_FO4_CBBE.json:2-68` has FO4-only seeded defaults/multipliers and `Inverted: []`; `TemplateProfileCatalogFactoryTests.cs:28-76` asserts FO4-only defaults and absence from Skyrim profiles. |
| 3 | Existing Skyrim profile files remain at repository root | ✓ VERIFIED | `settings.json`, `settings_UUNP.json`, and `settings_FO4_CBBE.json` are root-level; no profile migration is required by app wiring. |
| 4 | Legacy `.jbs2bg` projects without `Profile` map through `isUUNP` | ✓ VERIFIED | `ProjectFileService.cs:85-88` calls `ProjectProfileMapping.Resolve(dto?.Profile, dto?.IsUunp ?? false)`; `ProjectFileServiceTests.cs:150-175` covers both legacy values. |
| 5 | Unbundled saved `Profile` values preserve exact names on load/save | ✓ VERIFIED | `ProjectProfileMapping.Resolve` returns nonblank profile names unchanged; `ProjectFileService.cs:154-160` saves `preset.ProfileName`; `ProjectFileServiceTests.cs:122-148` covers `Community CBBE`. |
| 6 | Generation fallback remains non-blocking and detectable | ✓ VERIFIED | `TemplateProfileCatalog.cs:20-31` exposes `ContainsProfile` and keeps `GetProfile` fallback to `DefaultProfile`; `TemplateProfileCatalogTests.cs:10-33` covers both behaviors. |
| 7 | Imported BodySlide XML uses the currently selected profile, not path/slider inference | ✓ VERIFIED | `TemplatesViewModel.cs:432-449` assigns `preset.ProfileName = SelectedProfileName`; `TemplatesViewModelTests.cs:231-253` imports from a Skyrim-named folder while selected profile is FO4. |
| 8 | Unresolved profile fallback is visible as neutral information and non-blocking | ✓ VERIFIED | `TemplatesViewModel.cs:640-655` sets exact neutral fallback text and visibility; `MainWindow.axaml:312-323` displays it; tests cover generation success and no warning words. |
| 9 | User can explicitly adopt any bundled profile from unresolved fallback | ✗ FAILED | Review WR-01 is confirmed by `TemplatesViewModel.cs:621-633` and `577-587`: the displayed fallback profile is already selected, so reselecting it cannot update `SelectedPreset.ProfileName`. |
| 10 | Main workflow does not label FO4 CBBE as experimental or introduce mismatch wording | ✓ VERIFIED | Grep found no `experimental`/`mismatch` in App source except tests/review docs; `AppShellTests.cs:121-151` asserts profile names and visible copy. |
| 11 | FO4 profile confidence context exists outside app workflow | ✓ VERIFIED | `docs/release/README.md:27-32` documents the FO4 seed/calibration status outside the in-app flow. |
| 12 | Clear warnings for unknown/missing/inferred/mismatched/experimental profile states before generation/export | ✗ FAILED | App code only implements neutral fallback information for unbundled selected presets; `MainWindowViewModel.cs:428-488` exports without profile-risk checks. |
| 13 | Bundled-profile behavior is protected by tests for templates, morphs, and BoS JSON | ✗ FAILED | FO4 template behavior is tested, but BoS JSON fixture tests still use `settings.json` for FO4/UUNP scenarios and morph/profile integration has no specific coverage. |

**Score:** 10/13 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `settings_FO4_CBBE.json` | Distinct FO4 profile data | ✓ VERIFIED | Contains FO4 sliders, neutral multipliers, and empty inverted list. |
| `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs` | Runtime bundled profile registration | ✓ VERIFIED | Loads FO4 via `LoadRequiredProfile("settings_FO4_CBBE.json", directories)`. |
| `src/BS2BG.App/BS2BG.App.csproj` | App content-copy wiring | ✓ VERIFIED | Includes `settings_FO4_CBBE.json` with output/publish copy metadata. |
| `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs` | Fallback detection contract | ✓ VERIFIED | Contains `ContainsProfile` and non-throwing fallback `GetProfile`. |
| `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` | Selected-profile import and fallback state | ⚠️ PARTIAL | Core state exists, but default fallback profile adoption path is incomplete. |
| `src/BS2BG.App/Views/MainWindow.axaml` | Fallback information panel | ✓ VERIFIED | Panel exists below profile toolbar and binds text/visibility. |
| `src/BS2BG.App/Themes/ThemeResources.axaml` | Neutral info brushes | ✓ VERIFIED | Light/dark `BS2BGInfo*` resources exist and are distinct from warning brushes. |
| `docs/release/README.md` | FO4 confidence context | ✓ VERIFIED | Contains release-facing FO4 calibration note. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `TemplateProfileCatalogFactory.cs` | `settings_FO4_CBBE.json` | `LoadRequiredProfile` | ✓ WIRED | Manual check: lines 28-29 bind `ProjectProfileMapping.Fallout4Cbbe` to `settings_FO4_CBBE.json`. |
| `BS2BG.App.csproj` | `settings_FO4_CBBE.json` | `Content Include` | ✓ WIRED | Manual check: line 32 includes copy/publish metadata. |
| `ProjectFileService.LoadFromString` | `SliderPreset.ProfileName` | `ProjectProfileMapping.Resolve` | ✓ WIRED | `ProjectFileService.cs:85-88` sets constructor profile from resolved DTO fields. |
| `TemplateProfileCatalog.ContainsProfile` | `TemplatesViewModel` fallback UI | boolean unresolved-profile detection | ✓ WIRED | `TemplatesViewModel.cs:642-654` calls `ContainsProfile` before setting fallback text/visibility. |
| `TemplatesViewModel.ImportPresetFilesCoreAsync` | `SliderPreset.ProfileName` | selected profile assignment | ✓ WIRED | `TemplatesViewModel.cs:445-448` assigns imported presets from `SelectedProfileName`. |
| `MainWindow.axaml` | `IsProfileFallbackInformationVisible` | `IsVisible` binding | ✓ WIRED | `MainWindow.axaml:319`. |
| `MainWindow.axaml` | `ProfileFallbackInformationText` | `Text` binding | ✓ WIRED | `MainWindow.axaml:321`. |
| Export commands | profile-risk warning state | pre-export check | ✗ NOT WIRED | `MainWindowViewModel.cs:428-488` has no unresolved-profile warning/check before BodyGen or BoS export. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `MainWindow.axaml` fallback panel | `Templates.ProfileFallbackInformationText` | `TemplatesViewModel.RefreshProfileFallbackInformation()` from `SelectedPreset.ProfileName` and `TemplateProfileCatalog` | Yes | ✓ FLOWING |
| `TemplatePreviewTextBox`/BoS preview | `SelectedProfileName` | selected preset sync or user profile selector | Yes | ✓ FLOWING, with adoption-path caveat |
| `TemplateGenerationService.GenerateTemplates` | `preset.ProfileName` | project/imported presets and catalog `GetProfile` | Yes | ✓ FLOWING |
| `BosJsonExportWriter.Write` | `preset.ProfileName` | project snapshot and catalog `GetProfile` | Yes | ✓ FLOWING, but profile-specific test coverage is incomplete |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Full test suite | `dotnet test --no-restore` | Passed: 238/238 | ✓ PASS |
| Artifact contract checks | `gsd-sdk query verify.artifacts` for plans 01-04 | Passed: 13/13 artifacts | ✓ PASS |
| Key-link SDK checks | `gsd-sdk query verify.key-links` for plans 01-04 | Reported source/pattern lookup failures | ⚠️ MANUAL OVERRIDE BY EVIDENCE — manual line checks verified most plan links; export warning link remains missing |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| PROF-01 | Plans 01 | Distinct FO4 profile not reusing Skyrim tables | ✓ SATISFIED | Distinct JSON + factory registration + FO4-only tests. |
| PROF-02 | Plans 02, 03 | Legacy `isUUNP` compatibility and save semantics | ✓ SATISFIED | `ProjectFileService` and tests preserve legacy/unbundled semantics. |
| PROF-03 | Plans 02, 03, 04 | Clear warning for unknown/missing/inferred/mismatched profile states | ✗ BLOCKED | Implemented neutral unbundled-profile information and explicitly suppressed mismatch/experimental/warning language; export preflight warnings absent. |
| PROF-04 | Plans 01, 03 | Profile-specific generation covered by tests for bundled profiles | ✗ BLOCKED | Template path is partly covered; BoS JSON distinct profile coverage and morph/export integration coverage are incomplete. |
| PROF-05 | Plan 04 | FO4 experimental/calibration context understandable | ✓ SATISFIED | Release docs contain FO4 calibration-status note outside the app workflow. |

No orphaned Phase 1 requirement IDs were found in `.planning/REQUIREMENTS.md`; PROF-01 through PROF-05 are all claimed by plans.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---:|---|---|---|
| `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` | 621-633 / 577-587 | Selector fallback value doubles as unresolved-profile adoption path | ⚠️ Warning | User cannot adopt the displayed fallback profile if it is already selected. |
| `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` | 428-488 | Export proceeds without profile-risk warning/preflight | 🛑 Blocker | Violates roadmap/PROF-03 before-generation/export warning contract. |

### Human Verification Required

Automated checks found blocking gaps. After gaps are addressed, manually verify the visual placement/styling of the fallback panel in the running Avalonia UI: it should appear directly below the Templates profile toolbar, use neutral information styling, and remain readable in light and dark themes.

### Gaps Summary

Phase 1 has substantial implementation for distinct FO4 profile data, legacy profile preservation, selected-profile import, neutral fallback information, and release documentation. However, the roadmap and requirements still require clear profile-risk warnings before generation/export and profile-specific test protection across templates, morphs, and BoS JSON. Those contracts are not fully met in the codebase. The code review warning about adopting the displayed fallback profile is also a real behavior gap, though secondary to the roadmap-level warning/test-coverage blockers.

---

_Verified: 2026-04-26T13:37:00Z_
_Verifier: the agent (gsd-verifier)_
