# Phase 5: Automation, Sharing, and Release Trust - Context

**Gathered:** 2026-04-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 5 delivers trusted automation and sharing around the already-proven BS2BG generation paths: headless generation, portable project bundles, assignment strategy presets, release artifact verification, and packaged setup/troubleshooting guidance.

The phase still preserves the core project constraints: local/offline only, no cloud services, no telemetry, no external game plugin editing, no mandatory game-folder discovery, no output-format changes, and no alternate generation pipeline that could drift from byte-sensitive GUI/Core behavior.

Scope override: the user explicitly expanded Phase 5 to include the richer assignment strategy menu described by `ADV-03`. Downstream agents should treat weighted groups, race filters, repeatable seeds, and group/bucket rules as Phase 5 scope, not as deferred future work.

</domain>

<decisions>
## Implementation Decisions

### Headless CLI Contract
- **D-01:** Add a dedicated CLI project/executable, not a headless mode flag inside `BS2BG.App.exe`.
- **D-02:** CLI generation must reuse the same Core project loading, profile catalog, generation, validation, and export services used by the GUI. It must not introduce a second formatter or output writer path.
- **D-03:** CLI output selection is explicit. Commands require output intent such as BodyGen INIs, BoS JSON, or all outputs rather than generating every artifact by default.
- **D-04:** CLI validation runs before writing. `Blocker` diagnostics fail the command with a nonzero exit code; `Caution` and `Info` diagnostics are reported without blocking.
- **D-05:** Existing target files are not overwritten unless an explicit overwrite flag is supplied. This applies to BodyGen INIs and BoS JSON outputs.

### Portable Bundle Shape
- **D-06:** The portable project bundle is a single shareable zip artifact, not only an unpacked folder.
- **D-07:** The zip uses a structured internal layout with predictable folders such as `project/`, `outputs/bodygen/`, `outputs/bos/`, `profiles/`, and `reports/`.
- **D-08:** Bundle profile copies include only referenced non-bundled custom profiles. Do not include unrelated local custom profiles or duplicate bundled profile data.
- **D-09:** Bundle manifests and reports must not include absolute local paths, drive roots, user names, original import directories, or original export directories. Use relative bundle paths and source filenames only.
- **D-10:** Bundle generation should include the `.jbs2bg` project, generated outputs requested by the workflow, referenced custom profile JSON copies, and a validation/report artifact suitable for support review.

### Assignment Strategy Scope
- **D-11:** Deterministic assignment means seed replay: the same project, eligible rows, preset set/order, strategy configuration, and seed produce the same assignments.
- **D-12:** Phase 5 includes the full assignment strategy menu: seeded random, round-robin, weights, race filters, and group/bucket rules.
- **D-13:** Assignment strategy configuration is saved in the `.jbs2bg` project so GUI, CLI, bundle generation, undo/redo, and collaborator machines can reproduce behavior.
- **D-14:** Race-aware rules match against the imported `Npc.Race` field case-insensitively. Do not add game-data lookup, plugin parsing, or implicit race resolution.
- **D-15:** If strategy rules leave an NPC with no eligible presets, assignment is blocked for that NPC and a diagnostic/report finding identifies the rule gap. Do not silently fall back to all presets.
- **D-16:** Strategy implementation must preserve the existing random-provider abstraction rather than bypassing it. Extend or wrap the provider seam as needed so deterministic behavior remains unit-testable.

### Release Trust And Support Docs
- **D-17:** Release automation supports both signed and unsigned paths. Use signing when configured/available; unsigned release artifacts remain valid when checksum sidecars and unsigned-warning verification docs are present.
- **D-18:** BodyGen, BodySlide, BoS, and common output-location troubleshooting guidance lives in packaged docs only for Phase 5. Do not add an in-app wizard or new Help-menu UI solely for this guidance.
- **D-19:** Package assertions verify manifest contents and smoke-level release trust: required files, checksums, profile assets, docs, absence of absolute paths in generated manifests/reports, and clean extraction launch when available.
- **D-20:** Release docs must keep the existing no-plugin-editing boundary clear: BS2BG helps users generate files and place outputs correctly, but does not edit external game plugins.

### the agent's Discretion
- Exact CLI command and flag names are flexible if they preserve explicit output selection, validation-first behavior, overwrite safety, and script-friendly nonzero failures.
- Exact zip manifest schema and report formatting are flexible if they are deterministic, path-scrubbed, and easy to test.
- Exact assignment strategy UI layout is flexible, but it must be accessible, undoable, persisted in the project, and compatible with CLI/bundle reproduction.
- Exact signing configuration mechanism is flexible as long as unsigned checksum verification remains supported.

</decisions>

<specifics>
## Specific Ideas

- The user explicitly rejected keeping the richer assignment menu deferred and requested expanding Phase 5 to include the full strategy menu.
- CLI should be a small automation executable around proven services, not a second implementation of generation math or export formatting.
- Bundle sharing should be safe to send to another person: no absolute paths and no unrelated local custom profile leakage.
- Release trust should improve the current portable release process without making a signing certificate mandatory.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope And Requirements
- `.planning/PROJECT.md` — project value, constraints, current completed milestones, and local/offline trust posture.
- `.planning/REQUIREMENTS.md` — `AUTO-01` through `AUTO-05` and `ADV-03`; note this context pulls `ADV-03` into Phase 5 scope by explicit user decision.
- `.planning/ROADMAP.md` — Phase 5 goal, dependencies, and success criteria.
- `.planning/STATE.md` — current phase state, continuity notes, and known open research needs for signing and CLI composition.

### Prior Phase Decisions
- `.planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-CONTEXT.md` — persistence, undo, filtering scope, and workflow safety conventions that assignment UI and project-saved strategies must preserve.
- `.planning/phases/03-validation-and-diagnostics/03-CONTEXT.md` — diagnostics/reporting conventions that CLI validation, bundle reports, and no-eligible-assignment findings should reuse.
- `.planning/phases/04-profile-extensibility-and-controlled-customization/04-CONTEXT.md` — custom profile trust domains, referenced-only project profile sharing, and profile export boundaries used by portable bundles.

### Current Specs And Product Constraints
- `PRD.md` — byte-sensitive output semantics, Core portability, CLI idea, portable release expectations, and no game-folder assumptions.
- `openspec/specs/template-generation-flow/spec.md` — import, preview, generation, runtime profile catalog, and unresolved-profile fallback behavior.
- `openspec/specs/morph-assignment-flow/spec.md` — existing custom target/NPC assignment flows and visible-row scope semantics.
- `openspec/specs/profile-extensibility/spec.md` — bundled/local/embedded profile trust domains, project embedding, recovery, and standalone profile export rules.
- `openspec/specs/release-polish/spec.md` — Windows portable package, signing-or-unsigned-docs requirement, release notes, and QA gates.

### Codebase Maps
- `.planning/codebase/STACK.md` — .NET/Avalonia/test stack constraints and Core portability expectations.
- `.planning/codebase/ARCHITECTURE.md` — project/service boundaries for Core/App/Test layers.
- `.planning/codebase/INTEGRATIONS.md` — file-system, profile, release, and external integration notes.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/BS2BG.Core/Serialization/ProjectFileService.cs` loads/saves `.jbs2bg` projects, embeds referenced custom profiles through `ProjectSaveContext`, and writes atomically through `AtomicFileWriter`.
- `src/BS2BG.Core/Generation/TemplateGenerationService.cs` and `src/BS2BG.Core/Generation/MorphGenerationService.cs` are the generation services the CLI and bundle paths must call.
- `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs` and `src/BS2BG.Core/Export/BosJsonExportWriter.cs` already implement the byte-sensitive BodyGen/BoS write paths and atomic batch behavior.
- `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs` and `src/BS2BG.Core/Diagnostics/ProjectValidationReport.cs` provide the validation/report model that CLI and bundle generation should extend rather than duplicate.
- `src/BS2BG.App/Services/DiagnosticsReportFormatter.cs` formats diagnostic findings for support review and can inform bundle report formatting.
- `src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs`, `RandomAssignmentProvider.cs`, and `MorphAssignmentService.cs` are the existing assignment seam and must remain the testable integration point for deterministic strategies.
- `tools/release/package-release.ps1` already creates a portable Windows zip, computes package file checksums, and writes a zip SHA-256 sidecar.
- `docs/release/README.md`, `UNSIGNED-BUILD.md`, and `QA-CHECKLIST.md` are the existing release-doc surfaces to extend for trust and troubleshooting.

### Established Patterns
- Core remains UI-free and portable; new CLI behavior should depend on Core services and minimal composition rather than Avalonia App services.
- Project JSON maintains legacy root fields and appends optional new sections only when needed; strategy persistence should follow the Phase 4 compatibility posture.
- Profile identity uses internal profile `Name`, not filenames or paths; bundles must preserve that identity and avoid unrelated profile leakage.
- Output semantics are byte-load-bearing: BodyGen INI line endings and BoS JSON formatting must stay on existing writer/formatter paths.
- App/ViewModel work uses ReactiveUI patterns, compiled AXAML bindings, accessible automation names, and undo/dirty tracking for user-visible mutations.

### Integration Points
- Add a new CLI project to `BS2BG.sln` that composes Core services and exits with script-friendly status codes.
- Add strategy model/persistence fields to Core project serialization with backward-compatible loading for older projects.
- Extend assignment services through the existing random-provider seam to support seeded random, round-robin, weights, race filters, and group/bucket rules.
- Add bundle creation service that composes project save, generation, profile export, validation/report formatting, checksum/manifest generation, and zip packaging.
- Extend release packaging/tests/docs around signing metadata, unsigned verification, package assertions, and setup/troubleshooting docs.

</code_context>

<deferred>
## Deferred Ideas

- In-app setup wizard for BodyGen/BodySlide/BoS remains out of scope for Phase 5.
- Game-data/plugin lookup for race resolution remains out of scope; race filters use imported NPC text only.
- Cloud sharing, accounts, telemetry, and automatic mod-manager/game-folder discovery remain out of scope.
- Cross-platform release parity remains out of scope unless a later roadmap explicitly expands the Windows-first release target.

</deferred>

---

*Phase: 05-automation-sharing-and-release-trust*
*Context gathered: 2026-04-27*
