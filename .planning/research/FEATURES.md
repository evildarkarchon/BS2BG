# Feature Landscape

**Domain:** BS2BG desktop modder tooling for converting BodySlide XML presets to BodyGen INI and BoS JSON outputs for Skyrim SE / Fallout 4 users.
**Researched:** 2026-04-26
**Scope note:** This research intentionally treats M0-M7 as complete and does not re-plan core import/generation/export, Templates/Morphs workflows, NPC import/assignment, undo/redo, project round-trip serialization, or release packaging except to classify them as already-satisfied table stakes.
**Overall confidence:** HIGH for project-internal parity and remaining backlog; MEDIUM for wider modding ecosystem expectations because sources are community documentation rather than formal platform specs.

## Positioning

BS2BG's product value is not novelty in morph math; it is trustworthy conversion and assignment around fragile BodyGen file formats. Future feature scope should therefore prioritize features that reduce user error, make profile/game mismatch visible, and improve large-list management without changing generated output semantics.

The domain ecosystem still expects manual local files: BodySlide XML presets, RaceMenu/LooksMenu BodyGen `templates.ini` + `morphs.ini`, BoS JSON files, xEdit-derived NPC dumps, and mod-manager folder layouts. Features that make these local workflows safer are high-value. Features that turn BS2BG into a BodySlide, xEdit, mod manager, cloud service, or 3D body previewer are anti-features.

## Already Satisfied Table Stakes

These features are expected for parity-sensitive use and are already represented by the current app/specs. Future milestones should protect them with regression tests rather than rebuild them.

| Feature | Why Expected | Status | Complexity Already Paid | Dependency / Regression Notes |
|---------|--------------|--------|--------------------------|-------------------------------|
| BodySlide XML preset import | BodySlide presets are the source format; community docs describe presets as saved slider arrangements in XML-like files. | Satisfied | High | Preserve sparse-slider behavior, optional XML declaration support, unknown child/attribute ignoring, duplicate-name handling. |
| Java-compatible slider math and formatting | Users trust output that behaves like jBS2BG; format drift breaks downstream BodyGen/BoS files. | Satisfied | High | Sacred path: `SliderMathFormatter`, `JavaFloatFormatting`, INI/BoS writers, golden fixtures. Do not change without explicit parity tests. |
| `templates.ini` generation and preview | BodyGen requires template lines; jBS2BG community workflows center on generating/copying/exporting them. | Satisfied | Medium | Preview must follow selected profile, omit-redundant state, and SetSlider edits. |
| `morphs.ini` generation for NPCs and custom targets | BodyGen assignment is only useful when targets map to template names. | Satisfied | Medium | Maintain visible-filter scoping for Fill Empty / Clear Assignments. |
| BoS JSON export | Original jBS2BG advertises BodyTypes of Skyrim JSON conversion support. | Satisfied | Medium | Preserve minimal-json-like numeric formatting, LF-only/no-trailing-newline output, and filename sanitization. |
| `.jbs2bg` project round trip | Modders share/save project state; external XML/NPC source files need not remain available. | Satisfied | High | Keep v1 root objects, `isUUNP`, optional `Profile`, null handling, stale reference dropping, deterministic ordering. |
| Preset management | Users need rename, duplicate, remove, clear, profile selection, and SetSlider editing after import. | Satisfied | Medium | Rename/remove must update or clear assignments safely and remain undoable. |
| NPC import and assignment | Community workflows use xEdit-style NPC lists and per-NPC/per-race targets. | Satisfied | Medium | Preserve `Mod|Name|EditorID|Race|FormID`, de-dupe by `(mod, editorId)`, encoding fallback, and form/plugin case rules. |
| Custom target grammar | BodyGen uses targets like `All|Female`, `All|Female|NordRace`, or specific plugin/FormID targets. | Satisfied | Low/Medium | Validation should remain user-facing; do not over-normalize strings because target syntax is mod ecosystem data. |
| Search/filter and bulk operations | Modder sessions involve large NPC lists and repetitive assignment. | Satisfied baseline | Medium | Baseline global search and visible-row semantics exist; remaining table stake is full per-column filtering below. |
| Drag-and-drop imports | Desktop modding tools commonly work with files/folders; DnD reduces picker friction. | Satisfied | Low/Medium | Must route through the same validation/import paths as picker imports. |
| Command palette and keyboard workflows | Power users spend 30-90 minutes in bulk editing sessions; keyboard flow is practical table stakes for modern desktop tooling. | Satisfied | Medium | Keep command availability tied to busy/dirty state. |
| Undo/redo for user mutations | Bulk edits can be destructive; parity modernization promised undo/redo. | Satisfied | Medium/High | Current implementation is fragile around live references; future work should harden, not expand casually. |
| Theme selection and accessibility baseline | Desktop app should be usable across dark/light/system settings and without mouse-only flows. | Satisfied baseline | Medium | Future UI additions must preserve compiled bindings, accessible names, focus order, and contrast. |
| Portable Windows release package | Modders expect local, installer-less tools that can sit next to mod-manager workflows. | Satisfied | Medium | Signing remains a future trust improvement, not core packaging. |

## Remaining Table Stakes for Future Work

These are not optional differentiators; they close gaps that directly affect trust, parity, or routine modder workflow after M0-M7.

| Feature | Why Expected | Complexity | Dependencies | Notes |
|---------|--------------|------------|--------------|-------|
| Correct distinct Fallout 4 CBBE profile | FO4 LooksMenu BodyGen uses different slider namespaces/semantics from Skyrim CBBE. Current codebase concerns identify FO4 as still experimental / incorrectly seeded from Skyrim settings. | High | `TemplateProfileCatalogFactory`, profile JSON content, FO4 fixtures, release docs | Highest-priority remaining feature. Ship a distinct `fallout4-cbbe.json`, visible experimental badge, and tests proving FO4 does not reuse Skyrim CBBE data. |
| Profile mismatch warnings | A wrong profile can produce valid-looking but wrong output. Users need warnings when opening projects with unknown profiles or importing XMLs whose sliders mostly do not match the selected profile. | Medium | Profile catalog, XML import diagnostics, project load diagnostics, status/notification UI | Keep compatibility fallback, but surface warnings before generation/export. This is safer than silently defaulting unknown profile names. |
| User-editable/custom profile management | Modders use many body variants beyond legacy CBBE/UUNP/FO4 CBBE; hardcoded profiles cannot scale. | High | Profile schema, validation, import/export/copy profile UI, project compatibility | Prefer local JSON profile editor/validator over bundled online profile packs. Preserve legacy `isUUNP` round trip. |
| Full per-column NPC filtering | Current app exposes only Race filtering despite the broader table-filter model; large NPC dumps need filter by mod, name, editor ID, form ID, race, and assigned presets. | Medium/High | Reusable filter popup/control, `MorphsViewModel`, headless UI tests, performance benchmarks | Complete ControlsFX-style behavior incrementally: reusable component first, then checklist/search per column. |
| Persist workflow preferences | Last-used folders and `Omit Redundant Sliders` are expected quality-of-life behavior and listed as incomplete in codebase concerns. | Low/Medium | `UserPreferencesService`, file dialog services, Templates VM | Persist last project/preset/NPC/export folders and omit-redundant state. This reduces repeated file-dialog friction and unexpected generation differences after restart. |
| Large-input guardrails and performance tests | xEdit dumps and preset collections can grow large; current filtering/import paths do full scans and whole-file reads. | Medium | Benchmarks/stress fixtures, filtered collection abstraction, virtualization | Add file-size/user-warning guardrails and thousands-of-NPC tests before major filter or import changes. Do not prematurely rewrite parsers unless data shows need. |
| Safer undo/redo snapshots | Undo/redo is table stakes once shipped; live-reference snapshots risk restoring mutated state incorrectly in long sessions. | Medium | `UndoRedoService`, mutation commands, ViewModel tests | Convert risky operations to value snapshots and add interleaving tests for rename + assignment + remove + clear. |
| Export/save failure transparency | Atomic writers protect common cases, but locked files/antivirus/cross-volume failures can still confuse users. | Medium | Atomic writer tests, app status/notification UX | Add clearer messages and failure-injection coverage before expanding export destinations. |
| Release trust: code signing or first-class verification UX | Unsigned Windows binaries are acceptable for early portable releases, but publisher trust matters for broader distribution. | Medium | Release pipeline, certificate availability, docs | If signing is unavailable, improve checksum verification instructions and surface them in release package docs. |
| Setup/troubleshooting guidance in-app | BodyGen requires external setup: Build Morphs enabled, zeroed base body/outfits, RaceMenu/LooksMenu BodyGen settings, correct output folders. | Medium | Help/about docs, non-invasive checklist UI | Provide local help/checklist links only. Avoid automating external tool configuration. |

## Differentiators Worth Considering

These are valuable, but should be phased after remaining table stakes unless they directly support FO4/profile correctness.

| Feature | Value Proposition | Complexity | Dependencies | Recommendation |
|---------|-------------------|------------|--------------|----------------|
| Profile diagnostics dashboard | Shows slider coverage: imported sliders matched by profile, missing defaults injected, multipliers/inverts applied, unknown sliders, and likely game/body mismatch. | Medium/High | Profile catalog, formatter diagnostics, import diagnostics | Strong differentiator because it turns parity internals into user trust signals. Build read-only diagnostics before editable profile tooling. |
| Profile calibration assistant | Lets users load known-good BodyGen examples or BodySlide preset corpora and derive candidate defaults/multipliers/inverts for custom profiles. | High | Diagnostics, profile editor, test corpus, clear uncertainty labels | Worth considering for FO4 and non-CBBE bodies, but only with explicit “best effort” language. Do not auto-commit calibration silently. |
| Output folder advisor | Given a plugin/master name and target game, suggests the common BodyGen output path pattern and warns when users export to a suspicious folder. | Medium | Game/profile metadata, folder picker service, local path heuristics | Helpful because community docs emphasize exact folder placement. Keep advisory; do not require game install discovery. |
| Assignment strategy presets | Bulk assignment strategies such as weighted random, balanced distribution, by race/mod, or “trim to safe count.” | Medium | Morph assignment service, RNG determinism policy, undo snapshots | Useful for large projects. Preserve current random behavior as default; make advanced strategies opt-in. |
| NPC import mapping preview | Preview parsed NPC rows, duplicates, invalid lines, charset fallback, and selected rows before committing import. | Medium | Parser diagnostics, staging UI, undo integration | Reduces data mistakes. Particularly useful for non-English or custom xEdit exports. |
| Project validation report | One-click report: missing/unknown profiles, empty targets, stale assignment references, too many presets per target, illegal/suspicious target names, export readiness. | Medium | Existing validators, notification UX | High-value differentiator because it is local, deterministic, and does not alter outputs. |
| Dry-run export diff/preview | Shows generated file paths and content diffs against existing `templates.ini`/`morphs.ini`/BoS JSON before writing. | Medium/High | Export services, file reading, diff viewer, encoding/line-ending awareness | Valuable for cautious modders. Keep byte-exact diff semantics. |
| Headless CLI | `bs2bg generate --project X.jbs2bg --out ./out` enables repeatable mod builds and CI-style generation. | Medium | Core already UI-free; CLI packaging; shared export services | Good post-table-stakes feature. Avoid adding new behavior; CLI should call the same Core services as the GUI. |
| Portable project bundle | Export a zip containing `.jbs2bg`, generated outputs, profile JSON copies, and validation report. | Medium | Project serializer, export writers, profile catalog, release docs | Useful for sharing/debugging. Must avoid bundling user game paths or private files by default. |
| Mod-manager convenience docs/templates | Provide example MO2/Vortex folder structures and “where to put files” snippets per game/profile. | Low/Medium | Docs and optional in-app Help page | Low risk and high support value. Prefer docs over direct integration. |
| Localization-ready UI strings | Makes the tool approachable to non-English modding communities where xEdit/BodySlide guides exist. | Medium | Resource extraction, UI layout testing | Consider after feature surface stabilizes; parsing/output formats must remain invariant. |

## Anti-Features and Explicit Non-Goals

These features are tempting in this domain but would dilute BS2BG's core value, increase maintenance risk, or break parity expectations.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| BodySlide replacement / mesh editor / `.nif` renderer | Requires separate 3D/mesh ecosystem; PRD explicitly says BS2BG is not BodySlide. | Keep importing BodySlide XML; link users to BodySlide/Outfit Studio docs. |
| Writing BodySlide XML back out | Changes the source-of-truth direction and risks corrupting user preset files. | Keep `.jbs2bg` as the editable project state; optionally export diagnostics. |
| Built-in xEdit plugin parser/editor | ESP/ESM parsing and conflict resolution are a different tool class. | Continue accepting simple xEdit-derived text dumps; provide export-script guidance if needed. |
| Automatic game/mod-manager discovery as required setup | MO2/Vortex/game installs vary; forced discovery creates privacy, permission, and path bugs. | Use file/folder pickers, remember last-used folders, and provide advisory checks. |
| Cloud sync/accounts/telemetry/hosted profile marketplace | Local/offline modder tooling is expected and project docs make cloud/telemetry out of scope. | Keep profiles local JSON files; users can share them manually. |
| Auto-download community preset/profile packs | Licensing, provenance, and adult-content distribution risks are high in this modding ecosystem. | Support local import of user-provided profile JSON with validation. |
| Changing formatter/math for “cleaner” .NET output | Breaks byte-identical compatibility and user trust. | Add new outputs only behind explicit opt-in formats, if ever; keep BodyGen/BoS parity untouched. |
| Silent profile fallback | Produces valid-looking wrong output when profile names are missing or mismatched. | Preserve fallback for compatibility but warn clearly and include validation report entries. |
| Regenerating golden fixtures to bless behavior changes | Hides C# regressions; expected files are authoritative Java-reference outputs. | Fix code or create a documented, explicitly approved compatibility break. |
| 3D body preview from slider values | Hard, body-specific, and not necessary for BodyGen text generation. | Provide text previews, BoS JSON view, diagnostics, and optional image lookup. |
| Face preset / LooksMenu JSON face-body merger | FO4 face presets and BodyGen INI serve different workflows; community sources note this distinction can confuse users. | Keep BoS JSON and BodyGen INI exports; document what BS2BG does and does not produce. |
| Installer-first distribution | Portable modding utilities fit MO2/Vortex/manual workflows better and avoid registry/admin friction. | Keep portable zip; add signing/checksum verification. |
| Cross-platform packaging as equal priority | Users are Windows-first because Skyrim/Fallout modding toolchains are Windows-centered. | Keep Avalonia portability where cheap; ship/test Windows first. |
| Long-lived auto-backup/revision history | Project docs listed revision history as out-of-scope follow-up; undo/redo already covers session mistakes. | Consider bounded session undo hardening and explicit “Save As copy” patterns. |

## Feature Dependencies

```text
Distinct FO4 profile -> Profile mismatch warnings -> Profile diagnostics dashboard -> Profile editor/calibration assistant

User preferences -> Folder advisor -> Dry-run export preview / project bundle

Reusable NPC column filter -> Large-input benchmarks -> Assignment strategy presets

Undo/redo snapshot hardening -> Advanced bulk assignment strategies -> Project validation report mutations, if any

Parser diagnostics -> NPC import mapping preview -> Better charset and invalid-line UX

Release verification docs -> Optional code signing -> Broader Nexus/GitHub release trust

Core service stability -> Headless CLI -> Repeatable batch generation workflows
```

## MVP Recommendation for Next Feature Scope

Prioritize:

1. **Distinct FO4 profile + profile warnings** — closes the most serious correctness/trust gap and prevents valid-looking wrong output.
2. **Preference persistence + full per-column NPC filtering** — completes routine workflow expectations without touching output math.
3. **Project/profile validation report** — creates a safety net for future profile and assignment features.

Defer:

- **Profile calibration assistant** until diagnostics and distinct FO4 profile are stable.
- **Headless CLI** until GUI/profile/export services expose clean seams and table-stakes gaps are closed.
- **Dry-run diff export** until export failure handling and validation report are in place.
- **Localization** until the UI surface stops changing.

## Phase Suggestions for Requirements Definition

### Phase A: Correctness and Trust

- Distinct `fallout4-cbbe.json` and loader path.
- Unknown/missing profile warnings on project load and generation/export.
- Slider coverage diagnostics for selected preset/profile.
- Release documentation updates marking FO4 confidence honestly.

**Why first:** Profile mismatch can silently corrupt the core value proposition. This phase should get deeper research into known-good FO4 CBBE / LooksMenu BodyGen calibration data.

### Phase B: Workflow Completion

- Persist last-used folders and `Omit Redundant Sliders`.
- Complete per-column NPC filtering.
- Add large NPC/preset performance tests and guardrails.
- Harden undo/redo snapshot semantics for covered mutations.

**Why second:** These are user-facing table stakes but mostly avoid sacred formatter/export code.

### Phase C: Validation and Diagnostics

- Project validation report.
- NPC import mapping preview with duplicate/invalid/charset diagnostics.
- Export folder advisor.
- Optional dry-run export path/content preview.

**Why third:** Builds on profile and filtering foundations; helps users debug without BS2BG needing to own external game setup.

### Phase D: Automation and Sharing

- Headless CLI using the same Core services.
- Portable project bundle with validation report and profile copies.
- Optional assignment strategy presets.

**Why last:** These amplify the tool after core correctness and workflow safety are solid.

## Complexity Notes

| Area | Complexity | Risk Driver | Research Need |
|------|------------|-------------|---------------|
| FO4 profile correctness | High | Lack of authoritative defaults/inverts/multipliers; FO4 namespace differs from Skyrim. | High: collect known-good BodyGen examples and compare to FO4 BodySlide XML fixtures. |
| Profile editor | High | User-created data can break output while appearing valid. | Medium/High: define schema validation and migration rules. |
| Per-column filtering | Medium/High | Avalonia DataGrid control limitations and current monolithic AXAML/VM. | Medium: prototype reusable filter control and benchmark. |
| Preferences | Low/Medium | Straightforward persistence, but affects file dialog flow and generated output option state. | Low. |
| Validation report | Medium | Needs consistent diagnostics across import, project load, profile catalog, assignment, export. | Medium. |
| Headless CLI | Medium | Core is ready, but packaging and shared error reporting need design. | Low/Medium. |
| 3D/mesh preview | Very High | Different domain and dependencies. | Do not research unless project goals change. |

## Sources

### Project-internal sources (HIGH confidence)

- `.planning/PROJECT.md` — current status, active requirements, constraints, out-of-scope boundaries.
- `PRD.md` — parity checklist, original v2 feature scope, future open questions, known risks.
- `AGENTS.md` — current implementation status, sacred files, stack and workflow constraints.
- `openspec/specs/template-generation-flow/spec.md` — implemented template/import requirements.
- `openspec/specs/morph-assignment-flow/spec.md` — implemented morph/NPC assignment requirements.
- `openspec/specs/inspector-parity-views/spec.md` — implemented SetSlider/BoS/image/no-preset parity views.
- `openspec/specs/ux-upgrades/spec.md` — implemented UX modernization requirements.
- `openspec/specs/project-roundtrip/spec.md` — project compatibility requirements.
- `openspec/specs/export-commands-app-shell/spec.md` — export/app-shell requirements.
- `.planning/codebase/CONCERNS.md` — current missing features, fragile areas, performance limits, and test gaps.
- `docs/release/RELEASE-NOTES-v1.0.0.md` — shipped highlights and known limitations.

### Ecosystem/community sources (MEDIUM confidence)

- Nexus Mods Wiki, “Bodyslide: Guide and Tutorial” — BodySlide presets, groups, Build Morphs context: https://wiki.nexusmods.com/index.php/Bodyslide:_Guide_and_Tutorial
- LoversLab, “jBS2BG - BodySlide to BodyGen Converter/Generator” — original jBS2BG feature expectations and BodyGen/BoS workflow: https://www.loverslab.com/topic/90812-jbs2bg-bodyslide-to-bodygen-convertergenerator
- LoversLab, “[Unofficial] BodyGen - docs” — BodyGen `templates.ini` / `morphs.ini`, target syntax, zeroed/body-morph setup expectations: https://www.loverslab.com/topic/53531-unofficial-bodygen-docs
- LoversLab, “How to use different bodyslide presets for PC and NPCs (BodyGen)” — practical jBS2BG usage, custom targets, NPC list workflows, output folder layout: https://www.loverslab.com/topic/163766-how-to-use-different-bodyslide-presets-for-pc-and-npcs-bodygen/
- Nexus Mods, “LooksMenu BodyGen Presets for CBBE and BodyTalk” — FO4 LooksMenu BodyGen requirements and body/preset diversity context: https://www.nexusmods.com/fallout4/mods/27890
- LoversLab, “LooksMenu BodyGen functionality for Fallout 4” — FO4 BodyGen folder/path examples and slider namespace examples: https://www.loverslab.com/topic/77879-so-i-noticed-looks-menu-now-has-bodygen-functionality-for-fallout-4-anyone-got-it-working-care-to-share-inis/

## Confidence Assessment

| Category | Confidence | Notes |
|----------|------------|-------|
| Already satisfied table stakes | HIGH | Based on project specs, codebase status, and release notes. |
| Remaining table stakes | HIGH | Based on `.planning/codebase/CONCERNS.md`, PRD open questions, and shipped limitations. |
| Differentiators | MEDIUM | Inferred from project architecture and community workflow pain points; should be validated with maintainer/user feedback before implementation. |
| Anti-features | HIGH | Mostly explicit in PRD/project docs and reinforced by ecosystem boundaries. |
| FO4 profile recommendations | MEDIUM | Correctness gap is confirmed internally; exact calibration data remains unresolved and needs deeper phase research. |
