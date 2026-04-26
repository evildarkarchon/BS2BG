# Phase 1: Profile Correctness and Trust - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 01-profile-correctness-and-trust
**Areas discussed:** FO4 profile shape, Warning behavior, Profile inference, Trust evidence

---

## FO4 profile shape

### New profile data location

| Option | Description | Selected |
|--------|-------------|----------|
| Separate root JSON | Add a distinct bundled file alongside `settings.json`/`settings_UUNP.json`, minimizing Phase 1 churn while fixing the current FO4 reuse bug. | yes |
| Profiles folder now | Move toward `profiles/fallout4-cbbe.json` immediately; cleaner long-term, but overlaps Phase 4 custom profile management. | |
| Embedded defaults | Keep profile data in code and emit/copy it at runtime; fewer files, but less transparent for modders and tests. | |
| You decide | Planner can choose the smallest approach that satisfies distinct FO4 semantics and packaging. | |

**User's choice:** Separate root JSON
**Notes:** Keep Phase 1 minimal and avoid pulling in Phase 4 profile-folder migration.

### FO4 seed source

| Option | Description | Selected |
|--------|-------------|----------|
| Known FO4 sliders | Use the observed FO4 CBBE slider-name set from PRD/fixtures, defaults at 1.0, multipliers 1.0, inverted empty, and document best-effort calibration. | yes |
| Fixture-derived union | Generate the seed from the committed FO4 fixture XML during development, keeping the profile tightly tied to available test data. | |
| Manual curated subset | Only include high-confidence/common FO4 sliders; smaller file, but misses real presets and may still trigger unknown-slider noise. | |
| You decide | Planner can pick the safest seed source as long as FO4 never reuses Skyrim tables. | |

**User's choice:** Known FO4 sliders
**Notes:** Use PRD FO4 slider guidance.

### Existing Skyrim profile files

| Option | Description | Selected |
|--------|-------------|----------|
| Keep legacy files | Leave `settings.json` and `settings_UUNP.json` unchanged, add only the FO4 file, and defer broader profile-folder migration to Phase 4. | yes |
| Copy plus migrate | Ship legacy files and a new `profiles/` folder in parallel; clearer future path but more duplication to test now. | |
| Move all profiles | Adopt `profiles/` as the only bundled layout in Phase 1; clean but risks broad startup/package churn. | |
| You decide | Planner can preserve compatibility while choosing file placement details. | |

**User's choice:** Keep legacy files
**Notes:** Preserve existing packaging/layout.

### FO4 selector visibility

| Option | Description | Selected |
|--------|-------------|----------|
| Always visible | Show `Fallout 4 CBBE (Experimental)` alongside Skyrim profiles so users can choose it explicitly without enabling extra settings. | yes |
| Visible with tooltip | Show normal name in the dropdown but explain experimental status in adjacent tooltip/warning text. | |
| Hidden until enabled | Gate FO4 behind an experimental toggle; avoids accidental use but makes the distinct profile harder to discover. | |
| You decide | Planner can choose the least confusing UI within existing Templates tab patterns. | |

**User's choice:** Always visible
**Notes:** Later warning discussion refined this: FO4 should be visible as a normal profile with no experimental label.

---

## Warning behavior

### Warning placement

| Option | Description | Selected |
|--------|-------------|----------|
| Inline plus status | Show persistent inline warning near the profile selector/preview and repeat concise status text when generation/export uses risky profiles. | yes |
| Status only | Least UI churn, but easy to miss before exporting profile-dependent output. | |
| Modal dialogs | Hard to miss, but disruptive for normal FO4 experimental use and repeated generation. | |
| You decide | Planner can use existing Templates tab patterns to keep warnings visible without overbuilding UI. | |

**User's choice:** Inline plus status
**Notes:** Superseded by subsequent correction: avoid warnings generally.

### Warning triggers

| Option | Description | Selected |
|--------|-------------|----------|
| All risky states | Warn for experimental FO4, unknown saved profile, missing profile file, inferred profile, and likely slider/profile mismatch. | |
| Unknown only | Warn only when the app cannot resolve a saved or selected profile; simpler but leaves FO4 risk underexplained. | |
| Mismatch only | Focus warnings on likely wrong-game imports; useful but misses missing/unknown profile round-trip risks. | |
| You decide | Planner can choose warning triggers that satisfy PROF-03 with minimal UI churn. | |

**User's choice:** Bodygen files encompase many body mods, not all of which are profiled, so there should be no warnings.
**Notes:** This is a key correction. Do not add mismatch warnings for custom/unprofiled body mods.

### Missing bundled profile fallback

| Option | Description | Selected |
|--------|-------------|----------|
| Preserve and inform | Keep the saved profile name, do not treat it as an error, and show neutral informational text only if output will fall back to a bundled profile. | yes |
| Silent fallback | Use default Skyrim CBBE with no user-facing message; least noisy but risks hidden wrong output. | |
| Require selection | Ask the user to pick a bundled profile before generation/export; prevents hidden fallback but interrupts custom/unprofiled workflows. | |
| You decide | Planner can satisfy compatibility while avoiding scary warnings for unprofiled body mods. | |

**User's choice:** Preserve and inform
**Notes:** Informational fallback is allowed; warnings are not.

### FO4 experimental label

| Option | Description | Selected |
|--------|-------------|----------|
| Label only | Show `Fallout 4 CBBE (Experimental)` as neutral labeling, not a warning or blocker. | |
| No label | Treat FO4 like any other profile in the selector; least noisy but hides the calibration caveat from PROF-05. | yes |
| Warning banner | Keep a warning banner for FO4 specifically; clear but conflicts with avoiding warnings for unprofiled/body-mod variation. | |
| You decide | Planner can choose wording that communicates best-effort status without alarming users. | |

**User's choice:** No label
**Notes:** FO4 should not be marked experimental in the main workflow.

### FO4 confidence surfacing

| Option | Description | Selected |
|--------|-------------|----------|
| Docs and notes | Keep FO4 caveat in release notes/help text, not the main workflow. | |
| About/help link | Expose profile confidence in an optional help/details surface users can open when they want context. | |
| Do not surface | Treat FO4 as a normal bundled profile everywhere; downstream planner should adjust PROF-05 interpretation accordingly. | yes |
| You decide | Planner can find the least intrusive way to satisfy PROF-05 without warning users during generation. | |

**User's choice:** Do not surface
**Notes:** This creates tension with `PROF-05`; CONTEXT.md records the user decision for downstream interpretation.

---

## Profile inference

### Import profile choice

| Option | Description | Selected |
|--------|-------------|----------|
| Use selected profile | Keep current behavior: imported presets get the profile currently selected by the user; no automatic guessing from file path or slider names. | yes |
| Infer from path | Use file path hints like Fallout 4 or Skyrim folders; convenient but brittle with Mod Organizer and custom layouts. | |
| Infer from sliders | Detect likely game/body from slider-name overlap; useful but risky for custom bodies that share names differently. | |
| Ask each import | Prompt on import; explicit but adds friction for batch imports. | |

**User's choice:** Use selected profile
**Notes:** No import prompts or heuristics.

### Mismatch detection

| Option | Description | Selected |
|--------|-------------|----------|
| No heuristics | Do not infer or warn based on slider names; users may intentionally use unprofiled/custom bodies. | yes |
| Inform only | Detect likely mismatch and show neutral info without blocking; may still create noise for custom bodies. | |
| Auto switch | Automatically switch to FO4 when FO4-heavy sliders are detected; convenient but dangerous for edge cases. | |
| You decide | Planner can balance PROF-03 against avoiding false positives for custom bodies. | |

**User's choice:** No heuristics
**Notes:** Strongly tied to the no-warning custom body decision.

### Legacy project without Profile

| Option | Description | Selected |
|--------|-------------|----------|
| Use legacy flag | `isUUNP=true` maps to Skyrim UUNP, otherwise Skyrim CBBE, with no extra prompting. | yes |
| Ask on open | Prompt the user to choose; explicit but interrupts known-compatible old projects. | |
| Use current profile | Apply current dropdown preference; convenient but can corrupt old project semantics. | |
| You decide | Planner can preserve compatibility while deciding any UI details. | |

**User's choice:** Use legacy flag
**Notes:** Matches existing project-roundtrip spec.

### Unbundled saved profile generation

| Option | Description | Selected |
|--------|-------------|----------|
| Fallback visibly | Preserve the original name for round-trip, use Skyrim CBBE as the calculation fallback, and show neutral info that fallback math is being used. | yes |
| Block generation | Prevents accidental fallback math but blocks custom/unprofiled workflows. | |
| Use selected profile | Use the current dropdown profile for calculation while preserving saved name; flexible but less predictable. | |
| Silent fallback | Keep current behavior: use the catalog default with no message; simplest but hidden. | |

**User's choice:** Fallback visibly
**Notes:** Neutral visibility only; not a warning.

---

## Trust evidence

### FO4 proof threshold

| Option | Description | Selected |
|--------|-------------|----------|
| Distinct-table tests | Tests prove FO4 loads from its own JSON and never shares Skyrim CBBE/UUNP defaults, multipliers, or inverted-slider tables. | yes |
| Golden outputs | Require fixture/golden output changes for FO4 generation; stronger but may need non-Java expected baselines because Java has no FO4 profile. | |
| Manual QA only | Fastest, but weak for a parity-sensitive profile correctness phase. | |
| You decide | Planner can choose enough tests to satisfy PROF-01/PROF-04. | |

**User's choice:** Distinct-table tests
**Notes:** Minimum acceptance evidence for FO4 profile correctness.

### Golden expected files

| Option | Description | Selected |
|--------|-------------|----------|
| Do not alter | Keep `tests/fixtures/expected/**` sacred; add focused C# tests/fixtures for distinct FO4 profile behavior instead of rebasing Java golden files. | yes |
| Add new expected | Add a separate expected artifact for FO4 profile outputs, clearly not a Java-reference golden baseline. | |
| Regenerate expected | Rebaseline existing expected outputs; strongest snapshot coverage but conflicts with the Java-parity fixture policy unless explicitly justified. | |
| You decide | Planner can choose a safe test artifact strategy without mutating sacred files unnecessarily. | |

**User's choice:** Do not alter
**Notes:** Respect sacred fixture policy.

### UI/ViewModel coverage

| Option | Description | Selected |
|--------|-------------|----------|
| Selection and fallback | Cover profile selection updates preview/missing defaults, imports use selected profile, unresolved saved profile is preserved with visible neutral fallback info. | yes |
| Selection only | Keep tests to dropdown/profile changes; less coverage for unresolved profile project loads. | |
| Core only | Avoid UI tests in Phase 1; faster, but profile correctness is user-visible in Templates workflow. | |
| You decide | Planner can map coverage to existing TemplatesViewModel test patterns. | |

**User's choice:** Selection and fallback
**Notes:** Include user-visible behavior tests.

### Custom body warning pressure

| Option | Description | Selected |
|--------|-------------|----------|
| Assert no warnings | Add tests that unknown/custom slider names do not create mismatch warnings or block generation; only unresolved profile fallback produces neutral info. | yes |
| Ignore warnings | Do not add custom-body-specific tests; simpler but could allow warning behavior to creep back in later. | |
| Document only | Capture the policy in CONTEXT.md but leave enforcement to manual review. | |
| You decide | Planner can decide whether this policy needs automated coverage. | |

**User's choice:** Assert no warnings
**Notes:** Prevent future mismatch-warning regressions.

---

## the agent's Discretion

- Exact FO4 JSON file name.
- Exact neutral fallback message wording and placement.
- Exact test class names and fixture helper structure.

## Deferred Ideas

- `profiles/` folder migration and custom profile management — Phase 4.
- Authoritative Fallout 4 calibration assistant/community calibration workflow — v2 advanced modding unless pulled forward.
- Path or slider-name profile inference — deferred unless a later phase explicitly revisits it.
