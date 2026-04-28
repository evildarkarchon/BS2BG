---
phase: 01
phase_name: "profile-correctness-and-trust"
project: "BS2BG (Bodyslide to Bodygen)"
generated: "2026-04-28T00:25:08-07:00"
counts:
  decisions: 8
  lessons: 7
  patterns: 7
  surprises: 5
missing_artifacts: []
---

# Phase 01 Learnings: profile-correctness-and-trust

## Decisions

### Distinct Fallout 4 CBBE Profile Data
Fallout 4 CBBE was made a first-class bundled profile backed by root-level `settings_FO4_CBBE.json`, with neutral `1.0` defaults/multipliers and an empty inverted list until calibrated data exists.

**Rationale:** Phase 1 could not be trustworthy while Fallout 4 CBBE reused Skyrim CBBE data, and D-01/D-03 locked the bundled profile layout at repository root.
**Source:** 01-01-SUMMARY.md

---

### Preserve Golden Expected Fixtures
Profile-specific FO4 behavior is verified through focused assertions rather than by rebasing sacred golden files under `tests/fixtures/expected/**`.

**Rationale:** Distinct FO4 output intentionally differs from the old Skyrim-reuse path, but golden fixtures are byte-identical compatibility artifacts and should not be edited to absorb behavior changes.
**Source:** 01-01-SUMMARY.md

---

### Detect Unknown Profiles Without Changing Fallback Generation
`TemplateProfileCatalog.GetProfile` remains non-throwing for unknown profile names, while `ContainsProfile` exposes whether a saved profile is bundled.

**Rationale:** Generation must stay compatible and non-blocking, but App code needs a reliable signal to show neutral fallback information.
**Source:** 01-02-SUMMARY.md

---

### Preserve Unbundled Saved Profile Names
Unbundled saved profile names stay on `SelectedPreset.ProfileName`; preview and generation resolve through bundled fallback rules without normalizing the saved string.

**Rationale:** This preserves project round-trip compatibility while making fallback generation semantics visible to the user.
**Source:** 01-03-SUMMARY.md

---

### Keep Fallback Selector Blank Until Explicit Adoption
For unbundled saved profiles, the profile selector remains blank until the user explicitly chooses a bundled profile.

**Rationale:** Preselecting the fallback profile made choosing that same profile unable to adopt it, so blank selector state makes adoption an intentional user action.
**Source:** 01-05-SUMMARY.md

---

### Use Neutral In-App Information And Release-Only FO4 Context
Unresolved-profile feedback stays neutral in the Templates workflow, and Fallout 4 CBBE calibration confidence is documented in release notes rather than in-app experimental labels.

**Rationale:** Phase 1 decisions D-06/D-08 forbid in-app FO4 experimental labels and warning UX, while PROF-05 still requires users to find calibration context.
**Source:** 01-04-SUMMARY.md

---

### Treat Morph Generation As Profile-Independent
Morph generation is explicitly documented and tested as independent of preset profile names.

**Rationale:** Morph lines depend on assigned preset names, targets, and NPC assignments, not slider calculation profiles.
**Source:** 01-06-SUMMARY.md

---

### Align Source Contracts With Locked Context Decisions
ROADMAP and REQUIREMENTS now cite D-05 through D-08 as the accepted Phase 1 override for warning, mismatch, inference, and in-app experimental-label wording.

**Rationale:** Future executors and verifiers need the source contracts to reflect the implemented neutral-fallback scope rather than re-requesting prohibited warning UX.
**Source:** 01-07-SUMMARY.md

---

## Lessons

### Old Tests Can Encode The Bug Being Fixed
The existing FO4 default-catalog golden comparison reflected the prior Skyrim-profile reuse behavior once Fallout 4 CBBE correctly loaded distinct profile data.

**Context:** The test was updated to verify FO4-only generated output directly while preserving `tests/fixtures/expected/**`.
**Source:** 01-01-SUMMARY.md

---

### Avoid Parallel Focused And Full Test Runs
Starting a full `dotnet test` while a focused test run was still active caused an MSBuild file-lock failure.

**Context:** Re-running the full suite after the focused run completed passed.
**Source:** 01-01-SUMMARY.md

---

### UI Resource Tests Should Read Source AXAML
The initial RED path for `ThemeResources.axaml` targeted the test output directory, causing a path issue rather than a meaningful missing-resource failure.

**Context:** Correcting the test to read source AXAML made the RED failure represent the intended missing info resources.
**Source:** 01-04-SUMMARY.md

---

### Fallback Adoption Requires Distinct Selector State
Verification exposed that preselecting the fallback profile prevented users from adopting that same bundled profile because setting the same ComboBox value again did not mutate the preset.

**Context:** Plan 05 fixed this by leaving `SelectedProfileName` empty for unbundled saved profiles and making any bundled selection an explicit adoption path.
**Source:** 01-05-PLAN.md

---

### Contract Mismatches Should Be Resolved At The Contract
Verification found ROADMAP/PROF-03 still demanded warnings even though locked context decisions forbade warning, mismatch, inference, and in-app experimental UX.

**Context:** Plan 07 closed the gap by updating ROADMAP and REQUIREMENTS instead of implementing prohibited UI behavior.
**Source:** 01-07-PLAN.md

---

### Profile Coverage Can Use Small Distinguishing Assertions
Profile-specific BoS JSON and export coverage did not require broad fixture rewrites; small exact fragments were enough to prove profile-specific behavior.

**Context:** Plan 06 loaded root bundled profile files directly and asserted FO4-only names, shared-slider differences, and morph independence.
**Source:** 01-06-SUMMARY.md

---

### Headless UI Tests Do Not Replace Visual Readability Checks
Automated tests verify AXAML placement, bindings, automation names, resources, and forbidden copy, but not final rendered readability in light and dark themes.

**Context:** Verification and UAT both left visual fallback panel placement and theme readability as pending human confirmation.
**Source:** 01-VERIFICATION.md

---

## Patterns

### Bundled Profile Addition Checklist
Add a root-level profile JSON file, register it as app content, wire it in `TemplateProfileCatalogFactory`, and add factory tests proving order and distinct data.

**When to use:** Use when adding or changing bundled profile definitions so data, packaging, runtime loading, and tests remain aligned.
**Source:** 01-01-SUMMARY.md

---

### Detection Separate From Fallback Lookup
Use `ContainsProfile` for UI detection of unresolved saved profiles and keep generation paths on `GetProfile` for non-blocking fallback behavior.

**When to use:** Use when UI needs to explain fallback semantics without changing Core generation compatibility.
**Source:** 01-02-SUMMARY.md

---

### Neutral Fallback ViewModel State
Derive fallback text and visibility from `TemplateProfileCatalog.ContainsProfile` and `GetProfile`, not from paths, game folders, slider names, mismatch heuristics, or experimental labels.

**When to use:** Use for unresolved profile UX where saved profile identity must be preserved and generation should remain non-blocking.
**Source:** 01-03-SUMMARY.md

---

### Information Styling Separate From Warning Styling
Use dedicated `BS2BGInfo*` theme resources and headless UI tests that assert exact profile labels plus absence of mismatch/experimental language.

**When to use:** Use when displaying non-blocking explanatory state that must not be interpreted as a warning or error.
**Source:** 01-04-SUMMARY.md

---

### Effective Calculation Profile Helper
Centralize calculation profile resolution in `GetSelectedCalculationProfile()` so preview, BoS JSON, missing-default rows, and inspector rows share the same fallback semantics.

**When to use:** Use when a ViewModel must calculate from a fallback profile while preserving a different saved model value.
**Source:** 01-05-SUMMARY.md

---

### Focused Gap-Closure Tests Over Fixture Rewrites
Assert small profile-distinguishing output fragments and design invariants instead of rewriting broad generated expected files.

**When to use:** Use when verification identifies missing coverage around existing generation behavior and sacred golden fixtures should remain unchanged.
**Source:** 01-06-SUMMARY.md

---

### Cite Locked Decisions In Source Contracts
When context decisions intentionally narrow earlier requirement wording, update ROADMAP and REQUIREMENTS to cite those decisions directly.

**When to use:** Use after verification reveals that source contracts and locked user decisions point future agents toward conflicting outcomes.
**Source:** 01-07-SUMMARY.md

---

## Surprises

### Distinct FO4 Profile Broke The Old Default-Catalog Expectation
Making Fallout 4 CBBE truly distinct caused `GenerateTemplatesUsesDefaultFallout4ProfileSettings` to stop matching output that was based on Skyrim reuse.

**Impact:** The test needed to shift from golden comparison to direct FO4-only output assertions without modifying sacred expected fixtures.
**Source:** 01-01-SUMMARY.md

---

### Concurrent Test Runs Caused File Locks
A full test run started in parallel with a focused test run hit an MSBuild file-lock failure.

**Impact:** Verification had to be rerun sequentially; the full suite passed after the focused command completed.
**Source:** 01-01-SUMMARY.md

---

### Fallback Preselection Blocked Explicit Adoption
The fallback profile being preselected meant choosing the displayed fallback profile again did not update `SelectedPreset.ProfileName`.

**Impact:** A separate gap-closure plan was required to leave the selector blank for unbundled saved profiles and route explicit selection through the normal adoption path.
**Source:** 01-05-PLAN.md

---

### Automated Key-Link Verification Needed Manual Evidence
The SDK key-link verifier could not resolve most symbolic sources even though manual line checks verified the links.

**Impact:** The phase verification had to record manual evidence for wiring while still treating the links as pass-by-manual-evidence.
**Source:** 01-VERIFICATION.md

---

### A Non-Blocking Analyzer Warning Remained In Test Code
`dotnet test` emitted an existing CA1861 analyzer warning in `ExportWriterTests.cs`.

**Impact:** The warning did not fail verification and was recorded as out of scope for the coverage-only plan.
**Source:** 01-06-SUMMARY.md
