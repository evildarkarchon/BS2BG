# Compatibility and Feature-Parity Boundary

This document resolves [issue #68](https://github.com/evildarkarchon/BS2BG/issues/68) for the Java 25 and JavaFX 25 modernization planned by [issue #62](https://github.com/evildarkarchon/BS2BG/issues/62). It classifies the observable behavior at the planning baseline, commit `00f916f`, into strict compatibility, capability parity, approved corrections, and behavior delegated to later modernization decisions.

Production modernization remains out of scope for this decision.

## Authority and compatibility policy

The accepted `ProjectSession` and immutable `Project` work on the `improve-codebase-architecture` branch is authoritative. Older Java 1.1.2 evidence remains useful where the accepted branch has not deliberately replaced it, but it does not override the current Project lifecycle, identity, recovery, ordering, or import contracts.

The modernization uses a tolerant-reader, canonical-writer policy:

- Valid legacy inputs remain readable and keep their observable meaning.
- Canonical writes may normalize representation where this document permits it, but may not silently lose, merge, or invent Project data.
- Successful generated artifacts retain their exact Windows bytes except for the explicitly approved NPC Morph Assignment ordering below.
- Failure behavior that can lose data, partially update state, write unsafe paths, report false success, or violate UI-thread ownership is corrected rather than preserved.
- The external JavaFX-independent `ProjectSession` interface, immutable snapshots, and package-private immutable `Project` aggregate remain the architectural boundaries established by ADR-0001 and ADR-0002.

Where the earlier Rust/Slint compatibility plan differs from the accepted Java branch, the Java branch wins. In particular, BodySlide batches commit independently per source, missing Slider Preset references recover with diagnostics and dirty state, repeated NPC display-name members remain representable, and the Project format remains unversioned with unknown root members rejected.

## Strict compatibility contracts

### Project files

`.jbs2bg` compatibility is semantic, not byte-for-byte. JSON indentation, whitespace, line endings, and member order are not part of the contract.

A compatible reader and writer preserve all of the following:

- The three case-sensitive root objects `SliderPresets`, `CustomMorphTargets`, and `MorphedNPCs` and their current member shapes.
- Slider Preset profile mode, enabled choices, nullable stored small and big endpoints, percentage bounds, and the distinction between an explicitly stored null endpoint and a synthesized missing default.
- Omission of an unchanged synthesized default only while it remains enabled with a 100–100 percentage window. Its effective values continue to resolve from the active settings profile.
- Case-insensitive logical identity and uniqueness for Slider Presets and Custom Morph Targets while preserving display casing.
- NPC Morph Assignment identity by case-insensitive plugin name plus editor ID. Repeated NPC display names remain readable and writable when those stable identities differ.
- Form ID normalization by keeping the last six characters and removing leading zeroes while retaining one zero.
- Canonical case-insensitive ordering of Slider Presets, Custom Morph Targets, Slider Preset choices, and assigned Slider Preset names. NPC Morph Assignments use deterministic plugin-then-editor-ID ordering; the Java 1.1.2 same-plugin list order is intentionally not preserved.
- Referential cascades when a Slider Preset is renamed, removed, or cleared.
- Recovery of missing Slider Preset references by omitting only the unresolved relationship, reporting every affected location, and publishing a dirty `RECOVERED` snapshot.
- Transactional rejection of malformed structure, unsupported members, ambiguous normalized identities, duplicate logical relationships, and other invalid values.
- Atomic open and save behavior. Failure preserves the active Project, dirty state, lifecycle status, file identity, and existing destination bytes.
- A case-insensitive `.jbs2bg` extension check that produces exactly one suffix.

No `FormatVersion` member is added by this modernization plan. Versioning may be reconsidered only by a later decision that supplies a migration and bidirectional-compatibility reason.

### BodySlide Slider Preset input

BodySlide XML import preserves these successful-input rules:

- A batch is ordered and reports one outcome per selected source.
- Each source is atomic. Valid sources commit even when another source in the same batch is rejected or fails.
- The `SliderPresets` root is accepted case-insensitively; normal BodySlide `Preset` and `SetSlider` elements retain their current meanings.
- Dots in imported Slider Preset names normalize to spaces, names are trimmed, and logical identity is case-insensitive.
- Within one source, a later duplicate Slider Preset payload or slider endpoint wins while the first logical Slider Preset's display casing survives.
- `size="small"` and `size="big"` are accepted case-insensitively. A missing `size` retains the legacy big-endpoint meaning; any other explicit value is an approved rejection with a source-specific diagnostic.
- Imported Slider Presets begin in the Standard profile, retain existing Project relationships on reimport, and synthesize configured missing defaults.

### NPC Database input and images

The NPC Database remains session-scoped source data independent of the Project. Promoting an entry creates an NPC Morph Assignment by copying immutable values; it never shares mutable state with the source catalog.

The pipe-delimited text format preserves:

- One nonblank record per line with `plugin|display name|editor ID|race|form ID`.
- Trimming of fields, tolerance of fields after the fifth, truncation of race at its first quote, and `Unnamed (<editor ID>)` for an empty display name.
- Case-insensitive deduplication by plugin plus editor ID, import-order display, and the existing Form ID normalization.
- Charset detection with UTF-8 as the deterministic fallback when detection is inconclusive.

Parsing becomes transactional per selected file. A malformed row or I/O failure leaves the NPC Database unchanged and reports the file, line, and reason instead of returning partial data or false success.

NPC image preview remains a required capability. The two legacy filename patterns, `<display name> (<editor ID>)` and `<display name>`, remain discovery fallbacks for `.jpg`, `.jpeg`, `.png`, and `.bmp`. Packaged storage location, user-directed discovery, caching, sizing, and placement may change.

### Settings and preferences

The schemas and output effects of `settings.json` and `settings_UUNP.json` remain importable or receive an explicit migration. This includes the `Defaults`, `Multipliers`, and `Inverted` data, current default values, and lookup behavior wherever changing it would change generated output.

The output-affecting **Omit Redundant Sliders** preference is migrated. Recent chooser directories, Java Preferences node names, working-directory placement, automatic file creation location, and popup geometry are conveniences rather than compatibility data. Issue #75 owns their packaged-storage and upgrade policy.

### Generated artifacts

Every generation or export command captures one immutable Project snapshot. View selection, sorting, and filtering never narrow the complete generated output.

For the Windows deliverable, exact bytes are frozen by golden fixtures for valid inputs:

- UTF-8 without a BOM.
- `templates.ini` contains one case-insensitively ordered Slider Preset per line and no final newline.
- `morphs.ini` contains Custom Morph Targets first, then NPC Morph Assignments in deterministic plugin-then-editor-ID order, and has a CRLF after every emitted line including the last.
- Empty Custom Morph Targets and NPC Morph Assignments remain emitted with an empty right-hand side and are also surfaced through structured warning data.
- Assigned Slider Preset names remain case-insensitively ordered and pipe-delimited.
- Template slider expressions preserve enabled-choice filtering, inversion, configured multipliers, percentage interpolation, two-decimal half-up rounding, and legacy Java float rendering. Equal values use `slider@value`; unequal values use `slider@min:max`.
- **Omit Redundant Sliders** affects Templates output only. BoS JSON always omits neutral redundant sliders and continues to use transformed small and big endpoints without applying the percentage window.
- Each BoS payload preserves the current pretty-printed JSON bytes and member order: `string.bodyname`, ordered `slidernameN` members, `int.slidersnumber`, then all `highvalueN` members before all `lowvalueN` members.
- Clipboard and preview output represents the same generated content as its corresponding artifact, apart from UI newline presentation where JavaFX controls normalize it.

The accepted plugin-then-editor-ID NPC ordering is the one explicit departure from Java 1.1.2's same-plugin stable list order. It removes UI-order dependence without changing BodyGen meaning.

## Capability-parity contracts

The modernized application preserves every current workflow, but it need not preserve the current control hierarchy or popup mechanics.

### Project and Slider Preset workflows

- New, Open, Save, Save As, dirty-state indication, and discard confirmation.
- Multi-file BodySlide XML import with per-source diagnostics.
- Slider Preset import, rename, duplicate, remove, clear, Standard/UUNP switching, slider-choice editing, individual preview, aggregate generation, clipboard copy, BoS preview, single BoS export, and batch BoS export.
- Immediate coherent feedback from accepted edits, while allowing the presentation layer to coalesce high-frequency slider input without changing the final committed values.

### Custom Morph Target and NPC workflows

- Create, remove, and clear Custom Morph Targets.
- Add, add all, remove, and clear Slider Preset relationships for either a Custom Morph Target or NPC Morph Assignment.
- Load, inspect, filter, sort, add one, add visible, and clear visible NPC Database entries, including optional random assignment and image preview.
- Filter, sort, remove one, remove visible, clear visible assignments, and fill visible empty NPC Morph Assignments from a selected Slider Preset subset.
- Generate, inspect, copy, and export Templates and Morphs output, including warnings for targets with no assigned Slider Preset.

Automatic assignment remains semantically random. Each eligible target is chosen independently from the supplied Slider Preset set; no stable seed, exact sequence, or distribution guarantee is part of compatibility.

### Selection, sorting, filtering, and transient state

The logical behavior is strict even though the control implementation may change:

- Column filters combine with AND.
- An operation described as **all**, **visible**, **clear**, or **fill** acts on the identities visible after every active filter, not only selected rows and not the hidden backing collection.
- Sorting changes presentation order, not filtered membership or command scope.
- Bulk commands freeze the visible stable identities before creating one immutable Project edit.
- Fill Empty affects only visible NPC Morph Assignments that are empty at command capture and chooses independently from the selected Slider Preset subset.
- Selection continuity uses case-insensitive Slider Preset or Custom Morph Target name, plugin-plus-editor-ID NPC Morph Assignment identity, and case-insensitive assigned Slider Preset name.
- Custom Morph Target and NPC Morph Assignment selection remain mutually exclusive. Rename or deletion may naturally move or clear selection.
- Project content changes invalidate stale generated output. Save-only metadata changes, unchanged outcomes, rejections, and failures do not.

New and Open retain the session NPC Database, active filters, and sort choices. Generated output is cleared and must be regenerated from the new Project snapshot. Selections are restored only when the same logical identity exists and remains visible.

Exact right-click filter widgets, checklist behavior, case-sensitive search, search-to-selection side effects, column-width changes, and type-ahead timing are not compatibility requirements. Their accessible destination behavior belongs to issues #71 through #73.

## Approved corrections

### Artifact safety

INI and batch BoS export become transactional per user command:

1. Produce and validate every final byte payload from one immutable snapshot.
2. Preflight every destination and case-insensitive collision.
3. Stage every artifact adjacent to its destination.
4. Replace the complete target set only after all staging succeeds.
5. Preserve all pre-command destination bytes if validation, staging, or commit fails.

The application does not delete unrelated or stale JSON files from a selected directory.

A Windows-safe BoS filename mapping leaves already-safe Slider Preset names unchanged. It reversibly percent-encodes unsafe UTF-8 bytes, including literal percent, escapes trailing dots and spaces and reserved Windows basenames, and uses a stable hash suffix when a component must be shortened. The application reports every Slider Preset-to-filename mapping. A case-insensitive mapped collision rejects the entire export before writing. Single-file `.json` extension checks are case-insensitive.

### Output validity

Generation and export reject the entire operation with complete diagnostics when:

- distinct NPC Morph Assignments collapse to the same plugin-plus-normalized-Form-ID output identity;
- a name or condition contains a line break or an unescapable delimiter for its target format; or
- another conflict would produce duplicate, malformed, ambiguous, or silently lossy output.

The Project remains unchanged. Values are never silently renamed, discarded, or partially emitted.

### Verified UI defects

The following observed behaviors are defects to correct and cover with regression tests at the appropriate presentation or UI seam:

- a stale single-Slider-Preset preview after selection clears;
- All-Min and All-Max modes being active simultaneously;
- filter headers resizing the wrong column;
- selection restoration targeting an item hidden by an active filter;
- repeated-character type-ahead continuing beyond its timeout;
- the missing dot in `.jpeg` image lookup;
- popup placement assuming the primary monitor when the owner is elsewhere; and
- a modeless warning entering a nested `showAndWait` loop.

### Presentation policies delegated with constraints

Issues #71 through #73 choose the exact implementation for these behaviors while preserving accessible final results:

- coalescing high-frequency slider edits while keeping truthful live preview;
- disabling unavailable actions instead of accepting silent no-ops;
- conventional and accessible Page Up, Page Down, Home, and End behavior; and
- responsive image-preview sizing.

The existing assignment-count warning thresholds remain provisional: normal below 31, warning at 31 through 76, and critical at 77 or more. They require text or icon cues in addition to color. A later implementation may change or remove them only after verifying the downstream constraint suggested by the legacy source comment.

## Deliberate non-contracts and delegated decisions

The following are not compatibility requirements:

- FXML hierarchy, exact window geometry, the fixed 900-by-600 layout, tab versus navigation-rail structure, popup count, control placement, exact labels, and current icons.
- Hardcoded dark CSS, private Modena resources, vendored ControlsFX filter internals, and current table-header filter decoration.
- Exact accelerators, mnemonic behavior, 750-millisecond type-ahead implementation, focus traversal accidents, caret position, scroll hacks, and selection-reset quirks.
- Modal versus transient notification delivery and exact wording, provided severity, persistence, and recovery action remain clear.
- Raw threads, whole-pane disabling, wait cursors, absent progress/cancel UI, and close suppression. Issue #69 defines the replacement job and shutdown contract.
- Working-directory configuration paths, recent-folder storage, and installer-era user-data placement. Issue #75 defines delivery and migration locations.
- Project JSON byte representation.

The prototype in issue #67 did not record a final maintainer choice among its variants. This boundary therefore preserves workbench capabilities without claiming a selected navigation, inspector, output, progress, notification, or narrow-window arrangement. Issues #71 through #73 must settle those destination details.

## Verification boundary

Compatibility tests exercise public or intentional package seams, never private controller methods or the vendored filter implementation.

| Seam | Required evidence |
| --- | --- |
| `ProjectSession` and immutable `ProjectSnapshot` | Legacy Project open, semantic save/reopen, lifecycle transitions, recovery, validation, atomic failure, imports, edits, cascades, no-op behavior, and stable identities. |
| `ProjectOutputFormatter.generate` and `ProjectGeneratedOutput` | Golden Windows bytes for Templates, Morphs, and BoS output; ordering, math, omission, empty targets, collisions, and immutable results. |
| `ProjectPresentation.render` | One coherent snapshot per outcome, truthful dirty/title state, generated-output invalidation, and diagnostics. |
| Presentation-owned selection/filter reducer | Stable-identity selection, active-filter visibility, AND composition, frozen visible-set commands, sort independence, and Project-switch behavior without JavaFX controls. |
| Input and artifact adapters | Transactional NPC parsing, charset fallback, settings migration, safe filename mapping, collision preflight, all-or-nothing artifact replacement, and failure diagnostics. |
| Packaged JavaFX UI Automation | Complete workflows, keyboard access, focus, dialogs and notifications, filters, high contrast, DPI and multi-monitor behavior, cancellation, shutdown, and installer-launched paths. |

TDD proceeds in vertical slices at these agreed seams: one failing behavior test, the minimum implementation, then the next behavior. Exact output expectations come from accepted golden artifacts rather than recomputing the production algorithm in tests. Random-assignment tests assert eligibility and scope, not an exact random sequence.

The current `mvn verify` gate covers the `data`, `project`, and `presentation` source areas only. It does not compile or exercise the complete JavaFX application. A green current build is therefore evidence for the existing core seams, not proof of full UI feature parity; issue #74 must close that verification gap before release.

## Completion criteria for modernization

The modernized application satisfies this boundary only when:

1. Every strict compatibility claim has automated fixture or golden coverage.
2. Every current workflow is reachable through the approved workbench and produces the same logical Project or artifact result.
3. Every approved correction has a regression demonstrating both the legacy trigger and the replacement outcome.
4. Deferred interaction, job, accessibility, component, delivery, and verification decisions are resolved by their numbered tickets.
5. The full Java 25/JavaFX 25 application and packaged Windows artifact pass the gates defined by issue #74.
