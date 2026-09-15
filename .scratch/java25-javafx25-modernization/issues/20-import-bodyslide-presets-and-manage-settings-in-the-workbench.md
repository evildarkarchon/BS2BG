# Import BodySlide presets and manage Settings in the Workbench

Status: resolved
Source: [GitHub #104](https://github.com/evildarkarchon/BS2BG/issues/104)
GitHub state at migration: closed (completed)
Created: 2026-08-29T07:12:40Z
Updated: 2026-09-01T04:02:24Z
Closed: 2026-09-01T04:02:24Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 19

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Import BodySlide presets and manage Settings in the Workbench.

## Acceptance criteria

- [ ] Standard and UUNP Settings are viewable and editable through the Workbench with accepted persistence, diagnostics, and output-affecting behavior.
- [ ] BodySlide import uses the central job path with captured sources, truthful progress, cancellation safe points, partial-source outcomes, and Activity evidence.
- [ ] Import results update the authoritative Project flow without losing focus, selection identity, or prior committed content on failure.
- [ ] Every replaced Templates, Set Sliders, Settings, rename, and related popup/controller route is removed; no second writable Templates route remains.
- [ ] Packaged tests exercise Settings persistence and recovery plus successful, malformed, partial, failed, and cancelled imports.

## Blocked by

- [#103](19-edit-slider-preset-choices-in-templates.md)

## Comments

No comments at migration.
