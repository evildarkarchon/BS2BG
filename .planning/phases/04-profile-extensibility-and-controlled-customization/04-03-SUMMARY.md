---
phase: 04-profile-extensibility-and-controlled-customization
plan: 03
subsystem: project-embedded-custom-profiles
tags: [csharp, system-text-json, project-roundtrip, custom-profiles, tdd]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: Core custom profile validation/export contracts and AppData catalog/store profile definitions from plans 04-01 and 04-02
provides:
  - Project-owned dirty-tracked embedded custom profile collection
  - Optional legacy-compatible CustomProfiles project JSON section
  - Save-time resolver for referenced local custom profiles absent from the project snapshot
  - Load diagnostics for malformed, duplicate, and bundled-name embedded profiles
affects: [profile-extensibility, project-roundtrip, profile-recovery, sharing]

tech-stack:
  added: []
  patterns:
    - Optional System.Text.Json root section appended after legacy project fields
    - Result-style project load diagnostics for recoverable embedded-profile failures
    - Referenced-name-only save filtering for project profile sharing

key-files:
  created:
    - tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs
  modified:
    - src/BS2BG.Core/Models/ProjectModel.cs
    - src/BS2BG.Core/Models/CustomProfileDefinition.cs
    - src/BS2BG.Core/Serialization/ProjectFileService.cs
    - src/BS2BG.App/Services/UserProfileStore.cs
    - tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs

key-decisions:
  - "Embedded project profiles are project-owned mutable definitions so collection edits and metadata/table replacement participate in dirty tracking."
  - "CustomProfiles is omitted unless at least one non-bundled referenced profile resolves from the project or save context."
  - "Invalid embedded profile entries produce diagnostics while legacy preset Profile strings and project data still load."

patterns-established:
  - "ProjectSaveContext resolves referenced local custom profiles at save time before serialization."
  - "ProjectLoadResult preserves project open behavior while surfacing code-bearing embedded-profile diagnostics."

requirements-completed: [EXT-03, EXT-05]

duration: 22 min
completed: 2026-04-27
---

# Phase 04 Plan 03: Embedded Custom Profile Project Serialization Summary

**Legacy-compatible `.jbs2bg` projects now embed exactly referenced custom profile definitions with load diagnostics for malformed or conflicting embedded data**

## Performance

- **Duration:** 22 min
- **Started:** 2026-04-27T08:36:22Z
- **Completed:** 2026-04-27T08:58:30Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added `ProjectModel.CustomProfiles` as a dirty-tracked project collection and made `CustomProfileDefinition` mutable/cloneable for project-owned profile snapshots.
- Extended `ProjectFileService` with `ProjectSaveContext`, `ProjectLoadResult`, and code-bearing `ProjectLoadDiagnostic` records.
- Added optional top-level `CustomProfiles` serialization after `SliderPresets`, `CustomMorphTargets`, and `MorphedNPCs`, omitted when empty to preserve no-custom byte output.
- Implemented referenced-name filtering so only non-bundled custom profiles used by presets are embedded, resolved first from `ProjectModel.CustomProfiles` and then from save-time context.
- Added diagnostics for malformed embedded profiles, duplicate embedded names, and bundled-name collisions while preserving legacy preset `Profile` strings.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add project model embedded-profile tests** - `1d13dd69` (test)
2. **Task 1 GREEN: Add project embedded profile model support** - `a4e2f120` (feat)
3. **Task 2 RED: Add embedded profile serialization tests** - `b7ca4900` (test)
4. **Task 2 GREEN: Serialize referenced embedded custom profiles** - `46a5bbbb` (feat)

**Plan metadata:** pending final docs commit

_Note: Both plan tasks used TDD RED/GREEN commits._

## Files Created/Modified

- `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs` - Covers absent optional section compatibility, project dirty tracking, referenced save filtering, context resolution, load hydration, and embedded-profile diagnostics.
- `src/BS2BG.Core/Models/ProjectModel.cs` - Adds `CustomProfiles` ownership, dirty tracking, replace/clone behavior, and profile-name sorting support.
- `src/BS2BG.Core/Models/CustomProfileDefinition.cs` - Converts profile definitions into mutable project nodes with XML-documented construction and clone semantics.
- `src/BS2BG.Core/Serialization/ProjectFileService.cs` - Adds optional `CustomProfiles`, save context, load result diagnostics, DTO mapping, referenced-profile filtering, bundled-name rejection, and validation-backed embedded hydration.
- `src/BS2BG.App/Services/UserProfileStore.cs` - Replaces record `with` usage with explicit construction after profile definitions became mutable project nodes.
- `tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs` - Replaces record `with` usage with explicit construction for the updated profile definition contract.

## Decisions Made

- Embedded custom profiles are mutable project nodes rather than immutable records so project-local profile metadata/table replacement can mark dirty state like presets and morph targets.
- The optional `CustomProfiles` root field is appended after all legacy root fields and has `WhenWritingNull` omission so existing no-custom projects retain legacy JSON shape.
- Save embeds only resolved, non-bundled custom profiles referenced by preset `ProfileName`; unrelated local or project profiles remain private.
- Embedded profile validation reuses `ProfileDefinitionService` over raw JSON elements so malformed embedded entries cannot abort legacy project loading.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated App/test record-copy call sites after making profile definitions mutable**
- **Found during:** Task 1 (Add project model support for embedded custom profiles)
- **Issue:** `CustomProfileDefinition` changed from a record to a dirty-trackable project node, so existing record `with` expressions in the App store and validation tests no longer compiled.
- **Fix:** Replaced record-copy expressions with explicit `CustomProfileDefinition` construction preserving the same values.
- **Files modified:** `src/BS2BG.App/Services/UserProfileStore.cs`, `tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs`
- **Verification:** `dotnet test --filter FullyQualifiedName~ProjectFileServiceCustomProfileTests` compiled and passed after the fix.
- **Committed in:** `a4e2f120`

**2. [Rule 1 - Bug] Preserved project loading when embedded profile numeric fields are malformed**
- **Found during:** Task 2 (Serialize optional CustomProfiles section)
- **Issue:** Strongly typed embedded profile DTO numeric properties caused `System.Text.Json` to throw before diagnostics could be emitted, blocking legacy project load for malformed embedded entries.
- **Fix:** Store embedded profile payloads as `JsonElement` and validate each raw element through `ProfileDefinitionService`, allowing `EmbeddedProfileInvalid` diagnostics while legacy project fields hydrate.
- **Files modified:** `src/BS2BG.Core/Serialization/ProjectFileService.cs`
- **Verification:** `LoadWithDiagnosticsReportsMalformedEmbeddedProfileAndKeepsLegacyProjectData` passes in focused test run.
- **Committed in:** `46a5bbbb`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes were necessary to satisfy dirty tracking and diagnostic-load correctness without expanding scope beyond embedded project profile persistence.

## Issues Encountered

- A parallel final verification attempt caused a transient PDB file lock between `dotnet test` and `dotnet build`. Re-running `dotnet build BS2BG.sln` after the test process exited passed cleanly.

## Known Stubs

None.

## Threat Flags

None - the project JSON trust boundary, embedded profile validation, referenced-only information disclosure control, and legacy field compatibility were covered by the plan threat model.

## Verification

- `dotnet test --filter FullyQualifiedName~ProjectFileServiceCustomProfileTests` — passed during Task 1 GREEN (2 tests).
- `dotnet test --filter "FullyQualifiedName~ProjectFileServiceCustomProfileTests|FullyQualifiedName~ProjectFileServiceTests"` — passed during Task 2 GREEN and final verification (19 tests).
- `dotnet build BS2BG.sln` — passed after final focused tests (0 warnings, 0 errors).

## TDD Gate Compliance

- RED gate commits: `1d13dd69`, `b7ca4900`
- GREEN gate commits: `a4e2f120`, `46a5bbbb`
- Refactor gate: not needed; implementation passed focused tests and build without a separate cleanup commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

The project round-trip layer can now carry project-scoped embedded profiles for sharing and recovery. Plans 04-04 and 04-06 can consume `ProjectLoadResult.Diagnostics` and `ProjectModel.CustomProfiles` to present missing-profile recovery and explicit local-vs-embedded conflict workflows.

## Self-Check: PASSED

- Created/modified files verified: `ProjectFileServiceCustomProfileTests.cs`, `ProjectModel.cs`, `CustomProfileDefinition.cs`, `ProjectFileService.cs`, and `04-03-SUMMARY.md` exist.
- Commits verified: `1d13dd69`, `a4e2f120`, `b7ca4900`, and `46a5bbbb` exist in git history.
- Verification commands passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
