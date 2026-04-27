---
phase: 1
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-27T00:57:51-07:00
plans_reviewed: [01-01-PLAN.md, 01-02-PLAN.md, 01-03-PLAN.md, 01-04-PLAN.md, 01-05-PLAN.md, 01-06-PLAN.md, 01-07-PLAN.md]
---

# Cross-AI Plan Review - Phase 1

## Gemini Review

This review evaluates the seven execution plans for **Phase 1: Profile Correctness and Trust**.

### 1. Summary
The plans provide a highly surgical and technically sound path for establishing multi-profile support while maintaining strict byte-parity with the Java reference. The strategy successfully decouples "calculation fallback" from "project metadata," ensuring that modders can load unbundled profiles without losing data while seeing exactly how the tool will interpret their presets. The dependency ordering (Core -> ViewModel -> UI -> Gap Closure) is logical, and the emphasis on TDD with negative assertions (ensuring forbidden "warning" language is absent) demonstrates strong alignment with the locked Phase 1 context.

### 2. Strengths
- **Decoupled Fallback Logic:** Plan 01-05 correctly identifies and fixes a subtle UX deadlock where pre-selecting a fallback profile prevented users from explicitly "adopting" it.
- **Parity Protection:** The plans strictly adhere to the "sacred files" constraint, using focused C# assertions for Fallout 4 behavior rather than rebaselining golden-file fixtures.
- **Contract Integrity:** Plan 01-07 proactively aligns the project's high-level roadmap and requirements with the "neutral fallback" pivot, preventing process friction during final verification.
- **Granular TDD:** Every task includes specific `dotnet test` filter commands and clear acceptance criteria, ensuring that regressions in the complex slider math are caught immediately.

### 3. Concerns
- **LOW - Test Environment Pathing (Plan 01-01):** Tests that load root-level JSON files (`settings_FO4_CBBE.json`) often rely on `AppContext.BaseDirectory` or `RepositoryRoot` helpers. If the CI/CD environment or the specific test runner does not copy these content files to the output directory correctly, the factory tests will fail.
- **MEDIUM - UUNP Assertion Strength (Plan 01-06):** As noted in `01-REVIEW.md`, if the UUNP BoS JSON assertions remain too generic (only checking for the absence of `slidername1`), they might pass even if the Skyrim CBBE output is accidentally returned. While Plan 06 aims to close this gap, the executor must ensure the UUNP test cases are as rigorous as the FO4 ones.
- **LOW - ViewModel Sync Complexity (Plan 01-03/01-05):** Managing `syncingProfileFromPreset` flags to prevent infinite loops or accidental overwrites during selection changes is error-prone. The plan handles this, but it requires careful implementation to ensure the "explicit adoption" path remains distinct from "automatic sync."

### 4. Suggestions
- **Centralize RepositoryRoot:** In Plan 01-06, ensure the helper for loading root profiles is shared or identical to the one in `ProjectFileServiceTests.cs` to avoid duplicating logic for locating the project root.
- **Assert Outcome Ledger:** In Plan 01-06 Task 2, explicitly assert that the `BodyGenIniExportResult` paths match the provided directory path, and consider adding a case for a "Read Only" file system error to verify the atomic restoration logic (T-01-13/14).
- **Visual Validation Note:** Since "Human Verification" is required for theme readability (Plan 01-04), the summary for Task 2 should explicitly mention that the AXAML uses `DynamicResource` rather than `StaticResource` to support runtime theme switching.

### 5. Risk Assessment
**Overall Risk: LOW**

The phase goal is well-defined and the implementation is restricted to additive data (FO4 JSON) and non-breaking Core helpers. The most complex logic (slider math) is not being rewritten, only re-contextualized with different profile tables. The high volume of unit and headless UI tests provides a robust safety net against breaking the existing Skyrim-based byte parity. The pivot from "warnings" to "neutral info" significantly reduces the risk of UX friction for power users with custom body mods.

---

## the agent Review

# Phase 1 Plan Review: Profile Correctness and Trust

## Summary

The seven plans collectively deliver a bounded, well-scoped correctness fix for FO4 profile reuse plus neutral unresolved-profile fallback UX, fully respecting the locked D-01 through D-16 decisions. Wave structure (1: data + Core contracts, 2: ViewModel, 3: UI, 4: gap closure + contract reconciliation) is dependency-correct. The standout strength is rigorous TDD with explicit RED/GREEN expectations and acceptance criteria expressed as grep-able patterns; the standout concern is the test-design weakness later flagged in `01-REVIEW.md` (UUNP BoS JSON assertion is too weak) plus a circular contract-update plan (07) that retroactively narrows scope after implementation rather than during discuss-phase.

## Strengths

- **Locked-decision discipline.** Every plan re-cites D-numbers in `must_haves.truths` and acceptance criteria, making it hard for executors to drift into prohibited warning/inference UX.
- **Sacred-file protection is explicit.** Plan 06 includes `git diff -- tests/fixtures/expected` as an acceptance gate; multiple plans avoid editing `JavaFloatFormatting.cs` / `SliderMathFormatter.cs`.
- **TDD-first ordering.** Tests are authored before implementation in 01, 02, 03, 05, 06, with negative assertions (no `warning`, `mismatch`, `experimental` substrings) that prevent regression into forbidden language.
- **Architectural separation preserved.** Core gets a pure detection helper (`ContainsProfile`); App owns the user-facing fallback text, matching the AGENTS.md UI-free Core constraint.
- **Round-trip preservation is testable.** The unbundled `Community CBBE` round-trip tests prove the silent-rewrite anti-pattern cannot reappear.
- **Plan 05 correctly identified and isolated the adoption-path bug** (`SetSelectedProfileNameFromPreset` preselecting the fallback profile blocks re-selection) with a focused `GetSelectedCalculationProfile` separation.
- **Plan 07 explicitly records the context override** instead of silently letting verification re-fail; this is unusual but correct given D-08's "unless a later spec explicitly reverses" clause.

## Concerns

- **HIGH - Plan 06 UUNP coverage gap later confirmed by review.** `01-REVIEW.md` WR-01 confirmed exactly this: the UUNP-only assertion `NotContain("\"slidername1\": \"Breasts\"")` would pass if the UUNP variable accidentally received Skyrim CBBE JSON (since CBBE emits `Breasts` as `slidername2`). The plan's must-have truth claims "BoS JSON generation is protected by profile-specific tests for [...] Skyrim UUNP" but the acceptance criteria do not enforce positive UUNP-distinguishing assertions. This was foreseeable from the plan text alone.
- **MEDIUM - Plan 03 `OnSelectedProfileNameChangedReactive` interaction with undo is underspecified.** The plan adds a profile-change undo record (visible in current code) but does not say how it interacts with the syncing-from-preset path. Plan 05 then changes the same code path without re-checking the undo recording. There is no test asserting that selector sync during preset selection does not push undo entries, versus explicit user changes that do. This could produce phantom undo entries when navigating presets with unbundled profiles.
- **MEDIUM - Plan 04 release-doc placement is loosely specified.** "Concise FO4 profile note" with `contains: "Fallout 4 CBBE profile"` is the only acceptance criterion. PROF-05 deserves a stronger contract: at minimum, the note should call out `1.0` defaults, `1.0` multipliers, and empty inverted seed assumptions so a future calibration phase can detect drift. The implemented `docs/release/README.md` does this, but the plan did not require it.
- **MEDIUM - Plan 07 is structurally backwards.** Updating ROADMAP/REQUIREMENTS after implementation to match what was built is contract reconciliation, not gap closure. The right time to narrow PROF-03/PROF-05 was during `/gsd-discuss-phase` before any plan was authored. As executed it works, but it sets a precedent: when verification fails because the contract is wrong, the plan rewrites the contract. Acceptable here because D-05 through D-08 predated and constrained PROF-03 wording, but the plan should explicitly say "this is contract reconciliation, not requirement reduction."
- **LOW - Plan 01 FO4 multiplier semantics are ambiguous.** D-02 says "multipliers at `1.0`" and the plan seeds 31 sliders with explicit `1.0` multipliers. But `settings.json` and `settings_UUNP.json` have empty `Multipliers: {}` (no entries), implying default-1.0 by absence. Phase 1 introduces a different convention (explicit 1.0 entries) for FO4. Not wrong, but it creates two valid "1.0 multiplier" representations and tests do not enforce which is canonical.
- **LOW - Plan 03's exact-string fallback copy creates copy/test coupling.** The exact sentence is duplicated in the ViewModel implementation, ViewModel test, UI-SPEC, AppShell test, and Plan 03/04 acceptance criteria. Any future copy edit or i18n work requires touching all locations. A constants class or test helper extracting the format string would reduce this.
- **LOW - No regression test for "what happens when an unbundled profile is later bundled."** If a future phase ships `Community CBBE` as a bundled profile, currently displayed fallback panels should disappear automatically. There is no test for this transition. Likely unimportant for Phase 1 but worth noting for Phase 4 custom profile management.
- **LOW - Plan 06 does not test the export writer path for FO4.** It tests `BodyGenIniExportWriter.Write` with two profiles but not specifically the `BosJsonExportWriter.Write(directory, presets, catalog)` integration with FO4 presets producing distinct on-disk files. The formatter test covers `FormatBosJson` directly but not the writer's per-preset profile-lookup wiring (`profileCatalog.GetProfile(preset.ProfileName)`).

## Suggestions

- Strengthen Plan 06 UUNP assertions: require positive UUNP-distinguishing assertions such as exact `slidersnumber` or per-slider `highvalueN`/`lowvalueN` values, plus `skyrimUunpJson.Should().NotBe(skyrimCbbeJson).And.NotBe(fallout4CbbeJson)`.
- Add a dedicated `BosJsonExportWriter`-level FO4 test in Plan 06 that writes to a temp directory and asserts file contents include FO4-only sliders, proving the writer's catalog lookup wiring and not just the formatter.
- Plan 03 should explicitly assert no-undo-on-preset-sync behavior to prevent phantom undo entries when navigating presets that have unbundled profiles.
- Plan 04 should require the release note to enumerate seed assumptions (`1.0` defaults, `1.0` multipliers, empty inverted) and reference the FO4 slider list, not just contain the phrase "Fallout 4 CBBE profile."
- Plan 07 should be reframed as a `/gsd-discuss-phase` artifact correction, with a preamble noting that PROF-03/PROF-05 wording was authored before D-05 through D-08 were locked and the contract is being aligned, not reduced.
- Add a test that deleting `settings_FO4_CBBE.json` makes the factory throw with a useful message. The plan claims this in pitfall A1 but does not enforce it as an acceptance criterion. `LoadRequiredProfile` already does this; a test would prevent silent regression to the old fallback-to-`settings.json` behavior.
- Extract the fallback copy to a shared constant such as `TemplatesViewModel.UnresolvedProfileFallbackTemplate` so tests, AXAML, and ViewModel reference one source.
- Plan 01 should pick one multiplier convention. Either drop the explicit `1.0` entries to match the empty-Multipliers convention in `settings.json`/`settings_UUNP.json`, or document why FO4 enumerates them, such as a future calibration entrypoint.

## Risk Assessment

**Overall risk: LOW**

The plans operate inside an established Core/App architecture with strong existing test coverage, target a bounded behavioral correctness fix (FO4 catalog wiring + neutral fallback UX), and are explicitly forbidden from touching the highest-risk surfaces (`SliderMathFormatter`, `JavaFloatFormatting`, golden expected fixtures). All material risks (silent FO4 reuse, lost unbundled profile names, prohibited warning UX, golden-file edits) have direct test gates and acceptance criteria that catch them. The MEDIUM concerns are quality-of-coverage and process-shape issues that do not threaten Phase 1 correctness; they threaten future maintainability and would have been better caught in plan-checker than in code review. No HIGH-severity production risk; the HIGH-severity item (Plan 06 UUNP weakness) is a test-design defect that produces false confidence rather than wrong output.

---

## Codex Review

## Summary

Overall, these plans are strong: they respect the phase's core tension, which is making profile behavior explicit without adding forbidden warning or inference UX. The sequencing is mostly coherent, the tests are specific, and the plans keep sacred output fixtures out of scope. The main issue is that Plan 03 knowingly introduces the fallback-selector adoption bug that Plan 05 later fixes; if these are handed to separate agents or executed partially, that is a real regression window. I would fold Plan 05's behavior back into Plan 03 before implementation.

## Strengths

- Clear separation between Core profile resolution, App-layer fallback messaging, and release-facing FO4 calibration context.
- Good TDD framing, especially around FO4 no longer reusing Skyrim CBBE data.
- Strong adherence to locked decisions D-05 through D-10: no mismatch heuristics, no path inference, no FO4 experimental labels in-app.
- Good protection for golden fixtures and byte-level parity constraints.
- Plan 06 usefully closes the "templates only" evidence gap by adding BoS JSON and morph/export coverage.
- Plan 07 correctly fixes the planning contract instead of implementing warning UX that the user explicitly rejected.

## Concerns

- **HIGH:** Plan 03 and Plan 05 conflict. Plan 03 preselects fallback in `SelectedProfileName`; Plan 05 says that is a verified bug. This should not exist as an intermediate planned state.
- **HIGH:** Plan 03/05 ViewModel state is fragile. Switching presets, empty selector state, explicit adoption, missing-default refresh, preview, BoS preview, and generated templates all need to use the same effective calculation profile.
- **MEDIUM:** Plan 01's "existing `GenerateTemplatesUsesDefaultFallout4ProfileSettings` continues to pass" may be stale if that test currently encodes FO4-as-Skyrim behavior. The plan should explicitly allow updating non-golden expectations to the new FO4 seed contract.
- **MEDIUM:** New public `TemplateProfileCatalog.ContainsProfile` in Plan 02 needs XML documentation under this repo's AGENTS.md rules.
- **MEDIUM:** Plan 04 updates `docs/release/README.md`, but does not prove that this release-facing note is actually included in packaged/release artifacts.
- **MEDIUM:** Plan 07 depends on `gsd-sdk`; add fallback validation commands if unavailable.
- **LOW:** Broad UI negative assertions for words like `experimental`/`mismatch` can become brittle if they scan too much text. Scope them to main workflow UI text only.
- **LOW:** Project-controlled profile names are displayed in fallback text. XAML binding is safe, but very long or newline-heavy names could degrade layout.

## Suggestions

- Merge Plan 05's selector behavior into Plan 03: unresolved saved profiles should leave `SelectedProfileName` empty from the first implementation.
- Add explicit tests that empty `SelectedProfileName` does not overwrite `SelectedPreset.ProfileName` or accidentally resolve to default during reactive updates.
- Ensure `GenerateTemplates`, `RefreshPreview`, `RefreshSelectedBosJson`, `RebuildSetSliderRows`, and missing-default refresh all use a single helper for effective calculation profile.
- In Plan 01, assert every seeded FO4 slider appears in both `Defaults` and `Multipliers`, not only a few FO4-only names.
- In Plan 01, include a missing `settings_FO4_CBBE.json` fail-fast test through the search-directory overload.
- In Plan 04, add either packaging validation or an explicit note that release packaging inclusion is handled elsewhere.
- In Plan 07, run text-based fallback checks with `Select-String` if `gsd-sdk` is not installed.

## Risk Assessment

**Overall risk: MEDIUM.** The phase goal is achievable and the plans cover the important correctness surfaces, but the Templates ViewModel behavior is stateful enough to regress easily. The highest risk is not Core catalog work; it is selector synchronization and ensuring fallback calculation does not silently mutate saved profile semantics. If Plan 05 is folded into Plan 03 before execution and release-doc packaging is clarified, the risk drops to LOW-MEDIUM.

## Per-Plan Risk

- **01-01:** LOW-MEDIUM. Good data/catalog plan; tighten FO4 seed completeness and stale test expectations.
- **01-02:** LOW. Minimal Core change; add XML doc comment and whitespace/case edge coverage.
- **01-03:** HIGH as written. It contains behavior later identified as wrong.
- **01-04:** LOW-MEDIUM. Sound UI/doc plan; prove docs are release-facing in practice.
- **01-05:** MEDIUM. Correct fix, but reactive selector state needs careful regression coverage.
- **01-06:** MEDIUM. Valuable coverage, but keep tests focused and avoid path-fragile root-file loading.
- **01-07:** LOW-MEDIUM. Correct scope correction; add command fallback for `gsd-sdk`.

---

## Consensus Summary

All three reviewers consider the Phase 1 plans generally well-scoped, technically sound, and aligned with the locked user decisions that forbid profile inference, mismatch warnings, and in-app FO4 experimental labels. They agree the strongest parts of the plan set are the Core/App separation, preservation of unbundled profile names, use of focused tests instead of golden-file edits, and the explicit Plan 05 correction for fallback-profile adoption.

The main consensus risk is not the Core profile catalog work; it is the stateful Templates ViewModel selector/fallback behavior across Plan 03 and Plan 05. Codex and the agent both flagged Plan 03's intermediate fallback-preselection behavior as a high-risk or conflicting plan state because Plan 05 later identifies it as wrong. All reviewers also pointed to test-quality risks around profile-specific BoS JSON coverage, especially ensuring UUNP has positive distinguishing assertions rather than only absence checks.

### Agreed Strengths

- The plans preserve Java parity and avoid sacred golden expected fixture edits.
- Core responsibility remains limited to catalog/profile facts while App owns user-facing fallback messaging.
- The locked D-05 through D-10 decisions are repeatedly enforced, reducing risk of prohibited warning or inference UX.
- Round-trip preservation of unbundled profile names is explicit and testable.
- Plan 05 addresses an important adoption-path bug that would otherwise make fallback behavior confusing.

### Agreed Concerns

- **HIGH - Fold Plan 05 behavior into Plan 03 before any fresh execution.** Two reviewers flagged the Plan 03 -> Plan 05 sequence as creating a known-bad intermediate state where fallback profile preselection blocks explicit adoption.
- **MEDIUM/HIGH - Strengthen Plan 06 UUNP and BoS JSON assertions.** Gemini and the agent both called out weak UUNP-specific assertions; Codex also rated Plan 06 as medium risk and recommended focused, non-path-fragile coverage.
- **MEDIUM - Add or clarify tests for ViewModel selector sync side effects.** The agent and Codex both noted that empty selector state, explicit adoption, preview generation, missing-default refresh, BoS preview, and undo behavior need one consistent effective-calculation-profile path.
- **MEDIUM - Tighten FO4 profile data and packaging evidence.** Codex and the agent suggested stronger requirements for seeded FO4 sliders/multipliers, missing-file fail-fast behavior, and release-doc inclusion.
- **LOW/MEDIUM - Clarify Plan 07 as contract reconciliation.** The agent and Codex agree Plan 07 is directionally correct but should be framed as aligning pre-existing contradictory artifacts with locked decisions rather than reducing requirements after implementation.

### Divergent Views

- Gemini rates the overall plan set **LOW** risk, while Codex rates it **MEDIUM** due to ViewModel state fragility. The agent splits the difference: overall **LOW**, but with a high-severity test-design weakness in Plan 06.
- Gemini suggests an outcome-ledger/read-only export case in Plan 06; the other reviewers did not emphasize export-failure behavior for Phase 1.
- The agent suggests extracting fallback copy to a shared constant; Codex focuses more on reactive selector tests and empty selector behavior.
- Codex uniquely flags XML documentation for the new public `ContainsProfile` method, which is relevant under this repository's comment/docstring policy.
