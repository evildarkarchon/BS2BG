---
phase: 04-profile-extensibility-and-controlled-customization
plan: 07
subsystem: profile-recovery-actions-and-capability-specs
tags: [csharp, reactiveui, diagnostics, profile-recovery, openspec, tdd]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: source-tagged runtime catalog, project-embedded custom profiles, recovery diagnostics, profile manager/editor workflows, and project-open overlay conflict handling from plans 04-01 through 04-06
provides:
  - Binding-ready Diagnostics recovery action rows with explicit command routing
  - Profile manager workflows for exact internal-name import recovery and project-copy overlay activation
  - Undo-aware template preset profile remapping and selected custom/embedded profile JSON export
  - OpenSpec profile extensibility, project roundtrip, and template generation capability deltas
affects: [profile-extensibility, diagnostics-ui, template-generation-flow, project-roundtrip, profiles-workspace]

tech-stack:
  added: []
  patterns:
    - Diagnostics-to-profile-manager recovery action handler boundary
    - Undo-recorded recovery remap snapshots over preset profile references
    - Source-gated standalone profile JSON export for LocalCustom and EmbeddedProject rows

key-files:
  created:
    - openspec/specs/profile-extensibility/spec.md
  modified:
    - src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs
    - src/BS2BG.App/ViewModels/DiagnosticFindingViewModel.cs
    - src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs
    - openspec/specs/project-roundtrip/spec.md
    - openspec/specs/template-generation-flow/spec.md
    - tests/BS2BG.Tests/DiagnosticsViewModelTests.cs
    - tests/BS2BG.Tests/ProfileManagerViewModelTests.cs
    - tests/BS2BG.Tests/TemplatesViewModelTests.cs

key-decisions:
  - "Diagnostics exposes explicit recovery action rows but delegates profile imports, project overlays, and remaps through an App-layer handler instead of owning mutations."
  - "Import Matching Profile resolves missing references only by CustomProfileDefinition.Name with OrdinalIgnoreCase; matching filenames remain ignored."
  - "Selected standalone profile export is limited to LocalCustom and EmbeddedProject rows, not bundled profiles or missing fallback rows."

patterns-established:
  - "Recovery action labels are generated from ProfileRecoveryActionKind in one binding-ready row model."
  - "Recovery remaps use UndoRedoService snapshots so undo restores unresolved profile names and fallback information."

requirements-completed: [EXT-01, EXT-03, EXT-04, EXT-05]

duration: 6 min
completed: 2026-04-27
---

# Phase 04 Plan 07: Missing Profile Recovery Actions and Profile Sharing Summary

**Explicit missing-profile recovery commands with undoable remaps, exact-name import validation, project-copy overlays, selected JSON export, and OpenSpec capability coverage**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-27T09:07:51Z
- **Completed:** 2026-04-27T09:14:01Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Added Diagnostics recovery action rows for `Import Matching Profile`, `Use Project Copy`, `Remap to Installed Profile`, and `Keep Unresolved for Now`, while preserving Diagnostics as read-only until an explicit action command is invoked.
- Routed recovery actions through `IProfileRecoveryActionHandler` implemented by `ProfileManagerViewModel`, so file pickers, local-store writes, and project overlay mutations stay outside `DiagnosticsViewModel`.
- Implemented exact internal display-name validation for import recovery, project-scoped embedded-copy activation without local store writes, and an explicit keep-unresolved acknowledgement that leaves fallback/recovery state visible.
- Added undo-aware `TemplatesViewModel.RemapProfileReferences` and enabled selected standalone JSON export only for local custom and project-embedded profile rows.
- Updated OpenSpec capability specs for profile extensibility, optional `CustomProfiles` round-trip behavior, project-scoped overlays, visible fallback behavior, and recovery remap semantics.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Add recovery action routing tests** - `1fbdad5a` (test)
2. **Task 1 GREEN: Route missing profile recovery actions** - `28fc1c53` (feat)
3. **Task 2 RED: Add remap and profile export tests** - `83eea5e2` (test)
4. **Task 2 GREEN: Add undoable remap and JSON export** - `2658abd3` (feat)
5. **Task 3: Update OpenSpec capability specs** - `98a10283` (docs)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs` - Adds recovery action rows, labels, command execution, keep-unresolved acknowledgement, and the action handler boundary.
- `src/BS2BG.App/ViewModels/DiagnosticFindingViewModel.cs` - Exposes diagnostic `Code` and `Category` metadata to App tests/bindings.
- `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` - Implements recovery action routing, exact-name import recovery, project-copy overlay activation, keep-unresolved status, and custom/embedded-only JSON export.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Adds undo-recorded profile reference remapping with fallback/preview/selector refresh after remap and undo.
- `src/BS2BG.App/AppBootstrapper.cs` - Registers the profile recovery handler and forces the intended catalog factory constructor for DI.
- `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs` - Adds parent-directory profile search for deterministic test-host/App DI profile discovery.
- `openspec/specs/profile-extensibility/spec.md` - New capability spec for bundled read-only profiles, local custom validation, project embedding, conflicts, recovery, remap, and export behavior.
- `openspec/specs/project-roundtrip/spec.md` - Documents optional `CustomProfiles` and legacy field preservation.
- `openspec/specs/template-generation-flow/spec.md` - Documents project-scoped overlays, visible fallback behavior, and recovery remap undo semantics.
- `tests/BS2BG.Tests/DiagnosticsViewModelTests.cs` - Covers recovery action rows, keep-unresolved visibility, and recovery-coded diagnostics/report expectations.
- `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` - Covers filename/internal-name mismatch rejection, project-copy overlay activation without store writes, and export command source gating.
- `tests/BS2BG.Tests/TemplatesViewModelTests.cs` - Covers undo after recovery remap restoring missing profile fallback information.

## Decisions Made

- Diagnostics recovery actions are binding-ready rows, but mutations route through `IProfileRecoveryActionHandler` so Diagnostics does not own storage or project mutation workflows.
- `Import Matching Profile` validates with `ProfileDefinitionService` and then uses `ProfileRecoveryDiagnosticsService.CanResolveMissingReference`; filenames never resolve missing references.
- `Use Project Copy` activates all current project embedded profiles through the existing project-scoped overlay and does not write embedded definitions to the local profile store.
- `Export Profile JSON` is intentionally source-gated to local custom and embedded rows to satisfy D-17 without becoming a Phase 5 portable bundle/export-everything workflow.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Made App DI catalog factory construction deterministic**
- **Found during:** Task 1 focused verification
- **Issue:** `AppBootstrapperRegistersDiagnosticsServicesAndViewModel` resolved `TemplateProfileCatalogFactory` through Microsoft DI, which selected the public `(IUserProfileStore, IEnumerable<string>)` constructor with an empty enumerable and failed to find bundled `settings.json`.
- **Fix:** Registered `TemplateProfileCatalogFactory` with an explicit factory that calls the intended one-argument constructor.
- **Files modified:** `src/BS2BG.App/AppBootstrapper.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~DiagnosticsViewModelTests|FullyQualifiedName~ProfileManagerViewModelTests"` passed.
- **Committed in:** `28fc1c53`

**2. [Rule 3 - Blocking] Added test-host bundled profile search fallback**
- **Found during:** Task 1 focused verification
- **Issue:** Test-host App DI can run from output folders that do not contain bundled profile JSON files beside the test assembly.
- **Fix:** Kept `AppContext.BaseDirectory` first and added current/parent directory candidates so repo-root bundled profile JSON can be discovered in deterministic tests without changing production base-directory preference.
- **Files modified:** `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~DiagnosticsViewModelTests|FullyQualifiedName~ProfileManagerViewModelTests"` passed.
- **Committed in:** `28fc1c53`

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes were needed to keep the planned recovery routing verifiable through existing App DI smoke tests. No profile math, formatter, export writer, or golden fixture behavior changed.

## Issues Encountered

- Existing Diagnostics tests still expected the older generic profile fallback text. They were updated to assert the Phase 4 recovery-coded diagnostic copy and metadata introduced by the recovery diagnostics flow.
- Focused `dotnet test` runs emit existing analyzer warnings in unrelated files; final `dotnet build BS2BG.sln` completed with 0 warnings and 0 errors.

## Known Stubs

None. Empty collection initializers in ViewModels/tests are mutable UI/test collections populated by catalog, project, or command state.

## Threat Flags

None - the recovery UI to project/local profile state boundary and selected profile export path boundary were covered by the plan threat model.

## Verification

- `dotnet test --filter "FullyQualifiedName~DiagnosticsViewModelTests|FullyQualifiedName~ProfileManagerViewModelTests"` — passed (13 tests) for Task 1.
- `dotnet test --filter "FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~ProfileManagerViewModelTests"` — passed (37 tests) for Task 2.
- `dotnet test --filter "FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~DiagnosticsViewModelTests|FullyQualifiedName~ProfileManagerViewModelTests"` — passed (44 tests) for Task 3 and final verification.
- `dotnet build BS2BG.sln` — passed (0 warnings, 0 errors).
- Acceptance checks confirmed required recovery labels, filename/internal-name mismatch coverage, project-copy overlay coverage, keep-unresolved visibility, `undoRedo.Record` use in remap, `ExportProfileJson` export success copy, and OpenSpec `CustomProfiles`/`Profile conflict found`/project-scoped overlay wording.

## TDD Gate Compliance

- RED gate commits: `1fbdad5a`, `83eea5e2`
- GREEN gate commits: `28fc1c53`, `2658abd3`
- Refactor gate: not needed; focused tests and build passed without a separate cleanup commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 04-08 can consume explicit recovery actions, source-gated profile export, and OpenSpec capability documentation. Remaining Phase 4 work should focus on Profiles workspace UI binding/visual verification over these command surfaces rather than adding new recovery semantics.

## Self-Check: PASSED

- Created file verified: `openspec/specs/profile-extensibility/spec.md` exists.
- Modified files verified: Diagnostics, Profile Manager, Templates, OpenSpec specs, and focused test files exist.
- Commits verified in git history: `1fbdad5a`, `28fc1c53`, `83eea5e2`, `2658abd3`, and `98a10283`.
- Verification commands passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
