# Cut Output workflows over to the Workbench

Status: resolved
Source: [GitHub #90](https://github.com/evildarkarchon/BS2BG/issues/90)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:07:02Z
Updated: 2026-08-29T07:14:29Z
Closed: 2026-08-29T07:14:29Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 09
Resolution: superseded; see imported comments for replacement tickets

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Deliver generation, inspection, copying, and export through the Workbench Output drawer with exact bytes, captured inputs, truthful cancellation, freshness handling, and transactional artifact publication.

## Acceptance criteria

- [ ] Templates, Morphs, and BoS Output tabs render accepted generated artifacts from one captured immutable Project snapshot and generation-settings basis.
- [ ] A fresh Generate result may reveal Output without stealing focus; stale, cancelled, failed, or superseded work never replaces output, reopens a surface, or changes navigation.
- [ ] Project content changes invalidate generated output, while save-only, unchanged, rejected, and failed outcomes do not.
- [ ] Copy and export preserve exact accepted bytes; batch export preflights and stages the complete target set, rejects unsafe or colliding destinations, and replaces transactionally.
- [ ] Generate and export use the central job path with truthful phases, cancellation, stale-result rejection, Activity evidence, retry, and safe shutdown behavior.
- [ ] Changed Output surfaces meet the accepted keyboard, accessibility, focus, High Contrast, DPI, responsive, read-only text navigation, and tab semantics.
- [ ] Retire Commons IO from the owning output/file adapters and delete all replaced legacy generation, preview, copy, BoS, and export routes.
- [ ] All inherited evidence remains green and the packaged launcher exercises complete Output workflows, exact-byte goldens, cancellation, staleness, collisions, and atomic failure.
- [ ] Packaged exit evidence is retained, and the issue is a whole-checkpoint revert boundary.

## Blocked by

- [#89](09-cut-templates-workflows-over-to-the-workbench.md)

## Comments

### evildarkarchon — 2026-08-29T07:14:28Z

Source: https://github.com/evildarkarchon/BS2BG/issues/90#issuecomment-5461003720
Updated: 2026-08-29T07:14:28Z

Superseded by the approved Output slices [#105](21-generate-and-inspect-captured-project-output.md) and [#106](22-copy-and-transactionally-export-output-artifacts.md).
