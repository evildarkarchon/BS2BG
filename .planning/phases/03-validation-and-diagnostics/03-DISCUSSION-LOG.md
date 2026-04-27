# Phase 3: Validation and Diagnostics - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 03-validation-and-diagnostics
**Areas discussed:** Health report shape, Profile diagnostics tone, NPC import preview, Export risk preview

---

## Health Report Shape

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| Where should the project health report live in the app? | Diagnostics panel | A read-only panel/tab in the existing shell; keeps findings visible while users inspect Templates and Morphs. | yes |
| Where should the project health report live in the app? | Modal report | A focused dialog launched on demand; simpler but less useful while navigating affected data. | |
| Where should the project health report live in the app? | Text report only | Generate copyable text without much UI; fastest to implement but weaker for interactive inspection. | |
| Where should the project health report live in the app? | You decide | Planner can choose the presentation that best fits the existing Avalonia shell and bindings. | |
| How should health report severity be modeled? | Blocker/Caution/Info | Clear risk language without implying every issue is an error; fits export readiness and profile diagnostics. | yes |
| How should health report severity be modeled? | Error/Warning/Info | Conventional terminology, but may conflict with Phase 1's decision to avoid noisy profile warnings. | |
| How should health report severity be modeled? | Action/Review/Note | User-task oriented labels; softer tone but less explicit about export readiness risk. | |
| How should findings be organized inside the report? | By workflow area | Group as Project, Profiles, Templates, Morphs/NPCs, Import, Export; each item still carries severity. | yes |
| How should findings be organized inside the report? | By severity first | All blockers together, then cautions and info; good for triage but loses workflow context. | |
| How should findings be organized inside the report? | Single checklist | Flat list of checks and pass/fail state; compact but harder to connect to app areas. | |
| What actions should health findings support? | Navigate and copy | Read-only findings can select/open the affected preset, target, NPC, or output area and copy a report; no auto-fixes. | yes |
| What actions should health findings support? | Report only | Findings explain problems but do not navigate; simplest and safest. | |
| What actions should health findings support? | Include fixes | Offer fix buttons for some issues; more convenient but expands scope and risks unintended mutations. | |

**User's choice:** Diagnostics panel; Blocker/Caution/Info severity; workflow-area grouping; read-only navigate/copy actions.
**Notes:** Auto-fix actions are out for this phase.

---

## Profile Diagnostics Tone

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| Where should profile diagnostics appear? | Diagnostics only | Show profile checks in the explicit Diagnostics panel/report; do not add ambient warning banners to normal template generation. | yes |
| Where should profile diagnostics appear? | Inline and diagnostics | Show a summary in Templates plus full details in Diagnostics; more visible but risks warning noise. | |
| Where should profile diagnostics appear? | Export gate | Surface profile diagnostics before export and require acknowledgement for risky cases; safer but more intrusive. | |
| How should likely profile mismatch indicators behave? | Conservative opt-in | Only in Diagnostics, labeled as possible mismatch indicators; never infer, auto-switch, warn globally, or block generation/export. | |
| How should likely profile mismatch indicators behave? | No mismatch checks | Avoid this entirely to prevent false positives for custom body mods, even though DIAG-02 calls it out. | yes |
| How should likely profile mismatch indicators behave? | Active warnings | Show warnings when slider names look mismatched; more proactive but conflicts with Phase 1 unless tightly scoped. | |
| What profile diagnostic detail should users see? | Summary plus drilldown | Show coverage counts, unknown slider counts, injected defaults, multipliers, inversions, and fallback state with expandable slider-level details. | yes |
| What profile diagnostic detail should users see? | Full table only | List every slider and applied rule immediately; comprehensive but likely noisy for large presets. | |
| What profile diagnostic detail should users see? | Summary only | Show high-level counts without per-slider drilldown; simple but less useful for troubleshooting. | |
| How should unbundled saved profiles be represented in diagnostics? | Neutral fallback detail | Show the saved profile name, calculation fallback profile, and affected presets as diagnostic context, not as an error or warning. | yes |
| How should unbundled saved profiles be represented in diagnostics? | Caution item | Mark unbundled profile fallback as caution because output may use fallback math; clearer risk but more warning-like. | |
| How should unbundled saved profiles be represented in diagnostics? | Ignore if generating | Do not report unbundled profiles if generation can proceed; avoids noise but hides important fallback behavior. | |
| Should profile diagnostics run for one selected preset or the whole project by default? | Whole project default | Diagnostics panel summarizes all presets, with filters/drilldown to selected preset details; fits project health reporting. | yes |
| Should profile diagnostics run for one selected preset or the whole project by default? | Selected preset only | Keeps diagnostics focused and fast; less useful for export readiness across many presets. | |
| Should profile diagnostics run for one selected preset or the whole project by default? | Two separate modes | Offer project-wide and selected-preset diagnostics; flexible but adds UI/API surface area. | |

**User's choice:** Diagnostics only; no mismatch checks; summary plus drilldown; neutral fallback details; whole-project default.
**Notes:** No mismatch checks is a locked narrowing of DIAG-02.

---

## NPC Import Preview

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| Should NPC text import become preview-first before mutating the project/import database? | Preview before commit | Parsing creates a temporary result; users review summary/details, then commit or cancel with no project/database mutation until commit. | |
| Should NPC text import become preview-first before mutating the project/import database? | Optional preview | Keep current direct import by default, with a separate preview command for cautious users. | yes |
| Should NPC text import become preview-first before mutating the project/import database? | Direct import plus report | Keep immediate import, but show richer post-import diagnostics; simplest but not truly before committing changes. | |
| What level of detail should the NPC import preview show? | Summary plus row table | Show counts and a row-level table for parsed, invalid, duplicate, fallback-decoded, and would-be-added rows. | yes |
| What level of detail should the NPC import preview show? | Summary only | Show counts by file and issue type; faster to build but less helpful for fixing invalid lines. | |
| What level of detail should the NPC import preview show? | Raw diagnostics list | Show parser diagnostics and parsed row counts without a curated table; close to existing Core result shape. | |
| How should duplicate NPC rows be handled in preview and commit? | Skip and explain | Keep current mod+editor ID duplicate skip behavior; preview lists duplicates and whether they duplicate the file or existing database/project row. | yes |
| How should duplicate NPC rows be handled in preview and commit? | Let user choose | Preview offers skip, replace, or keep duplicate; more control but expands import semantics and undo risk. | |
| How should duplicate NPC rows be handled in preview and commit? | Commit duplicates | Allow duplicate rows and leave cleanup to filters; risky for assignment/export correctness. | |
| How should assignment effects be represented for NPC import? | Separate effects clearly | Import preview states file import only adds to the NPC database; assignment-changing commands get their own before/after counts when they commit rows to morphs. | yes |
| How should assignment effects be represented for NPC import? | Fold assignment preview in | One preview covers parsed rows plus any auto-assignment that will happen next; convenient but mixes two workflow steps. | |
| How should assignment effects be represented for NPC import? | Do not show assignments | Keep import diagnostics limited to parse/add results; simpler but under-serves DIAG-03 wording. | |
| How should fallback charset decoding be shown? | Per-file caution | Preview/status shows which files used fallback decoding and the encoding name; row data remains reviewable before commit. | yes |
| How should fallback charset decoding be shown? | Summary count only | Show only how many files used fallback decoding; compact but less actionable. | |
| How should fallback charset decoding be shown? | Block commit | Require users to re-save fallback-decoded files as UTF-8; safer for mojibake but intrusive for common legacy dumps. | |

**User's choice:** Optional preview; summary plus row table; skip and explain duplicates; separate import and assignment effects; per-file fallback decoding caution.
**Notes:** Direct import may remain available.

---

## Export Risk Preview

| Question | Option | Description | Selected |
|----------|--------|-------------|----------|
| What should the export preview show before writing files? | Paths plus effects | Show exact target paths, whether each file will be created/overwritten, and generated output summary/snippet before commit. | yes |
| What should the export preview show before writing files? | Paths only | Show destination filenames and overwrite status; simpler but less exact about generated content. | |
| What should the export preview show before writing files? | Full content preview | Show full generated `templates.ini`, `morphs.ini`, and BoS JSON file contents before export; exact but potentially large/noisy. | |
| When should export require confirmation before writing? | Only overwrite/risk | Confirm when existing files will be overwritten or a multi-file batch has partial-output risk; normal create-new exports proceed after preview/commit. | yes |
| When should export require confirmation before writing? | Every export | Always require a confirmation after preview; safest but adds friction to routine exports. | |
| When should export require confirmation before writing? | Never confirm | Preview is informational only; existing export command still writes immediately when launched. | |
| How detailed should save/export failure messages be? | Outcome ledger | Report which files were written, restored, skipped, or left untouched; include original exception and rollback/incomplete state where known. | yes |
| How detailed should save/export failure messages be? | Current plus paths | Keep one status message but include affected target paths; lower implementation cost, less recovery detail. | |
| How detailed should save/export failure messages be? | Detailed dialog only | Show detailed failure information in a dialog, with short status text; useful but adds UI surface. | |
| Should export diagnostics change the atomic writer behavior or only expose it better? | Expose current semantics | Preserve existing atomic pair/batch behavior and add preview/result diagnostics around it; do not redesign transactions in Phase 3. | yes |
| Should export diagnostics change the atomic writer behavior or only expose it better? | Redesign writer results | Change writers to return detailed per-file transaction states; more complete but higher risk near sacred export code. | |
| Should export diagnostics change the atomic writer behavior or only expose it better? | External dry-run service | Build a separate dry-run planner that predicts paths/effects without touching writer code; safer but may duplicate logic. | |
| Should project save use the same preview/outcome diagnostics as export? | Failure outcome only | Do not add save-preview friction; improve save failure details to identify target, written/restored/skipped/untouched state where known. | yes |
| Should project save use the same preview/outcome diagnostics as export? | Preview Save As | Show save destination and overwrite risk before Save As writes; safer but extra friction for a familiar file workflow. | |
| Should project save use the same preview/outcome diagnostics as export? | Same as exports | Apply preview and confirmation uniformly to save and export; consistent but likely heavy-handed. | |

**User's choice:** Paths plus effects; confirmation only for overwrite/risk; outcome ledger; expose current atomic semantics; save gets failure outcome diagnostics only.
**Notes:** Do not redesign export transactions or add routine save-preview friction.

---

## the agent's Discretion

- Exact Diagnostics panel placement, styling, ViewModel/service names, and report DTO shape.
- Exact export preview snippet format and navigation/copy affordances.

## Deferred Ideas

None.
