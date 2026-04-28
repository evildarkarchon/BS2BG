---
phase: 05-automation-sharing-and-release-trust
verified: 2026-04-28T05:15:00Z
status: gaps_found
score: 4/5 must-haves verified
overrides_applied: 0
gaps:
  - truth: "User can create a portable project bundle containing the `.jbs2bg` file, generated outputs, profile JSON copies, and a validation report without private local paths."
    status: failed
    reason: "Portable bundle creation is implemented, but advisory review found release-blocking trust gaps: overwrite can delete an existing bundle before replacement succeeds; generated bundle outputs use a stale/bundled-only catalog instead of the custom profiles that the bundle includes; the CLI bundle command can throw before mapping expected user/input failures to stable automation exit codes."
    artifacts:
      - path: "src/BS2BG.Core/Bundling/PortableProjectBundleService.cs"
        issue: "Create deletes the existing bundle at lines 107-114 before the replacement zip is fully written; BuildPlan/AddGeneratedOutputEntries validate and generate with constructor profileCatalog at lines 147-151 and 216-232, not a request-scoped catalog including request.Project.CustomProfiles/SaveContext custom profiles."
      - path: "src/BS2BG.Cli/Program.cs"
        issue: "bundle action calls CreateBundleServiceAndRequest/Preview/Create at lines 108-119 without wrapping project load, malformed JSON, missing profile assets, or filesystem errors into the documented 0/2/3/4 automation outcomes."
      - path: "src/BS2BG.App/AppBootstrapper.cs"
        issue: "PortableProjectBundleService is registered with ITemplateProfileCatalogService.Current at startup lines 45-52, creating a stale catalog snapshot for later project/custom-profile changes."
    missing:
      - "Write bundle archives to a temp file in the destination directory and atomically replace/move only after successful zip close/validation, preserving the previous bundle on failure."
      - "Build a request-scoped generation/validation catalog for bundle preview/create that includes bundled profiles plus referenced embedded/project/local custom profiles from ProjectModel.CustomProfiles and ProjectSaveContext."
      - "Wrap CLI bundle project loading and service composition so expected user/input/I/O failures map to AutomationExitCode values instead of unhandled exceptions."
---

# Phase 5: Automation, Sharing, and Release Trust Verification Report

**Phase Goal:** Users can reuse the proven Core generation path outside the GUI, package shareable projects, and verify release artifacts with clear setup guidance.
**Verified:** 2026-04-28T05:15:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run headless CLI generation that uses the same Core services and output semantics as the GUI. | ✓ VERIFIED | `src/BS2BG.Cli/BS2BG.Cli.csproj` is a dedicated `Exe` referencing Core and System.CommandLine; `Program.cs` creates `HeadlessGenerationRequest`; `HeadlessGenerationService.Run` loads projects, validates at line 61 before writes, uses `TemplateGenerationService`, `MorphGenerationService`, `BodyGenIniExportWriter.Write`, and `BosJsonExportWriter.Write` at lines 86-101. Focused phase tests passed. |
| 2 | User can create a portable project bundle containing the `.jbs2bg` file, generated outputs, profile JSON copies, and a validation report without private local paths. | ✗ FAILED | Core bundle service exists and writes the required layout, but `05-REVIEW.md` has two critical bundle findings and one CLI robustness warning. Code confirms existing bundles are deleted before replacement succeeds (`PortableProjectBundleService.cs:107-114`), bundle generated output uses a constructor catalog rather than request-scoped custom profiles (`PortableProjectBundleService.cs:28,147-151,216-232`), App registers the service with a startup catalog snapshot (`AppBootstrapper.cs:45-52`), and CLI bundle lacks expected exception-to-exit-code handling (`Program.cs:108-119,179-183`). |
| 3 | User can apply deterministic assignment strategy presets through testable seams that do not bypass existing random-provider abstractions. | ✓ VERIFIED | `AssignmentStrategyService` exposes `ComputeEligibility` and applies strategies through `IRandomAssignmentProvider` (`AssignmentStrategyService.cs:10-19,71-103`); seeded replay uses `DeterministicAssignmentRandomProvider` with a pinned Mulberry32 implementation and no `new Random(seed)` in strategy replay code; diagnostics call shared eligibility and report `No eligible preset after strategy rules` (`ProjectValidationService.cs:130-140`). UI wiring exists via `MorphsViewModel.ApplyStrategyCommand` and compiled-bound `Apply Strategy` controls. |
| 4 | User can verify downloaded release artifacts through checksums, signing information when available, and release-package assertions. | ✓ VERIFIED | `tools/release/package-release.ps1` packages `BS2BG.Cli.exe`, `SIGNING-INFO.txt`, `SHA256SUMS.txt`, unsigned docs, FO4 profile assets, optional SignTool flow, and external `.sha256`; `ReleaseTrustTests.cs` asserts checksum/signing/path-safety contracts with heavyweight ReleaseSmoke tests gated by trait. |
| 5 | User can access setup and troubleshooting guidance for BodyGen, BodySlide, BoS, and common output-location mistakes without BS2BG editing external game plugins. | ✓ VERIFIED | `docs/release/BODYGEN-BODYSLIDE-BOS-SETUP.md` contains BodyGen, BodySlide, BodyTypes of Skyrim/BoS, output-location troubleshooting, a `Last verified:` date, and the exact no-plugin-editing boundary. `package-release.ps1` copies it into the package and `ReleaseDocsTests.cs` asserts inclusion/no in-app wizard patterns. |

**Score:** 4/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/BS2BG.Cli/BS2BG.Cli.csproj` | Dedicated CLI executable | ✓ VERIFIED | Contains `OutputType>Exe`, `System.CommandLine`, Core project reference, publish settings, and copied profile assets. |
| `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` | Validation-first headless generation | ✓ VERIFIED | Validates before writes, preflights overwrite, delegates to existing Core writers. |
| `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` | Portable bundle creation | ✗ BLOCKER | Substantive and wired, but overwrite replacement and custom-profile generation catalog gaps prevent trusted portable bundle creation. |
| `src/BS2BG.Core/Morphs/AssignmentStrategyService.cs` | Deterministic strategy executor | ✓ VERIFIED | Implements seeded random, round-robin, weighted, race filters, groups/buckets through provider-compatible seams. |
| `tools/release/package-release.ps1` | Release trust packaging | ✓ VERIFIED | Includes checksums, optional signing metadata, package required-file assertions, normalized zip creation. |
| `docs/release/BODYGEN-BODYSLIDE-BOS-SETUP.md` | Packaged setup guidance | ✓ VERIFIED | Required guide exists with BodyGen/BodySlide/BoS troubleshooting and no-plugin-editing boundary. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `Program.cs` | `HeadlessGenerationRequest` / `HeadlessGenerationService` | `generate` command action | ✓ WIRED | `Program.cs:94-105` creates the typed request and returns the service exit code. |
| `HeadlessGenerationService` | `ProjectValidationService.Validate` | pre-write validation | ✓ WIRED | Validation runs at line 61 before any writer calls. |
| `HeadlessGenerationService` | `BodyGenIniExportWriter` / `BosJsonExportWriter` | existing writer calls | ✓ WIRED | Calls at lines 90 and 99; no direct `File.WriteAllText` output writer path found. |
| `PortableProjectBundleService` | `ProjectFileService` | `project/project.jbs2bg` entry | ✓ WIRED | `BuildPlan` adds serialized project entry via `projectFileService.SaveToString` at line 167. |
| `PortableProjectBundleService` | `ZipArchive` | structured bundle entries | ⚠️ PARTIAL | Uses `ZipArchive` at lines 113-123, but writes directly to final path after deleting old zip, so the trusted overwrite link is unsafe. |
| `PortableProjectBundleService` | request custom profiles | generation catalog | ✗ NOT_WIRED | The service includes custom profile JSON entries from `ProjectModel`/`SaveContext`, but validation/generation still uses the constructor `profileCatalog`, so custom profile bytes do not drive generated output. |
| `MorphsViewModel` | `AssignmentStrategyService` | `ApplyStrategyCommand` | ✓ WIRED | `MorphsViewModel.cs:1373` applies strategy over all `project.MorphedNpcs`; AppBootstrapper injects `AssignmentStrategyService`. |
| `package-release.ps1` | `docs/release/BODYGEN-BODYSLIDE-BOS-SETUP.md` | copy into package | ✓ WIRED | Script copies the doc and asserts it as a required package file. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `HeadlessGenerationService` | project/output text | `ProjectFileService.Load` → validation → generation services/writers | Yes for bundled profile projects | ✓ FLOWING |
| `PortableProjectBundleService` | bundle entries/output bytes | project serialization, diagnostics, profile export, staged writer files | Partially; custom profile JSON flows into `profiles/`, but generated output bytes do not use those custom profiles | ⚠️ HOLLOW for custom-profile bundle outputs |
| `MorphsViewModel` / `AssignmentStrategyService` | NPC preset assignments | persisted strategy rows + project NPC/preset collections | Yes | ✓ FLOWING |
| `package-release.ps1` | release trust artifacts | publish outputs + docs + hashing/signing logic | Yes | ✓ FLOWING |
| `BODYGEN-BODYSLIDE-BOS-SETUP.md` | setup guidance | static packaged docs copied by release script | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Phase 5 focused tests | `dotnet test "BS2BG.sln" --filter "FullyQualifiedName~CliGeneration|FullyQualifiedName~AssignmentStrategy|FullyQualifiedName~PortableBundle|FullyQualifiedName~ReleaseTrust|FullyQualifiedName~ReleaseDocs|FullyQualifiedName~MorphsViewModelStrategy"` | Passed: 94, Skipped: 3 ReleaseSmoke tests, Failed: 0 | ✓ PASS |
| Roadmap contract load | `gsd-sdk query roadmap.get-phase 5 --raw` | Returned phase goal and 5 success criteria | ✓ PASS |
| Deferred filtering | `gsd-sdk query roadmap.analyze --raw` | No later roadmap phases after Phase 5 | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| AUTO-01 | 05-01, 05-02, 05-07 | User can run a headless CLI generation path that uses the same Core services and output semantics as the GUI. | ✓ SATISFIED | CLI project, typed contracts, generate command, validation-first service, writer delegation, stable exit codes. |
| AUTO-02 | 05-06, 05-07 | User can create a portable project bundle containing project, generated outputs, profile copies, and validation report without private local paths. | ✗ BLOCKED | Bundle exists, but advisory CR-01/CR-02/WR-01 show trusted bundle creation is unsafe/incorrect for overwrite/custom profiles/CLI failures. |
| AUTO-03 | 05-03, 05-04, 05-05 | User can apply deterministic assignment strategy presets through testable seams without bypassing random-provider abstractions. | ✓ SATISFIED | Persisted strategy model, deterministic executor, diagnostics, GUI apply/save/undo workflow, provider seam. |
| AUTO-04 | 05-08, 05-09 | User can verify downloaded release artifacts through checksums, signing information when available, and release-package assertions. | ✓ SATISFIED | Package script and tests cover SHA-256, signing metadata, unsigned path, required files, path-safety assertions. |
| AUTO-05 | 05-09 | User can access setup/troubleshooting guidance without BS2BG editing plugins. | ✓ SATISFIED | Packaged guide and tests assert BodyGen/BodySlide/BoS guidance and the no-plugin-editing boundary. |

No orphaned Phase 5 requirement IDs found: all AUTO-01 through AUTO-05 are declared in Phase 5 plan frontmatter.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---:|---|---|---|
| `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` | 111 | `File.Delete(request.BundlePath)` before replacement write | 🛑 Blocker | Data-loss risk when overwriting an existing bundle and a later archive operation fails. |
| `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` | 28, 150, 221, 231 | Constructor `profileCatalog` used for validation/generation | 🛑 Blocker | Bundle may include correct custom profile JSON while generating incorrect outputs for that profile. |
| `src/BS2BG.Cli/Program.cs` | 108-119, 179-183 | Bundle command expected failures not wrapped | ⚠️ Warning | Missing/malformed projects or unresolved profile files can bypass stable automation exit-code contract. |

### Human Verification Required

No pending human verification remains. The Morphs strategy UI visual checkpoint was recorded as approved in `05-05-SUMMARY.md`, and the bundle preview visual checkpoint is recorded in `05-07-VISUAL-VERIFICATION.md`.

### Gaps Summary

Phase 5 is close but not goal-complete because AUTO-02 is not trusted yet. The bundle workflow has real source/test/UI surfaces, but advisory review found a data-loss overwrite path, a custom-profile correctness gap where generated outputs can disagree with bundled profile copies, and a CLI bundle robustness gap. These are not deferred to any later phase and block declaring the portable bundle path trusted.

---

_Verified: 2026-04-28T05:15:00Z_
_Verifier: the agent (gsd-verifier)_
