# Requirements: BS2BG (Bodyslide to Bodygen)

**Defined:** 2026-04-26
**Core Value:** Modders can reliably convert existing BodySlide presets into BodyGen and BoS outputs that match the Java tool's behavior byte-for-byte where compatibility matters.

## v1 Requirements

Requirements for the next GSD roadmap. Each maps to exactly one roadmap phase.

### Profile Correctness

- [x] **PROF-01**: User can select a distinct Fallout 4 CBBE profile that does not reuse Skyrim CBBE or Skyrim UUNP defaults, multipliers, or inverted-slider behavior.
- [x] **PROF-02**: User can load existing `.jbs2bg` projects with legacy `isUUNP` values and preserve compatible profile semantics on save.
- [x] **PROF-03**: User can see neutral unresolved-profile fallback information when a preset/project references an unbundled profile name, without profile inference, slider-name mismatch warnings, or generation/export blocking for custom body mods per Phase 1 decisions D-05 through D-12 and D-16.
- [x] **PROF-04**: User can generate templates, morphs, and BoS JSON with profile-specific behavior covered by tests for each bundled profile.
- [x] **PROF-05**: User can understand the Fallout 4 CBBE profile seed/calibration status through release-facing documentation while the main workflow does not label Fallout 4 CBBE as experimental per D-06 and D-08.

Accepted Phase 1 override: ROADMAP/requirement wording that previously mentioned warnings, inferred/mismatched states, or experimental in-app status is intentionally narrowed by CONTEXT.md D-05 through D-08; future phases may revisit diagnostics through new specs, but Phase 1 must not add the deferred mismatch/inference work.

### Workflow Reliability

- [ ] **WORK-01**: User can restart BS2BG and keep last-used folders and generation-affecting workflow preferences such as omit-redundant sliders.
- [ ] **WORK-02**: User can filter NPC rows by mod, name, editor ID, form ID, race, assignment state, and preset-related values without losing stable NPC identity.
- [ ] **WORK-03**: User can run bulk NPC operations with explicit all, visible, selected, and visible-empty scopes so filtered rows are not mutated accidentally.
- [ ] **WORK-04**: User can undo and redo high-risk preset, target, NPC assignment, import, clear, and profile operations without mutable live-state corruption.
- [ ] **WORK-05**: User can work with large real-world preset and NPC datasets without UI freezes or unbounded filter/import delays.

### Validation and Diagnostics

- [x] **DIAG-01**: User can run a read-only project validation report that identifies profile, preset, target, NPC assignment, reference, and export-readiness issues.
- [x] **DIAG-02**: User can inspect profile diagnostics showing slider coverage, unknown sliders, injected defaults, multipliers, inversions, and likely profile mismatch indicators.
- [x] **DIAG-03**: User can preview NPC import results, including parsed rows, invalid lines, duplicates, charset fallback, and assignment effects before committing import changes.
- [ ] **DIAG-04**: User can preview export destinations and exact output effects before writing files when the workflow involves risk of overwriting or partial output.
- [ ] **DIAG-05**: User receives actionable save/export failure messages that identify which files were written, restored, skipped, or left untouched.

### Profile Extensibility

- [ ] **EXT-01**: User can import, copy, export, and validate local JSON profile files without corrupting bundled profiles or existing projects.
- [ ] **EXT-02**: User can edit supported profile metadata and slider tables through validated workflows that reject malformed or ambiguous profile data.
- [ ] **EXT-03**: User can save projects that reference custom profiles while preserving legacy compatibility fields for older `.jbs2bg` consumers.
- [ ] **EXT-04**: User can resolve missing custom profile references through clear diagnostics rather than silent fallback.
- [ ] **EXT-05**: User can bundle or copy project-specific profiles when sharing a project with another machine.

### Automation and Release Trust

- [ ] **AUTO-01**: User can run a headless CLI generation path that uses the same Core services and output semantics as the GUI.
- [ ] **AUTO-02**: User can create a portable project bundle containing the `.jbs2bg` file, generated outputs, profile JSON copies, and a validation report without private local paths.
- [ ] **AUTO-03**: User can apply deterministic assignment strategy presets through seams that remain testable and do not bypass existing random-provider abstractions.
- [ ] **AUTO-04**: User can verify downloaded release artifacts through checksums, signing information when available, and release-package assertions.
- [ ] **AUTO-05**: User can access setup and troubleshooting guidance for BodyGen, BodySlide, BoS, and common output-location mistakes without BS2BG editing external game plugins.

## v2 Requirements

Deferred beyond this roadmap unless the maintainer explicitly pulls them forward.

### Advanced Modding Features

- **ADV-01**: User can compare two presets through an in-app XML or generated-output diff.
- **ADV-02**: User can calibrate Fallout 4 profiles from known-good community data through an assisted workflow.
- **ADV-03**: User can use richer assignment strategy templates such as weighted groups, race filters, or repeatable seeds beyond baseline deterministic presets.
- **ADV-04**: User can access cross-platform release packages for Linux and macOS with the same confidence level as Windows builds.

### Ecosystem Integrations

- **ECO-01**: User can discover common mod-manager output folders automatically when safe and explicitly approved.
- **ECO-02**: User can export support bundles for bug reports that scrub private paths and include validation context.

## Out of Scope

Explicitly excluded to prevent scope creep.

| Feature | Reason |
|---------|--------|
| BodySlide mesh editing or `.nif` rendering | BS2BG is a conversion and assignment utility, not a BodySlide replacement. |
| Built-in xEdit, ESP, ESM, ESL, or plugin editing | Plugin editing is a separate domain with much higher data-loss risk. |
| Cloud sync, accounts, telemetry, or hosted services | The expected modder workflow is local, offline, and privacy-preserving. |
| Automatic download of profile packs or community presets | Hosted content and trust moderation are outside the core conversion value. |
| Formatter, rounding, line-ending, or JSON cleanup that breaks Java parity | Output compatibility is the core trust contract. |
| Regenerating golden expected fixtures to hide failing tests | Golden files are the Java-reference oracle unless explicitly rebaselined with rationale. |
| Installer-first distribution as the only release path | Modder tooling should remain portable and easy to drop into a modding workspace. |
| Mandatory game or mod-manager discovery before use | Manual file selection is reliable across Mod Organizer, Vortex, portable installs, and custom layouts. |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PROF-01 | Phase 1: Profile Correctness and Trust | Complete |
| PROF-02 | Phase 1: Profile Correctness and Trust | Complete |
| PROF-03 | Phase 1: Profile Correctness and Trust | Complete |
| PROF-04 | Phase 1: Profile Correctness and Trust | Complete |
| PROF-05 | Phase 1: Profile Correctness and Trust | Complete |
| WORK-01 | Phase 2: Workflow Persistence, Filtering, and Undo Hardening | Pending |
| WORK-02 | Phase 2: Workflow Persistence, Filtering, and Undo Hardening | Pending |
| WORK-03 | Phase 2: Workflow Persistence, Filtering, and Undo Hardening | Pending |
| WORK-04 | Phase 2: Workflow Persistence, Filtering, and Undo Hardening | Pending |
| WORK-05 | Phase 2: Workflow Persistence, Filtering, and Undo Hardening | Pending |
| DIAG-01 | Phase 3: Validation and Diagnostics | Complete |
| DIAG-02 | Phase 3: Validation and Diagnostics | Complete |
| DIAG-03 | Phase 3: Validation and Diagnostics | Complete |
| DIAG-04 | Phase 3: Validation and Diagnostics | Pending |
| DIAG-05 | Phase 3: Validation and Diagnostics | Pending |
| EXT-01 | Phase 4: Profile Extensibility and Controlled Customization | Pending |
| EXT-02 | Phase 4: Profile Extensibility and Controlled Customization | Pending |
| EXT-03 | Phase 4: Profile Extensibility and Controlled Customization | Pending |
| EXT-04 | Phase 4: Profile Extensibility and Controlled Customization | Pending |
| EXT-05 | Phase 4: Profile Extensibility and Controlled Customization | Pending |
| AUTO-01 | Phase 5: Automation, Sharing, and Release Trust | Pending |
| AUTO-02 | Phase 5: Automation, Sharing, and Release Trust | Pending |
| AUTO-03 | Phase 5: Automation, Sharing, and Release Trust | Pending |
| AUTO-04 | Phase 5: Automation, Sharing, and Release Trust | Pending |
| AUTO-05 | Phase 5: Automation, Sharing, and Release Trust | Pending |

**Coverage:**
- v1 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0

---
*Requirements defined: 2026-04-26*
*Last updated: 2026-04-26 after roadmap creation*
