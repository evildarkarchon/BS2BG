# Author Custom Morph Targets in Morphs

Status: resolved
Source: [GitHub #107](https://github.com/evildarkarchon/BS2BG/issues/107)
GitHub state at migration: open (no closure reason)
Created: 2026-08-29T07:12:50Z
Updated: 2026-08-29T07:12:50Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 22

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Author Custom Morph Targets in Morphs.

## Acceptance criteria

- [x] The Morphs Area renders Custom Morph Targets from immutable frames and dispatches typed intents through the authoritative Project flow.
- [x] Users can create, edit, remove, clear, filter, sort, select, validate, and report Custom Morph Targets.
- [x] Slider Preset relationship editing preserves Project referential integrity and accepted BodyGen condition semantics.
- [x] Selection remains identity-stable through filtering, sorting, edits, removal, and Project refreshes without silent retargeting.
- [x] Packaged keyboard and pointer tests verify authoring, relationship edits, validation, output continuity, accessibility, themes, DPI, and narrow mode.

## Blocked by

- [#106](22-copy-and-transactionally-export-output-artifacts.md)

## Comments

No comments at migration.

### Implementation audit — 2026-09-19

The authoring implementation was already present in `4bbdfb6` through `25e56e1`. Editing means Slider Preset
relationship editing, preserving the accepted BodyGen condition identity; this ticket does not add target renaming.

Follow-up `f23499d` routes rejected and failed confirmed Remove/Clear responses through normal Morphs reporting,
adds red/green controller regressions, and strengthens packaged initial-assignment and individual-assignment checks.
UI Automation now reacquires windows by native handle and refreshes pointer fallback coordinates on every retry.
Standards and Spec reviews found no remaining feature mismatch.

Verification so far: the clean Java 25 gate passes 456 tests without skips, and the PowerShell tooling suite passes
99 tests. Packaged verification is still in progress: the first run exposed stale window-title lookup; after its
fix, reopening reached a native file chooser whose File name control was exposed as a Pane without ValuePattern.
The final packaged criterion remains unchecked until a complete successful run is retained.

### Completion — 2026-09-19

Resolved after the clean checkpoint from `544953ae105f81c8f018f3b3b54c47c1f2271a5e`: 456 Java tests,
104 PowerShell tests, and all 20 packaged workflows passed without skips; the launcher exited with code 0.
[Retained evidence](../../../docs/build/evidence/windows-app-image-2026-09-19-morphs-100-percent/README.md)
includes exact output/export checks, keyboard and real-pointer authoring, native DPI measurements, UIA trees,
and visually inspected application-only screenshots.

The final pass also corrects inherited automation assumptions: native file/address controls exposed as Panes,
empty read-only text documents, BoS artifact identity, exclusive-lock readback, and import fixtures exceeding the
current 8 MiB parser limit. Production parser limits remain unchanged. At the user's request, all system High
Contrast/motion tests run after authoring and file workflows, before shutdown. Screenshot capture and pointer
input wait for Windows transition covers to clear.

Standards and Spec reviews have no remaining findings. This checkpoint verifies 100% display scale; 125% and
150% execution remains part of the wider Workbench scale matrix rather than being claimed by this run.
