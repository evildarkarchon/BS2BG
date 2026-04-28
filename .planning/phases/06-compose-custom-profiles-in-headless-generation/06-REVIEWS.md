---
phase: 6
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-28T06:51:08Z
plans_reviewed: [.planning/phases/06-compose-custom-profiles-in-headless-generation/06-01-PLAN.md]
---

# Cross-AI Plan Review - Phase 6

## Gemini Review

### Summary

The plan for Phase 6 is a well-targeted, surgical update that closes a critical parity gap between the CLI and GUI. By extracting the request-scoped catalog composition logic into a shared Core component (`RequestScopedProfileCatalogComposer`), the project ensures that headless generation and portable bundles follow the exact same rules for resolving embedded custom profiles. The strategy of using output divergence for regression testing - proving that CLI output matches the embedded profile rather than silently falling back to bundled defaults - is a high-signal validation approach that aligns well with the project's parity constraints.

### Strengths

- **Architectural Alignment**: Reuses the proven catalog-composition logic from `PortableProjectBundleService`, preventing logic drift between different automation outputs.
- **High-Signal Validation**: The TDD approach specifically targets the silent fallback bug by asserting that output bytes differ from bundled-only generation.
- **Clean Separation of Concerns**: Extracting the composer into `BS2BG.Core.Generation` keeps `HeadlessGenerationService` focused on orchestration while centralizing complex profile precedence rules.
- **Safety and Parity**: Maintains the sacred status of the Core writers and formatters, ensuring that the byte-identical output contract is not jeopardized by the catalog change.

### Concerns

- **LOW - CLI Environment Assumptions**: The plan correctly limits CLI generation to bundled plus project-embedded profiles. If a user expects CLI generation to pick up `%AppData%` profiles like the GUI, it will not. This is the correct portable-first decision, but it should be clearly noted in future CLI help or docs.
- **LOW - Refactoring Regression**: Moving private bundle helper logic into a public Core service carries a risk of breaking existing bundle tests. The plan mitigates this by requiring explicit regression checks for the bundle path in Task 2.
- **LOW - Duplicate Name Handling**: `TemplateProfileCatalog` throws on duplicate names. If a project embeds a profile that conflicts with a bundled one, the composer must ensure bundled precedence or filtering before constructing the catalog. The research mentions filtering, but the plan should ensure this is strictly implemented in the shared composer.

### Suggestions

- **Composer Unit Tests**: Task 2 should explicitly include unit tests for `RequestScopedProfileCatalogComposer` edge cases, including case-insensitive name collisions, unreferenced profile exclusion, and project-versus-save-context precedence.
- **Clarify CLI Overwrite Logic**: Since Phase 6 touches `HeadlessGenerationService`, ensure the composed catalog is also used for overwrite preflight checks if profile-specific output paths can vary by custom profile data.
- **XML Documentation**: Since `RequestScopedProfileCatalogComposer` becomes a shared Core contract, ensure `BuildForProject` documentation clearly defines the project-wins precedence rule for colliding profile names.

### Risk Assessment

Overall risk: **LOW**.

The plan is low-risk because it implements a read-only data resolution change rather than modifying slider math or file writers. By leveraging existing `PortableProjectBundleService` patterns, the implementation path is already de-risked by working code. The TDD regression tests provide strong confidence that the silent fallback failure state will be removed and protected against future regressions.

---

## the agent Review

### Summary

This is a tightly-scoped, well-researched single-plan phase that closes a clear correctness gap: `HeadlessGenerationService` currently passes its constructor-injected bundled-only catalog directly to validation and generation, so embedded custom profiles silently fall back to bundled semantics in CLI output. The plan extracts the existing request-scoped catalog logic from `PortableProjectBundleService` into a shared Core composer, wires `HeadlessGenerationService` through it, and adds byte-level regression tests that compare CLI output against request-scoped expected output and assert divergence from bundled fallback. The TDD ordering, sacred-file discipline, and reuse of established patterns are all sound. Main concerns are around the composer's `ResolveReferencedCustomProfiles` extraction risk and scope ambiguity about whether `ProjectSaveContext` should plumb through to the CLI.

### Strengths

- **Pattern reuse over invention**: The plan correctly identifies that `PortableProjectBundleService.BuildRequestProfileCatalog` already implements the target rule and chooses to extract and share rather than duplicate.
- **TDD ordering is correct**: Task 1 writes failing regressions first, Task 2 refactors bundle behavior, and Task 3 wires headless generation. Dependencies flow naturally.
- **Output-byte regressions, not just exit-code success**: Task 1 mandates byte comparison against request-scoped expected output and divergence from bundled-only output, catching the exact silent-fallback bug.
- **Sacred file discipline preserved**: The plan explicitly says not to edit formatters, writers, or `tests/fixtures/expected/**`.
- **No App/Avalonia dependency leak into Core**: The composer lives in `BS2BG.Core/Generation`, and the plan forbids introducing `IUserProfileStore` or App services into `BS2BG.Cli`.
- **Threat model is appropriately small**: It focuses on catalog tampering and output integrity, with existing `TemplateProfileCatalog` validation handling ambiguity.
- **Frontmatter must-haves are concrete and falsifiable**: The key links and patterns are suitable for mechanical audit.

### Concerns

- **MEDIUM - Bundle decomposition risk in Task 2**: `PortableProjectBundleService` currently uses `ResolveBundleProfileSet` for building the request catalog, generating `profiles/<name>.json` zip entries, and `FindMissingReferencedCustomProfiles`. The plan acknowledges this and proposes `ResolveReferencedCustomProfiles`, but should explicitly preserve the existing `SourceKind != Bundled` filter and deterministic `OrderBy(name, OrdinalIgnoreCase)` behavior.
- **MEDIUM - `ProjectSaveContext` plumbing scope is ambiguous**: Task 3 says not to add CLI local-profile-store lookup, but Task 2 asks the composer to accept `ProjectSaveContext?`. The plan implicitly chooses embedded-only for CLI, which is correct for AUTO-01, but the success criteria should pin this down.
- **MEDIUM - Test helper sharing is under-specified**: `CliGenerationTests` and `PortableBundleServiceTests` may need similar helper factories. The plan says to create helpers similar to bundle tests, but does not say whether to copy, extract to shared fixtures, or make helpers reusable.
- **LOW - Constructor signature risk**: If the composer is injected into `HeadlessGenerationService`, every call site needs updating. The plan should explicitly choose constructor-internal composition to avoid growing the existing constructor.
- **LOW - Bundled-name embedded profiles are already filtered upstream**: `ProjectFileService.LoadEmbeddedProfiles` filters embedded bundled-name collisions and emits diagnostics, while `Load` discards diagnostics. The composer should defensively filter without adding diagnostics or failing for this case.
- **LOW - Verification command coverage**: Targeted tests and build are listed, but a full `dotnet test` gate before summary creation would better catch indirect catalog regressions.
- **LOW - Missing direct assertion for bundled-name custom definitions**: The plan mentions preserving the filter, but should add a test that catches accidental removal.

### Suggestions

- Add a frontmatter must-have stating: Phase 6 does not add a CLI option, environment variable, or profile-store dependency for external local custom profiles; only project-embedded custom profile definitions participate in standalone CLI generate.
- Specify the composer's bundle-decomposition contract: `AddProfileEntries`, missing-profile checks, and catalog construction should all consume the same ordered, deduplicated resolved profile set.
- Choose a helper-sharing strategy for test profile factories, preferably a small shared `TestProfiles` helper in `tests/BS2BG.Tests/`.
- Pick the composer-injection approach explicitly. Recommended: store a private readonly `RequestScopedProfileCatalogComposer` initialized from the existing `profileCatalog` constructor parameter.
- Strengthen the phase verification gate with a full `dotnet test` before summary creation.
- Add focused composer unit tests covering bundled-only behavior, embedded-over-save-context precedence, save-context inclusion when no embedded definition exists, bundled-name filtering, and unreferenced profile exclusion.
- Document the `ProjectModel.CustomProfiles` precedence rule in XML docs: when both project and save context define a same-name profile, the project-embedded definition wins.

### Risk Assessment

Overall risk: **LOW**.

The implementation scope is genuinely small: one new Core class, small `HeadlessGenerationService` wiring, and a bundle refactor that delegates existing logic. No schema changes, new dependencies, or CLI surface are required. The medium concerns are implementation precision issues around bundle decomposition fidelity, scope locking, and test helper sharing rather than fundamental design risks.

---

## Codex Review

### Summary

The plan is strong and well-scoped. It targets the actual Phase 6 gap: `HeadlessGenerationService` uses a bundled-only catalog after project load, while portable bundles already compose a request-scoped catalog. The proposed shared Core composer, red-green tests, and byte-level output assertions are the right shape. Main risks are in the exact composer semantics: ordering, duplicate handling, null/default profile names, and avoiding an over-broad interpretation of custom profiles as local profile-store lookup.

### Strengths

- Uses TDD properly: tests first, and tests assert output divergence from bundled fallback rather than just success.
- Keeps the fix in `BS2BG.Core`, preserving CLI/App separation and avoiding Avalonia dependencies.
- Reuses existing generation/export writers, which protects Java parity and avoids touching sacred formatter/export files.
- Correctly identifies that validation and generation must use the same request-scoped catalog instance.
- Avoids scope creep into CLI local profile-store discovery, diagnostics expansion, or new CLI options.
- Good alignment with portable bundle behavior, reducing future drift between automation paths.

### Concerns

- **MEDIUM - Deterministic ordering**: The composer algorithm needs a precise deterministic ordering rule. Project custom profiles first, then save context can still become unstable if implementation enumerates dictionaries or profile collections instead of iterating referenced profile names in first-seen preset order.
- **MEDIUM - Duplicate handling**: The plan says project-embedded wins over save-context profiles, but implementation guidance could accidentally append both and let `TemplateProfileCatalog` throw.
- **MEDIUM - Null, empty, or bundled/default profile names**: `TemplateProfileCatalog.GetProfile(null)` is meaningful fallback behavior, but the composer should not treat null or empty names as referenced custom identities.
- **LOW - Public surface area**: Adding `ResolveReferencedCustomProfiles` as public-ish shared behavior could expose more surface than needed. If only bundle internals need it, keep the method narrowly scoped and documented.
- **LOW - Unit-test focus**: The plan's bundle assertions are useful, but a small direct composer unit test may be clearer and faster than relying only on service-level bundle tests.
- **LOW - Full validation coverage**: The verification lists `dotnet build BS2BG.sln` but not full `dotnet test`. Targeted tests are likely enough for execution, but phase closure should run the full test suite or explicitly defer it.

### Suggestions

- Define composer resolution as: collect referenced non-null/non-empty profile names from presets in first-seen order, skip bundled names case-insensitively, resolve each name from `project.CustomProfiles` first, then `saveContext.AvailableCustomProfilesByName`, and include at most one definition per name.
- Add focused composer tests for embedded beats save-context duplicate, bundled-name custom profile is ignored, unreferenced custom profile is ignored, null/default profile names do not add entries, and output order is deterministic.
- Keep `HeadlessGenerationService` wiring minimal: build the catalog once immediately after load and pass that same variable to validation, templates, and BoS.
- Make the shared composer avoid mutating or cloning project data unless mutation risk exists; if it reuses `SliderProfile`, document catalog composition as read-only.
- Clarify whether missing external local profiles means silent fallback remains unchanged or whether validation should surface unresolved custom-profile references. The current plan implies unchanged fallback, which seems appropriate for Phase 6, but it should be explicit.
- Consider making `RequestScopedProfileCatalogComposer` take `IEnumerable<ProfileCatalogEntry>` or a base `TemplateProfileCatalog` plus a small internal resolver, but avoid exposing bundle-specific save-context concerns more broadly than necessary.

### Risk Assessment

Overall risk: **MEDIUM-low**.

The implementation scope is small and aligned with existing architecture, but profile catalog composition is a correctness-sensitive path. A subtle ordering or duplicate-resolution bug could produce nondeterministic bundle contents or preserve silent fallback in one path. The proposed byte-level CLI tests reduce that risk substantially; direct composer edge-case tests would make the plan stronger and cheaper to maintain.

---

## Consensus Summary

All three reviewers agree that `06-01-PLAN.md` is the right shape for the Phase 6 gap: it is focused, Core-only, test-driven, and preserves Java parity by changing catalog selection rather than output writers or slider math. The shared concern is not the architecture, but the precision of the new catalog-composition contract.

### Agreed Strengths

- The plan correctly reuses the existing portable-bundle request-scoped catalog pattern instead of duplicating or inventing new CLI generation logic.
- Byte-level regression tests that assert divergence from bundled fallback are the strongest validation for the silent custom-profile fallback bug.
- Keeping the implementation in Core and reusing existing generation/export services protects App/CLI separation and the byte-identical output contract.
- The plan correctly requires one request-scoped catalog instance for validation, template generation, and BoS output.

### Agreed Concerns

- The composer contract needs explicit edge-case rules for duplicate names, bundled-name filtering, null or empty profile names, unreferenced custom profiles, and project-vs-save-context precedence.
- Bundle behavior can drift if `ResolveBundleProfileSet` is extracted incompletely; catalog construction, `profiles/` bundle entries, missing-profile checks, filtering, and ordering should share one resolved profile set.
- CLI scope should be locked to bundled plus project-embedded custom profiles for Phase 6. Do not introduce local profile-store lookup, a `--profiles-dir` option, environment variables, or App dependencies.
- Direct composer tests would make failures cheaper to diagnose than relying only on CLI and bundle integration tests.
- A full test-suite phase gate should be added or explicitly documented as deferred after targeted tests and build.

### Divergent Views

- Risk level differed slightly: Gemini and the agent rated the plan LOW risk, while Codex rated it MEDIUM-low because catalog composition is correctness-sensitive.
- Gemini mentioned overwrite preflight catalog usage as a possible clarification; the other reviewers focused more on profile resolution semantics and bundle extraction.
- The reviewers varied on how strongly to extract shared test helpers, but all agreed duplicated test setup could become a maintenance risk if not handled deliberately.
