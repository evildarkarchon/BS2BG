---
phase: 04-profile-extensibility-and-controlled-customization
plan: 02
subsystem: profile-storage-and-runtime-catalog
tags: [csharp, appdata, profile-catalog, reactiveui, atomic-writes, tdd]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: Core custom profile validation/export contracts from 04-01
provides:
  - Source-tagged profile catalog entries with bundled read-only metadata
  - User-local AppData custom profile discovery, diagnostics, and atomic JSON saves
  - Injectable runtime catalog factory/service with local refresh and project overlay hooks
affects: [profile-extensibility, profile-management-ui, project-embedded-profiles, diagnostics-profile-recovery]

tech-stack:
  added: []
  patterns:
    - Result-first user profile discovery and save/delete outcomes
    - Semaphore-serialized mutable App catalog holder with observable refresh notifications
    - Sanitized deterministic profile filenames with SHA-256 collision suffixes

key-files:
  created:
    - src/BS2BG.App/Services/UserProfileStore.cs
    - src/BS2BG.App/Services/TemplateProfileCatalogService.cs
    - tests/BS2BG.Tests/UserProfileStoreTests.cs
  modified:
    - src/BS2BG.Core/Generation/TemplateProfileCatalog.cs
    - src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
    - src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs
    - tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs

key-decisions:
  - "Custom local profile discovery is non-fatal: malformed user JSON produces diagnostics while bundled startup remains available."
  - "Runtime profile catalog refresh has a single App-layer owner, serialized through SemaphoreSlim, and ViewModels read Current catalog state."
  - "Project-scoped embedded profiles may override local custom entries only for the active project, while bundled-name collisions remain blockers."

patterns-established:
  - "Local custom profiles are stored under AppData/jBS2BG/profiles and persisted with AtomicFileWriter using UTF-8 without BOM."
  - "Catalog entries carry ProfileSourceKind/FilePath/IsEditable metadata while preserving existing ProfileNames/GetProfile fallback semantics."
  - "Custom profile filenames are sanitized from internal profile identity and collision-suffixed with stable SHA-256 instead of string.GetHashCode."

requirements-completed: [EXT-01]

duration: 7 min
completed: 2026-04-27
---

# Phase 04 Plan 02: User Profile Storage and Runtime Catalog Composition Summary

**AppData-backed custom profile storage with source-tagged catalog entries, atomic local JSON writes, and observable runtime catalog refreshes**

## Performance

- **Duration:** 7 min
- **Started:** 2026-04-27T08:25:20Z
- **Completed:** 2026-04-27T08:31:57Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments

- Extended `TemplateProfileCatalog` with `ProfileCatalogEntry` metadata, bundled read-only wrapping, and case-insensitive duplicate-name rejection while preserving existing lookup/fallback APIs.
- Added `IUserProfileStore`/`UserProfileStore` for `%APPDATA%/jBS2BG/profiles` discovery, validation diagnostics, deterministic duplicate handling, sanitized save filenames, and atomic UTF-8 JSON persistence.
- Converted profile catalog composition into injectable factory/service wiring with DI registrations, local custom refresh, project overlay hooks, serialized mutation, and ViewModel observation of the current catalog.
- Added focused tests for catalog metadata, AppData store behavior, deterministic duplicate handling, collision-safe filenames, local custom catalog inclusion, bundled-name duplicate rejection, and same-session Templates refresh usage.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add source-tagged catalog tests** - `517956f9` (test)
2. **Task 1 GREEN: Add source-tagged catalog entries** - `24cd7f5e` (feat)
3. **Task 2 RED: Add user profile store tests** - `66f0eb3c` (test)
4. **Task 2 GREEN: Implement user-local profile store** - `04d84c81` (feat)
5. **Task 3: Compose runtime custom profile catalog** - `86513d1c` (feat)

**Plan metadata:** pending final docs commit

_Note: TDD tasks used separate RED and GREEN commits._

## Files Created/Modified

- `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs` - Adds `ProfileCatalogEntry`, `Entries`, bundled legacy wrapping, and duplicate-name validation.
- `src/BS2BG.App/Services/UserProfileStore.cs` - Implements AppData custom-profile discovery, save/delete result objects, validation integration, filename sanitization, SHA-256 suffixes, and atomic writes.
- `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs` - Converts static-only factory into injectable composition for bundled entries followed by valid local custom profiles while retaining static shims.
- `src/BS2BG.App/Services/TemplateProfileCatalogService.cs` - Adds the serialized runtime catalog holder with refresh, project overlay, clear overlay, diagnostics, and `CatalogChanged` notifications.
- `src/BS2BG.App/AppBootstrapper.cs` - Registers `ProfileDefinitionService`, `IUserProfileStore`, `TemplateProfileCatalogFactory`, and `ITemplateProfileCatalogService`.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Reads current catalog state for export previews/writes and clears project overlays on new/open project state resets.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Observes catalog refreshes and recalculates profile-dependent preview/inspector state using `ITemplateProfileCatalogService.Current`.
- `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs` - Reads the current catalog at diagnostics refresh time rather than retaining a stale catalog snapshot.
- `tests/BS2BG.Tests/UserProfileStoreTests.cs` - Covers AppData path, discovery diagnostics, duplicate custom files, UTF-8 saves, and sanitized collision-safe filenames.
- `tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs` - Covers source metadata, duplicate rejection, local custom composition, bundled duplicate skipping, and same-session catalog refresh.

## Decisions Made

- Custom profile discovery failures and invalid files are represented as diagnostics so malformed user data cannot break bundled app startup.
- The App-layer catalog service is the only mutable runtime catalog owner; all refresh/overlay/clear operations are serialized through `SemaphoreSlim` and published through `CatalogChanged`.
- Embedded project overlays are in-memory only and can supersede same-name local custom profiles for the active project, but they cannot shadow bundled names.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

- Focused test compilation exposed a FluentAssertions collection API mismatch in the new test; the assertion was corrected before Task 2 RED was committed.
- The plan requested bundled `Game` metadata when wrapping profile catalog entries, but the specified `ProfileCatalogEntry` contract does not include a `Game` field. The implementation preserved the exact planned entry contract and source/editability metadata.

## Known Stubs

None.

## Threat Flags

None - the new AppData file boundary, catalog-inclusion validation, duplicate-name spoofing prevention, and atomic save behavior were covered by the plan threat model.

## Verification

- `dotnet test --filter FullyQualifiedName~TemplateProfileCatalogFactoryTests` — passed during Task 1 GREEN.
- `dotnet test --filter FullyQualifiedName~UserProfileStoreTests` — passed during Task 2 GREEN.
- `dotnet test --filter "FullyQualifiedName~UserProfileStoreTests|FullyQualifiedName~TemplateProfileCatalogFactoryTests"` — passed (14 tests).
- `dotnet build BS2BG.sln` — passed (0 warnings, 0 errors).

## TDD Gate Compliance

- RED gate commits: `517956f9`, `66f0eb3c`
- GREEN gate commits: `24cd7f5e`, `04d84c81`
- Refactor gate: not needed; Task 3 was non-TDD and implementation passed focused tests/build.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Runtime local custom profile discovery and refresh plumbing are ready for the Profiles tab/import/edit workflows. Future embedded project persistence and conflict UI can call `WithProjectProfiles`, `ClearProjectProfiles`, `Refresh`, `SaveProfile`, and `DeleteProfile` without mutating bundled profile files.

## Self-Check: PASSED

- Created files verified: `UserProfileStore.cs`, `TemplateProfileCatalogService.cs`, `UserProfileStoreTests.cs`, and `04-02-SUMMARY.md` exist.
- Commits verified: `517956f9`, `24cd7f5e`, `66f0eb3c`, `04d84c81`, and `86513d1c` exist in git history.
- Verification commands passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
