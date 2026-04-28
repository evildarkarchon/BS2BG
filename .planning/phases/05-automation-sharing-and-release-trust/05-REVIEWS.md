---
phase: 5
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-27T18:41:49.5762484-07:00
plans_reviewed:
  - 05-01-PLAN.md
  - 05-02-PLAN.md
  - 05-03-PLAN.md
  - 05-04-PLAN.md
  - 05-05-PLAN.md
  - 05-06-PLAN.md
  - 05-07-PLAN.md
  - 05-08-PLAN.md
  - 05-09-PLAN.md
---

# Cross-AI Plan Review - Phase 5

## Gemini Review

### Summary

Gemini assessed Phase 5 as strongly aligned with Java parity, Core portability, and byte-identical output constraints. The review especially approved the dedicated CLI over shared Core services, pinned deterministic assignment replay, privacy-first bundle design, and checksum/signing release posture.

### Strengths

- Architectural separation is strong: Core owns semantics, while CLI and App compose workflows.
- Seeded determinism through `DeterministicAssignmentRandomProvider` is a strong fit for repeatable mod-list assignment behavior.
- Bundle privacy is well represented through `BundlePathScrubber` and a manifest schema that avoids local path disclosure.
- Release trust tests use `ReleaseSmoke` gating to preserve a fast development loop while still allowing package-level verification.
- Packaged docs correctly preserve the no-plugin-editing boundary and unsigned-build trust posture.

### Concerns

- MEDIUM: `BosJsonExportPlanner` could drift from `BosJsonExportWriter` if filename sanitization or deduplication logic is duplicated instead of writer-owned or writer-consumed.
- LOW: Moving profile lookup to `AppContext.BaseDirectory` is correct for portable CLI use, but App/local-development asset resolution needs test coverage during any catalog factory migration.
- LOW: Bundle temp staging cleanup handles managed failures with `try/finally`, but abrupt process termination can still leave temp directories.

### Suggestions

- Refactor `BosJsonExportWriter` to consume `BosJsonExportPlanner`, or otherwise keep the planner mechanically tied to writer behavior.
- Make overwrite-refusal CLI messages name the exact bypass flag, such as `--overwrite`.
- Consider a future `Verify Bundle` CLI command that validates `manifest.json` and `SHA256SUMS.txt` after sharing.

### Risk Assessment

Overall risk: LOW. Gemini found the plan set disciplined, grounded in existing codebase patterns, and protected by incremental tests around established .NET APIs and project services.

## Claude Review

### Summary

Claude found the nine-plan sequence well structured and noted that earlier review feedback appears incorporated. The main residual risks are contract gaps in bundle serialization, weighted-strategy edge cases, and invalid strategy repair UX.

### Strengths

- Wave ordering is sound: foundations precede generation, strategy execution, bundle, release, and UI integration.
- Single-source semantics are enforced by staging bundle outputs through real `BodyGenIniExportWriter` and `BosJsonExportWriter` bytes.
- Privacy modeling is concrete: drive roots, UNC paths, backslashes, filenames, and usernames are addressed in path scrubbing.
- Deterministic replay is specified with a pinned PRNG, stable ordering, reference vectors, and tests that reject `new Random(seed)`.
- CLI tests avoid shelling out by invoking `Program.Main` in process under serialized console capture.
- Release trust keeps signing optional and heavyweight release tests gated.

### Concerns

- HIGH: Plan 06 depends on a possibly nonexistent `ProjectFileService` string serialization API. Add an explicit acceptance criterion or split out a small prerequisite task for `SerializeToString(ProjectModel, ProjectSaveContext?)` if needed.
- HIGH: Plan 04 does not define behavior when all matching weighted rules quantize to zero units after race filtering. Block that NPC per D-15 rather than falling back.
- HIGH: Plan 05 says invalid loaded strategies disable Apply until repaired, but it does not define how salvageable invalid rows hydrate into editable UI state.
- MEDIUM: Plan 02 should state that `--intent all` overwrite preflight checks all selected outputs before any write, and overwrite refusal means no writes occurred.
- MEDIUM: Plan 05 should add a test proving an active Morphs filter does not limit strategy application, since apply intentionally affects all `MorphedNpcs`.
- MEDIUM: Plan 06 should pin `ZipArchiveEntry.LastWriteTime` if preview and create outputs are expected to remain deterministic in tests.
- MEDIUM: Plan 08 should avoid SignTool password leakage through PowerShell command-line logging or transcripts.
- MEDIUM: Plan 07 should choose `AutomationExitCode` or reuse `HeadlessGenerationExitCode` explicitly instead of deferring the enum decision to implementation.

### Suggestions

- Add a Plan 06 task or acceptance criterion for the Core project serialization-to-string API before wiring `PortableProjectBundleService`.
- Add a weighted-strategy test for all matching rules rounding to zero units.
- Hydrate invalid loaded strategy rows as editable `AssignmentStrategyRuleRowViewModel` instances with per-row validation messages.
- Add a full-project apply test with an active filter hiding most NPC rows.
- Pin bundle zip entry timestamps to a deterministic value such as request `CreatedUtc` or Unix epoch.
- Commit to a generalized `AutomationExitCode` rename in Plan 07 if bundle/release CLI paths will share the same exit surface.
- Invoke SignTool through a safe argument-array pattern and never write password values, full command lines, or full certificate paths to logs or metadata.
- Add packaged-doc guidance that race filters match imported NPC race text exactly.

### Risk Assessment

Overall risk: MEDIUM. Claude found the architecture sound, but identified a few plan-contract ambiguities that should be resolved before execution.

## Codex Review

### Summary

Codex assessed the plan set as requirement-traceable and aligned with Core-first boundaries, privacy, and optional signing. The overall risk was MEDIUM because assignment strategy, bundle, and release surfaces are complex enough that unresolved contract details could cause implementation churn.

### Strengths

- `05-01` correctly creates a dedicated CLI, pins `System.CommandLine`, keeps App dependencies out, and checks profile asset copying.
- `05-02` strongly reuses project loading, validation, generation services, and existing writers.
- `05-03` provides a backward-compatible optional `.jbs2bg` strategy shape and preserves imported-race matching boundaries.
- `05-04` has excellent deterministic PRNG, exact-sequence tests, shared eligibility, and no-silent-fallback requirements.
- `05-05` preserves ReactiveUI conventions, undo/dirty state, and explicit full-project apply scope.
- `05-06` has a solid privacy/security posture with normalized zip entries, exact writer bytes, referenced-only profiles, temp cleanup, and blocker behavior.
- `05-07` correctly makes bundle preview first-class across CLI and GUI.
- `05-08` handles signing optionality, checksum trust, CLI packaging, secret redaction, and gated release smoke tests.
- `05-09` keeps setup guidance in packaged docs only and avoids in-app wizard creep.

### Concerns

- LOW: `--omit-redundant-sliders` has a default `false`, so the plan should clarify whether it must be explicitly supplied or merely available.
- MEDIUM: `BosJsonExportPlanner` likely touches sacred writer behavior, and `--intent all` partial-failure semantics are nuanced.
- MEDIUM: Persisted assignment strategy has no explicit schema/version field, despite being collaborator-replayed automation data.
- HIGH: `GroupsBuckets` semantics conflict between Plan 03 and Plan 04. Plan 03 does not require `RaceFilters` for group/bucket rules, while Plan 04 says bucket rules match only when `RaceFilters` contains `Npc.Race`.
- MEDIUM: Invalid loaded strategy diagnostics need a concrete flow from `ProjectLoadResult` into `MorphsViewModel`.
- MEDIUM: Bundle contracts define preview/result types but not an explicit outcome type for validation blockers, overwrite refusal, missing profiles, or I/O failures.
- MEDIUM: Plan 07 depends on Plan 06 failure statuses; align them before implementation.
- LOW: Fast default tests mostly assert release script/docs unless `ReleaseSmoke` is run somewhere.
- LOW: `Last verified: 2026-04-27` in Plan 09 will be stale if implemented on a later date unless intentionally tied to the research date.

### Suggestions

- Resolve `GroupsBuckets` empty-race-filter behavior before implementing Plans 03 and 04.
- Add a `PortableProjectBundleOutcome` or status enum so expected bundle failures are not modeled only as exceptions.
- Add `schemaVersion: 1` to persisted strategy data, or define version-tolerant loading now.
- Keep `BosJsonExportWriter` changes minimal and protected by BoS/export golden tests.
- Document the exact `ReleaseSmoke` command and make it part of the release gate.
- Use the actual verification date for packaged docs, or document why the research date is used.

### Risk Assessment

Overall risk: MEDIUM. Codex found the architecture and traceability strong but recommended resolving a few cross-plan contracts before coding.

## Consensus Summary

The reviewers agree that Phase 5 is well planned and largely ready for execution. The strongest shared signal is that the plan protects the project's core trust boundary by keeping generation, validation, export formatting, bundle output bytes, and deterministic assignment behavior in Core rather than creating CLI/App-specific logic. The actionable feedback is concentrated in a small set of plan-contract clarifications before implementation.

### Agreed Strengths

- Core-first architecture is the right boundary: CLI and GUI should orchestrate existing Core services instead of duplicating generation/export semantics.
- Deterministic assignment design is strong, especially the pinned PRNG, exact-sequence tests, stable ordering, and explicit rejection of `System.Random(seed)` for persisted replay.
- Bundle privacy and safety are treated as first-class requirements through path scrubbing, normalized relative zip entries, exact writer-byte packaging, and referenced-profile scoping.
- Release trust is pragmatic: optional signing, checksum-backed unsigned builds, secret redaction, required package assertions, and `ReleaseSmoke` gating avoid making SignTool mandatory.
- Wave ordering is generally sound and keeps high-risk work behind prerequisite contracts and tests.

### Agreed Concerns

- `BosJsonExportPlanner` must not drift from `BosJsonExportWriter`. Planner extraction should be mechanical, writer-owned or writer-consumed, and protected by existing BoS/export tests in addition to CLI tests.
- Assignment strategy edge cases need tighter contracts before execution: zero-unit weighted matches after race narrowing, `GroupsBuckets` behavior when `RaceFilters` is empty, invalid loaded strategy repair UX, and whether strategy persistence needs a schema/version field.
- Bundle service failure/result contracts need to be explicit enough for CLI/GUI mapping: validation blockers, overwrite refusal, missing profiles, I/O failures, temp cleanup, and any required `ProjectFileService` serialization-to-string API should not be discovered ad hoc during implementation.
- `--intent all` and bundle writes need clear preflight semantics: overwrite checks for all selected targets should happen before any writes when refusal is expected, and partial failure ledgers should clearly state what was written or left untouched.
- Release signing implementation should prevent secret leakage beyond normal logging: avoid printing password values or full certificate paths, and use safe process invocation patterns for SignTool.

### Divergent Views

- Overall risk differed: Gemini rated the plan set LOW risk, while Claude and Codex rated it MEDIUM due to contract ambiguities in strategy, bundle, and release surfaces.
- Zip determinism was weighted differently: Claude recommended pinning `ZipArchiveEntry.LastWriteTime`, while Gemini treated temp/staging issues as low operational risk.
- Codex uniquely emphasized adding `schemaVersion: 1` to persisted assignment strategy data.
- Claude uniquely recommended committing to a generalized `AutomationExitCode` rename rather than leaving the bundle exit-code enum decision to execution.
- Gemini uniquely suggested a future `Verify Bundle` CLI command; useful but not required for Phase 5 acceptance.
