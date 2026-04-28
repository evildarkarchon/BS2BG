---
phase: 06-compose-custom-profiles-in-headless-generation
verified: 2026-04-28T07:18:28Z
status: passed
score: 6/6 must-haves verified
overrides_applied: 0
---

# Phase 6: Compose Custom Profiles in Headless Generation Verification Report

**Phase Goal:** Projects that reference embedded or custom profiles generate through the CLI with request-scoped catalog semantics matching GUI and portable bundle output.
**Verified:** 2026-04-28T07:18:28Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Headless generation composes bundled profiles with the project's embedded/custom profile definitions before validation and generation. | ✓ VERIFIED | `HeadlessGenerationService.Run` loads the project, immediately builds `requestProfileCatalog = profileCatalogComposer.BuildForProject(project)`, and uses it for `ProjectValidationService.Validate`, `GenerateTemplates`, and `BosJsonExportWriter.Write` (`src/BS2BG.Core/Automation/HeadlessGenerationService.cs:61-109`). |
| 2 | CLI `generate` validates and generates template and BoS output using the same request-scoped catalog recipe as GUI and portable bundle paths. | ✓ VERIFIED | CLI `generate` delegates to `HeadlessGenerationService`; portable bundles construct the same `RequestScopedProfileCatalogComposer` and pass the composed catalog into validation, templates, and BoS output (`Program.cs:95-107`, `PortableProjectBundleService.cs:189-276`). |
| 3 | Regression tests prove a project with an embedded custom profile does not silently fall back to bundled profile semantics in CLI generation. | ✓ VERIFIED | `HeadlessGenerationServiceUsesEmbeddedCustomProfileFromProjectFile` and `ProgramMainGenerateAllUsesEmbeddedCustomProfileFromSavedProject` compare `templates.ini` and `Alpha.json` bytes to request-scoped expected output and assert templates differ from bundled-only fallback (`CliGenerationTests.cs:185-219`, `375-409`). |
| 4 | Portable bundle generation and CLI generation share the same Core catalog-composition rule so custom-profile semantics do not drift between automation outputs. | ✓ VERIFIED | Both services initialize `RequestScopedProfileCatalogComposer` from their constructor catalog. Bundle plan uses `BuildForProject` plus `ResolveReferencedCustomProfiles`; CLI/headless uses `BuildForProject` after project load (`HeadlessGenerationService.cs:29,61`; `PortableProjectBundleService.cs:29,79,189,195`). |
| 5 | Composer edge cases are explicit: duplicate names, bundled-name filtering, null/empty profile names, unreferenced custom profiles, deterministic ordering, and project-vs-save-context precedence are covered by focused tests. | ✓ VERIFIED | Composer code filters blank/bundled names, dedupes case-insensitively, first project definition wins, and project profiles override save context (`RequestScopedProfileCatalogComposer.cs:60-123`). Direct tests cover first-seen ordering, bundled/blank/unreferenced filtering, project-over-save-context, and save-context fallback (`RequestScopedProfileCatalogComposerTests.cs:10-95`). |
| 6 | AUTO-01: headless CLI generation continues to use existing Core project loading, validation, generation, and export writers without direct CLI file-output logic or added external custom-profile lookup. | ✓ VERIFIED | `Program.cs` constructs `HeadlessGenerationRequest` and calls `CreateGenerationService().Run(request)`; grep found no `File.WriteAllText`, `templates.ini`, `morphs.ini`, `--profiles-dir`, `APPDATA`, environment-variable, `IUserProfileStore`, or App dependency implementation in CLI/headless code beyond a documentation comment saying CLI does not reference BS2BG.App. |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/BS2BG.Core/Generation/RequestScopedProfileCatalogComposer.cs` | Shared Core request-scoped catalog composition and referenced custom-profile resolution | ✓ VERIFIED | Exists and substantive. Provides `BuildForProject` and `ResolveReferencedCustomProfiles`; bundled entries stay first and referenced custom profiles are appended as non-editable catalog entries. |
| `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` | CLI/headless validation, template generation, and BoS writing through composed request catalog | ✓ VERIFIED | Exists, wired to composer field, builds one request catalog after load, blocks unresolved custom profiles, then uses same catalog for outputs. |
| `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` | Portable bundle reuse of shared catalog composition and resolved profile-set rule | ✓ VERIFIED | Exists, wired to composer; validation, missing-profile checks, profile entries, templates, and BoS output use the shared request-scoped semantics. Sanitized profile filename collisions are deterministically suffixed. |
| `tests/BS2BG.Tests/RequestScopedProfileCatalogComposerTests.cs` | Direct composer regression coverage for review-identified edge cases | ✓ VERIFIED | Covers first-seen order, bundled/blank/unreferenced filtering, project precedence, and save-context fallback. |
| `tests/BS2BG.Tests/TestProfiles.cs` | Shared test profile factories and expected-output helpers | ✓ VERIFIED | Provides reusable project/profile/catalog/expected-output helpers used by CLI and composer tests. |
| `tests/BS2BG.Tests/CliGenerationTests.cs` | Service-level and Program.Main CLI regressions for embedded custom-profile output bytes | ✓ VERIFIED | Contains service-level and in-process CLI output-byte tests plus unresolved custom-profile blocking tests. |
| `tests/BS2BG.Tests/PortableBundleServiceTests.cs` | Bundle catalog/profile-entry behavior delegates to shared composer semantics | ✓ VERIFIED | Contains output-byte tests for local and embedded custom profiles, missing-profile tests, referenced/deduplicated profile entries, and sanitized filename collision regression. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `HeadlessGenerationService.cs` | `RequestScopedProfileCatalogComposer.cs` | `BuildForProject(project)` immediately after project load | ✓ WIRED | `profileCatalogComposer` field is initialized from constructor catalog; `BuildForProject(project)` occurs at line 61 before validation and generation. |
| `PortableProjectBundleService.cs` | `RequestScopedProfileCatalogComposer.ResolveReferencedCustomProfiles` | Same resolved profile set feeds bundle profile entries and request catalog feeds validation/templates/BoS | ✓ WIRED | `BuildForProject` at line 189; `ResolveReferencedCustomProfiles` at line 195; `requestProfileCatalog` is passed to validation, `AddGeneratedOutputEntries`, and missing-profile detection. |
| `CliGenerationTests.cs` | `templates.ini` and BoS JSON bytes | Expected output generated with request-scoped catalog and compared against CLI output | ✓ WIRED | Tests compare actual `templates.ini` and `Alpha.json` bytes with expected output from `TestProfiles.WriteExpectedOutputs(..., CreateRequestScopedCatalog(...))` and assert divergence from bundled fallback. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `HeadlessGenerationService.Run` | `requestProfileCatalog` | `ProjectFileService.Load` → `ProjectModel.CustomProfiles`/preset profile names → `RequestScopedProfileCatalogComposer.BuildForProject(project)` | Yes | ✓ FLOWING — composed catalog is used for validation, template generation, and BoS writing; unresolved custom profile names block before output writes. |
| `RequestScopedProfileCatalogComposer` | Resolved custom profile list | `project.SliderPresets` first-seen profile references, `project.CustomProfiles`, optional `ProjectSaveContext` | Yes | ✓ FLOWING — filters bundled/blank/unreferenced names and returns actual `CustomProfileDefinition` objects, not static fallback data. |
| `PortableProjectBundleService.BuildPlan` | `requestProfileCatalog` / `bundleProfiles` | Request project plus save context via shared composer | Yes | ✓ FLOWING — profile entries, missing-profile checks, validation, templates, and BoS output all consume composer outputs. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Phase 6 targeted regressions pass | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~RequestScopedProfileCatalogComposerTests|FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"` | Passed: 64, Failed: 0, Skipped: 0. Existing analyzer warnings were emitted but did not fail the run. | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AUTO-01 | `06-01-PLAN.md` | User can run a headless CLI generation path that uses the same Core services and output semantics as the GUI. | ✓ SATISFIED | CLI `generate` delegates to `HeadlessGenerationService`, which loads projects through `ProjectFileService`, validates with `ProjectValidationService`, generates through `TemplateGenerationService`/`MorphGenerationService`, and writes through Core export writers. Phase-specific gap closure for embedded custom profiles is implemented and covered by byte-level regressions. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | - | - | - | No blocker stub/placeholder/direct-output-writing/external-profile-lookup anti-patterns found in phase source files. `return null` in `RequestScopedProfileCatalogComposer.NormalizeCustomProfileName` is intentional filtering for blank/bundled names. |

### Human Verification Required

None. This phase is headless/Core behavior and is covered by code inspection plus targeted automated tests.

### Gaps Summary

No blocking gaps found. The codebase evidence shows the phase goal is achieved: headless CLI generation uses a request-scoped catalog built from bundled plus embedded project custom profiles; portable bundle output uses the same shared Core composer semantics; byte-level regressions prove custom profile output does not fall back to bundled semantics; and review fixes for unresolved custom-profile blocking and sanitized bundle profile filename collisions are present and tested.

---

_Verified: 2026-04-28T07:18:28Z_
_Verifier: the agent (gsd-verifier)_
