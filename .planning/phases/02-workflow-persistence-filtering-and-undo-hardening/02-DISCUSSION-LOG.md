# Phase 2: Workflow Persistence, Filtering, and Undo Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 02-workflow-persistence-filtering-and-undo-hardening
**Areas discussed:** Preference persistence, NPC identity/filtering, Bulk operation scopes, Undo hardening

---

## Preference Persistence

### OmitRedundantSliders persistence

| Option | Description | Selected |
|--------|-------------|----------|
| Local app setting | Restores the user's workflow across restarts without changing `.jbs2bg` schema or shared-project behavior. | Yes |
| Project setting | Stores generation-affecting behavior with the project for reproducibility, but changes project serialization semantics. | |
| Both with override | Use app default for new projects and project-specific override after save/load; more explicit but more complexity. | |
| You decide | Planner chooses the smallest approach that satisfies WORK-01 and preserves compatibility. | |

**User's choice:** Local app setting
**Notes:** Lock this as local preference state, not project file state.

### Remembered folder granularity

| Option | Description | Selected |
|--------|-------------|----------|
| Separate workflow folders | Remember project, BodySlide XML import, NPC import, BodyGen INI export, and BoS JSON export independently, matching PRD section 4.6. | Yes |
| One global folder | Simpler, but switching between XML, NPC dumps, projects, and output folders may keep sending users to the wrong place. | |
| Import/export split | Middle ground: one import folder, one export folder, one project folder. | |
| You decide | Planner chooses based on existing picker services and test cost. | |

**User's choice:** Separate workflow folders
**Notes:** Preference schema and picker integration should preserve distinct workflow locations.

### Preference failure behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Silent fallback | Preferences are convenience state; keep startup and core generation workflows unblocked, matching current service behavior. | Yes |
| Status message only | Show a non-blocking status message when preferences cannot be loaded/saved; more transparent but may create noise. | |
| Dialog warning | Make failures very visible, but preferences are low-risk and dialogs may interrupt normal modding workflows. | |
| You decide | Planner chooses a low-noise behavior consistent with existing error handling. | |

**User's choice:** Silent fallback
**Notes:** Do not interrupt the user for preference I/O failures.

### Filter state restoration

| Option | Description | Selected |
|--------|-------------|----------|
| No, session only | Persist durable folders/options only; filters are transient task context and stale filters can hide rows after restart. | Yes |
| Restore all filters | Resumes exactly where the user left off, but can make NPC rows appear missing after reopening. | |
| Restore search only | Persists free-text search but not column checklists; partial continuity with less hidden-state risk. | |
| You decide | Planner decides whether filter state belongs in local preferences after evaluating UX/test impact. | |

**User's choice:** No, session only
**Notes:** NPC filters/search should not persist across restarts.

---

## NPC Identity/Filtering

### Stable NPC identity

| Option | Description | Selected |
|--------|-------------|----------|
| Generated row ID | Preserves stable UI identity even when mod/editor/form fields change or duplicates exist; can remain internal without changing export semantics. | Yes |
| Mod + EditorID | Matches current de-dupe logic, but editor IDs may be absent or changed and duplicates can collide. | |
| Mod + FormID | Closer to plugin identity when form IDs are reliable, but imported dumps may have formatting or missing-value issues. | |
| You decide | Planner selects the identity strategy that best preserves selection and undo safety. | |

**User's choice:** Generated row ID
**Notes:** Stable identity should not depend solely on editable/imported display fields.

### Per-column filtering style

| Option | Description | Selected |
|--------|-------------|----------|
| Checklist plus search | Extend existing race checklist pattern to each column with in-popup search for long mod/race/preset lists. | Yes |
| Text filters only | Simpler and fast to implement, but weaker for assignment/race/mod facets where users expect exact values. | |
| Global search only | Avoids new UI, but does not satisfy WORK-02's per-field filtering requirement. | |
| You decide | Planner chooses a filtering pattern consistent with current Avalonia controls. | |

**User's choice:** Checklist plus search
**Notes:** Applies to mod, name, editor ID, form ID, race, assignment state, and preset-related values.

### Hidden selected rows

| Option | Description | Selected |
|--------|-------------|----------|
| Keep hidden selection | Stable row IDs preserve selection across filtering; bulk commands must still show explicit selected-vs-visible scope to avoid surprises. | Yes |
| Clear hidden selection | Reduces accidental selected-hidden operations, but frustrates users refining filters after selecting a set. | |
| Prompt each time | Most explicit, but likely noisy during normal filter edits. | |
| You decide | Planner balances selection continuity against hidden-row safety. | |

**User's choice:** Keep hidden selection
**Notes:** Hidden selection is allowed; explicit scopes carry the safety burden.

### Large-dataset filter responsiveness

| Option | Description | Selected |
|--------|-------------|----------|
| Debounced text filtering | Checklist changes apply immediately; free-text search waits briefly to avoid rebuilding visible rows on every keystroke. | Yes |
| Immediate everything | Simpler and feels direct on small files, but risks UI churn on large xEdit dumps. | |
| Manual apply button | Best for very large datasets, but slows normal filtering and adds UI friction. | |
| You decide | Planner selects based on performance tests and Avalonia filtering patterns. | |

**User's choice:** Debounced text filtering
**Notes:** Responsiveness for large NPC datasets is part of the acceptance criteria.

---

## Bulk Operation Scopes

### Scope exposure

| Option | Description | Selected |
|--------|-------------|----------|
| Scope selector | A clear All / Visible / Selected / Visible-empty scope control keeps commands compact while making hidden-row effects explicit. | Yes |
| Separate buttons | Very explicit, but the Morphs tab can become crowded as each operation gets several variants. | |
| Confirmation choices | Ask scope after command click; reduces persistent UI but interrupts frequent workflows. | |
| You decide | Planner chooses the least confusing UI that satisfies explicit-scope requirements. | |

**User's choice:** Scope selector
**Notes:** Scope should be visible before invoking bulk commands.

### Default scope when filters are active

| Option | Description | Selected |
|--------|-------------|----------|
| Visible rows | Matches the user's filtered view and minimizes accidental changes to hidden rows. | Yes |
| Selected rows | Useful when users intentionally select across filters, but does nothing unless selection is already prepared. | |
| All rows | Fast for whole-project operations, but highest hidden-row mutation risk. | |
| You decide | Planner chooses defaults per operation, favoring safety. | |

**User's choice:** Visible rows
**Notes:** Filtered views should naturally affect visible-scope operations.

### Random-fill empty scope

| Option | Description | Selected |
|--------|-------------|----------|
| Visible empty | Only fills currently visible NPCs without assignments; hidden rows and already-assigned rows stay untouched. | Yes |
| All empty | Good for one-shot full project fill, but can unexpectedly affect hidden filtered NPCs. | |
| Selected empty | Precise for hand-picked rows, but requires selection setup before filling. | |
| You decide | Planner can make visible-empty primary while still exposing other scopes where useful. | |

**User's choice:** Visible empty
**Notes:** Primary random fill path should avoid hidden-row mutation.

### Confirmations

| Option | Description | Selected |
|--------|-------------|----------|
| Destructive all-scope only | Confirm clear/remove operations when scope is All; avoid nagging for visible/selected routine edits. | Yes |
| Every bulk change | Maximum safety, but frequent assignment workflows become click-heavy. | |
| No confirmations | Fastest, relying on explicit scope and undo/redo for recovery. | |
| You decide | Planner picks confirmation rules after mapping exact operations. | |

**User's choice:** Destructive all-scope only
**Notes:** Do not add confirmation prompts to every bulk edit.

---

## Undo Hardening

### Undo strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Targeted value snapshots | Replace risky live-reference captures for high-risk operations while preserving the current lightweight undo service shape. | Yes |
| Full project snapshots | Simpler correctness model, but can retain large object graphs and conflict with large-dataset goals. | |
| Serialized snapshots | Strong isolation through project serialization, but slower and risks tying undo behavior to file-format compatibility. | |
| You decide | Planner chooses the safest approach after mapping current undo gaps. | |

**User's choice:** Targeted value snapshots
**Notes:** Harden risky operations without replacing the entire undo system.

### Operation coverage

| Option | Description | Selected |
|--------|-------------|----------|
| All high-risk listed | Cover preset, target, NPC assignment, import, clear, and profile operations from WORK-04. | Yes |
| NPC workflows first | Focus on the riskiest filtering/assignment area, leaving template/profile undo for later. | |
| Regression-only fixes | Only harden operations with known failing tests or live-reference bugs found during planning. | |
| You decide | Planner selects coverage based on current test gaps. | |

**User's choice:** All high-risk listed
**Notes:** Planner should map WORK-04 directly to test coverage.

### History bounds

| Option | Description | Selected |
|--------|-------------|----------|
| Bounded with status | Add a reasonable limit and non-blocking status when old undo entries are pruned to avoid unbounded memory growth. | Yes |
| Unbounded as today | Preserves current behavior, but long bulk-edit sessions can retain large snapshots indefinitely. | |
| User-configurable limit | Flexible, but adds preference/UI complexity beyond the core hardening goal. | |
| You decide | Planner chooses whether a bound is needed after measuring snapshot sizes. | |

**User's choice:** Bounded with status
**Notes:** Keep this non-blocking; no new preference UI is required unless the planner finds a strong reason.

### Bulk operation undo granularity

| Option | Description | Selected |
|--------|-------------|----------|
| One undo per bulk action | A visible bulk fill/clear/import should undo as one user action, even if it changed many rows. | Yes |
| One undo per row | Maximum granularity, but makes bulk recovery tedious and bloats history. | |
| Grouped with expand later | Keep one user action now while leaving room for future detailed history UI; more planning complexity. | |
| You decide | Planner maps undo granularity to existing service constraints. | |

**User's choice:** One undo per bulk action
**Notes:** User-level intent defines undo grouping.

---

## the agent's Discretion

- Exact preference JSON property names and migration details.
- Exact generated row ID storage shape.
- Exact debounce interval and filtering helper design.
- Exact undo history limit.

## Deferred Ideas

None.
