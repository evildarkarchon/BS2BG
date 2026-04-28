---
phase: 05-automation-sharing-and-release-trust
plan: 09
subsystem: release-docs
tags: [release-packaging, documentation, bodygen, bodyslide, bos, xunit]

requires:
  - phase: 05-automation-sharing-and-release-trust
    provides: Release package trust script and ReleaseSmoke pattern from plan 05-08
provides:
  - Packaged BodyGen, BodySlide, and BodyTypes of Skyrim setup/troubleshooting guide
  - Release package inclusion checks for the setup guide
  - Tests that enforce docs-only guidance and the no-plugin-editing boundary
affects: [release-packaging, release-docs, automation-sharing-and-release-trust]

tech-stack:
  added: []
  patterns: [packaged-only setup guidance, ReleaseSmoke-gated package inspection, date-format docs verification]

key-files:
  created:
    - docs/release/BODYGEN-BODYSLIDE-BOS-SETUP.md
    - tests/BS2BG.Tests/ReleaseDocsTests.cs
  modified:
    - docs/release/README.md
    - docs/release/QA-CHECKLIST.md
    - tools/release/package-release.ps1

key-decisions:
  - "Keep BodyGen, BodySlide, and BodyTypes of Skyrim setup guidance in packaged release docs only, with tests rejecting setup-wizard/help-menu patterns."
  - "Use a refreshable Last verified: YYYY-MM-DD line in the setup guide instead of pinning assertions to a fixed research date."

patterns-established:
  - "Release docs tests assert source docs and packaging script text quickly while heavyweight package generation remains skipped behind ReleaseSmoke."
  - "Setup guidance emphasizes generic MO2/Vortex/manual output-location checks and generated-file verification before copying."

requirements-completed: [AUTO-05, AUTO-04]

duration: 3 min
completed: 2026-04-28
---

# Phase 05 Plan 09: Packaged Setup Guidance Summary

**Packaged BodyGen/BodySlide/BoS setup and output-location troubleshooting guide with release-package inclusion tests and no-plugin-editing boundary assertions.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-28T03:48:23Z
- **Completed:** 2026-04-28T03:51:18Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Added `ReleaseDocsTests.cs` with TDD coverage for required guide text, `Last verified` date format, packaging-script inclusion, ReleaseSmoke-gated zip inspection, QA checklist refresh requirements, and absence of setup wizard/help-menu UI patterns.
- Created `BODYGEN-BODYSLIDE-BOS-SETUP.md` covering BodySlide XML inputs, BodyGen INI placement, BodyTypes of Skyrim/BoS JSON output, common output-location mistakes, generated-file verification, troubleshooting, and the exact no-plugin-editing boundary.
- Updated release README, QA checklist, and packaging script so the setup guide is listed, verified before release, copied into the package root, and asserted as a required package file.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add release docs assertions** - `8c858da4` (test)
2. **Task 2 GREEN: Write packaged guide and include it in release** - `94baf619` (feat)

**Plan metadata:** pending final docs commit

_Note: Task 1 used the requested TDD RED gate; Task 2 made the tests pass._

## Files Created/Modified

- `tests/BS2BG.Tests/ReleaseDocsTests.cs` - Adds fast release docs assertions and a skipped ReleaseSmoke package inspection for the setup guide.
- `docs/release/BODYGEN-BODYSLIDE-BOS-SETUP.md` - New packaged setup/troubleshooting guide for BodyGen, BodySlide, BoS, and output-location mistakes.
- `docs/release/README.md` - Lists the setup guide in package layout and points users to it before copying generated files.
- `docs/release/QA-CHECKLIST.md` - Adds release verification for the guide and its refreshable `Last verified` date.
- `tools/release/package-release.ps1` - Copies the setup guide into the package root and asserts it as a required package file.

## Decisions Made

- Kept setup/troubleshooting guidance as packaged docs only for Phase 5; no app UI, wizard, Help-menu item, telemetry, or game-folder discovery was added.
- Used `Last verified: 2026-04-28` in the guide and test coverage that enforces only the date-token format so future release verification can refresh the date.
- Reused the existing ReleaseSmoke convention for package-generation inspection so default `FullyQualifiedName~ReleaseDocs` tests remain fast and do not publish a release zip.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- RED commits present: `8c858da4`
- GREEN commits present: `94baf619`
- REFACTOR commits: none needed

## Known Stubs

None. The `SignTool was not available` text in `package-release.ps1` is release trust copy from plan 05-08, not a stub.

## Threat Flags

None beyond the plan threat model. The only trust-boundary surface added is static packaged documentation already covered by T-05-09-01 and T-05-09-02.

## Verification

- `dotnet test BS2BG.sln --filter FullyQualifiedName~ReleaseDocs` - passed (4 passed, 1 ReleaseSmoke test skipped by design).
- `dotnet build BS2BG.sln` - passed with 0 warnings and 0 errors in the final build.
- Acceptance checks confirmed the guide contains BodyGen, BodySlide, BodyTypes of Skyrim, output location, `Last verified: YYYY-MM-DD`, and the exact no-plugin-editing boundary; README, QA checklist, package script, and tests contain the required release docs references.

## Next Phase Readiness

Phase 5 release docs now ship with packaged setup/troubleshooting guidance and fast tests protecting inclusion. The orchestrator can merge this worktree and perform centralized STATE/ROADMAP updates plus any broader phase-level verification.

## Self-Check: PASSED

- Verified created files exist on disk: `docs/release/BODYGEN-BODYSLIDE-BOS-SETUP.md`, `tests/BS2BG.Tests/ReleaseDocsTests.cs`.
- Verified modified files exist on disk: `docs/release/README.md`, `docs/release/QA-CHECKLIST.md`, `tools/release/package-release.ps1`.
- Verified task commits `8c858da4` and `94baf619` exist in git history.
- Verified `.planning/STATE.md` and `.planning/ROADMAP.md` were not modified in this parallel worktree.

---
*Phase: 05-automation-sharing-and-release-trust*
*Completed: 2026-04-28*
