---
phase: 06
phase_name: "compose-custom-profiles-in-headless-generation"
project: "BS2BG (Bodyslide to Bodygen)"
generated: "2026-04-28"
counts:
  decisions: 8
  lessons: 7
  patterns: 8
  surprises: 6
missing_artifacts:
  - "06-UAT.md"
---

# Phase 06 Learnings: compose-custom-profiles-in-headless-generation

## Decisions

### CLI Generate Is Embedded-Only For Custom Profiles
Standalone CLI generation composes bundled profiles with project-embedded custom profile definitions only; it does not add `%APPDATA%`, `IUserProfileStore`, environment variable, `--profiles-dir`, or App dependency lookup.

**Rationale:** Phase 6 keeps CLI generate portable-project scoped and avoids ambiguous external local profile sources unless future requirements explicitly add them.
**Source:** 06-01-SUMMARY.md; 06-01-PLAN.md

---

### Shared Composer Owns Request-Scoped Catalog Rules
Portable bundle generation and headless generation share `RequestScopedProfileCatalogComposer` for bundled plus referenced custom profile catalog composition.

**Rationale:** Validation and output bytes cannot drift between automation paths if both CLI generation and bundles consume the same Core catalog-composition rule.
**Source:** 06-01-SUMMARY.md; 06-VERIFICATION.md

---

### Project Profiles Win Over Save Context
When a referenced profile exists in both `ProjectModel.CustomProfiles` and `ProjectSaveContext.AvailableCustomProfilesByName`, the project-owned embedded definition wins.

**Rationale:** The opened/shared project must be the authoritative source for project-scoped custom profile semantics, while save context is a fallback resolver.
**Source:** 06-01-SUMMARY.md; 06-01-PLAN.md

---

### Request Catalog Is Built Once After Project Load
`HeadlessGenerationService` builds one request-scoped catalog immediately after project load and uses that same catalog for validation, `templates.ini`, and BoS JSON output.

**Rationale:** Using one catalog instance prevents validation from approving one profile set while generation writes with another.
**Source:** 06-01-SUMMARY.md; 06-VERIFICATION.md

---

### Unresolved Custom Profiles Block Headless Output
Headless generation treats unresolved non-bundled profile names as validation blockers rather than preserving neutral GUI fallback semantics.

**Rationale:** Automation output must not silently use bundled fallback profile math when the project intended a custom profile.
**Source:** 06-01-SUMMARY.md; 06-VERIFICATION.md

---

### Composer Is Read-Only And Does Not Clone Definitions
The composer returns source profile definitions without cloning because catalog construction is read-only and does not mutate project or save-context profile data.

**Rationale:** Avoiding unnecessary clones keeps the helper simple while preserving the documented non-mutating contract.
**Source:** 06-01-SUMMARY.md

---

### Bundle Profile Entries Keep Deterministic Dedupe Behavior
Portable bundle `profiles/` entries preserve deterministic ordering and now deterministically suffix sanitized filename collisions.

**Rationale:** Distinct valid custom profile names can sanitize to the same JSON archive path; a valid bundle should not fail because of filename collision.
**Source:** 06-01-SUMMARY.md; 06-VERIFICATION.md

---

### No New Writer Or CLI Output Logic
Phase 6 preserves existing Core project loading, validation, generation, and export writers without adding direct CLI file-output logic.

**Rationale:** The phase fixes catalog composition only; output writer semantics and CLI parsing remain unchanged to avoid scope creep and byte-output regressions.
**Source:** 06-01-PLAN.md; 06-VERIFICATION.md

---

## Lessons

### Direct Composer Tests Diagnose Cheaper Than Integration Tests
Composer edge cases were covered directly before relying on CLI or bundle integration tests.

**Context:** Direct tests made bundled-name filtering, blank/unreferenced names, deterministic ordering, and project-vs-save-context precedence easier to verify than through output-byte failures alone.
**Source:** 06-01-SUMMARY.md; 06-01-PLAN.md

---

### Byte-Level Regression Is Necessary For Profile Math Bugs
Tests compared actual `templates.ini` and BoS JSON bytes against request-scoped expected generation and bundled fallback divergence.

**Context:** The bug was not about command success or file existence; it was silent wrong-profile output bytes from bundled fallback semantics.
**Source:** 06-01-SUMMARY.md; 06-VERIFICATION.md

---

### Console Capture Must Be Serialized Across All Program.Main Tests
Expanded filtered test runs exposed stdout/stderr capture interference between CLI generation and portable bundle tests.

**Context:** `PortableBundleServiceTests` was added to the existing `ConsoleCaptureCollection` so in-process `Program.Main` output redirection is serialized across both classes.
**Source:** 06-01-SUMMARY.md

---

### Missing Embedded Profiles Need Automation-Specific Blocking
A saved project referencing a non-bundled custom profile that was not embedded could still validate and generate with bundled fallback math.

**Context:** Code review found this after the initial headless wiring; a missing-profile preflight was added before overwrite/output writes.
**Source:** 06-01-SUMMARY.md

---

### Bundle Profile Filename Collisions Are Real Edge Cases
Distinct valid custom profile names can sanitize to the same `profiles/*.json` archive path.

**Context:** Code review found this could turn an otherwise valid bundle into `IoFailure`; deterministic suffixing and a collision regression were added.
**Source:** 06-01-SUMMARY.md

---

### Shared Test Profile Factories Reduce Drift
Repeated custom profile setup moved into `TestProfiles.cs` instead of duplicating helper blocks across CLI, bundle, and composer tests.

**Context:** The plan explicitly called for shared factories so expected-output generation remained consistent across integration and direct composer tests.
**Source:** 06-01-PLAN.md; 06-01-SUMMARY.md

---

### Existing Analyzer Warnings Can Appear In Targeted Runs
Targeted tests emitted existing analyzer warnings in source/test files, while the final solution build completed with zero warnings and errors.

**Context:** The warnings were not related to Phase 6 behavior and did not require scope expansion.
**Source:** 06-01-SUMMARY.md

---

## Patterns

### Request-Scoped Catalog Composition
Build a catalog from bundled entries plus only referenced non-bundled custom profiles for each loaded project/request.

**When to use:** Use for automation paths where the active project's embedded definitions must influence validation and generated output without mutating the base catalog.
**Source:** 06-01-SUMMARY.md; 06-VERIFICATION.md

---

### First-Seen Reference Resolution
Collect referenced profile names from project presets in first-seen order, skip blank/bundled names, and deduplicate case-insensitively.

**When to use:** Use when custom profile inclusion should follow actual project references while remaining deterministic.
**Source:** 06-01-PLAN.md; 06-VERIFICATION.md

---

### Project-Then-SaveContext Fallback
Resolve referenced custom profiles from `ProjectModel.CustomProfiles` first, then from `ProjectSaveContext`.

**When to use:** Use in project sharing and bundle/headless workflows where embedded project data should take precedence but GUI save context can still supply referenced local profiles.
**Source:** 06-01-PLAN.md; 06-01-SUMMARY.md

---

### One Catalog For Validation And Writers
Pass the same request-scoped catalog to `ProjectValidationService.Validate`, template generation, and BoS JSON writing.

**When to use:** Use whenever validation needs to prove the exact profile set that output writers will use.
**Source:** 06-01-SUMMARY.md; 06-VERIFICATION.md

---

### Direct Edge-Case Tests Before Integration Regression
Write focused unit tests for catalog composition rules, then add CLI/bundle output-byte integration tests.

**When to use:** Use when a shared helper has many resolution rules and integration failures would otherwise be hard to diagnose.
**Source:** 06-01-PLAN.md; 06-01-SUMMARY.md

---

### Request-Scoped Expected Output Helpers
Expected CLI output bytes are generated through existing Core generation/export services using a request-scoped catalog, not hardcoded snapshots.

**When to use:** Use when asserting generated bytes should match current writer semantics while still detecting wrong catalog composition.
**Source:** 06-01-PLAN.md; 06-VERIFICATION.md

---

### Automation-Specific Missing Profile Blocking
Headless generation checks unresolved non-bundled profile names after catalog composition and before output writes.

**When to use:** Use in automation flows where silent fallback would produce misleading files; GUI can remain neutral/fallback-oriented, but generated automation bytes must be trusted.
**Source:** 06-01-SUMMARY.md

---

### Shared Console Capture Collection
All in-process `Program.Main` tests that redirect stdout/stderr participate in one serialized console-capture collection.

**When to use:** Use when multiple test classes exercise CLI commands in process and xUnit parallelism could race global `Console` state.
**Source:** 06-01-SUMMARY.md

---

## Surprises

### The Initial Bug Was Silent Success
CLI generation could succeed and write files while using bundled fallback profile math for a project that referenced an embedded custom profile.

**Impact:** Regression coverage had to assert byte equality against request-scoped output and byte divergence from bundled-only fallback.
**Source:** 06-01-PLAN.md; 06-VERIFICATION.md

---

### Review Found A Remaining Wrong-Math Path
After wiring the request-scoped catalog, code review still found that unresolved non-bundled custom profile references could fall back and generate.

**Impact:** A headless missing-profile preflight was added and covered by both service-level and `Program.Main` regressions.
**Source:** 06-01-SUMMARY.md

---

### Bundle Profile Filename Collision Was Not Covered Initially
Different custom profile names could sanitize to the same archive filename and cause bundle failure.

**Impact:** Bundle profile entry naming gained deterministic suffixing for collisions.
**Source:** 06-01-SUMMARY.md

---

### Test Helper Namespace Ambiguity Interrupted RED Setup
The initial RED test run hit a test-helper namespace ambiguity before the intended missing-production-code failure.

**Impact:** The helper ambiguity was corrected so RED represented the absent composer, not test compilation noise.
**Source:** 06-01-SUMMARY.md

---

### Program.Main Tests Interfered Across Suites
Adding more CLI and bundle tests exposed that console capture serialization needed to span both test classes.

**Impact:** `PortableBundleServiceTests` joined `ConsoleCaptureCollection`, and the targeted suite passed afterward.
**Source:** 06-01-SUMMARY.md

---

### One Small Phase Produced Multiple Review Fixes
Although Phase 6 had only one plan, it still required two code-review blocker fixes after the main implementation.

**Impact:** Final verification passed only after unresolved custom-profile blocking and bundle filename collision handling were added.
**Source:** 06-01-SUMMARY.md; 06-VERIFICATION.md
