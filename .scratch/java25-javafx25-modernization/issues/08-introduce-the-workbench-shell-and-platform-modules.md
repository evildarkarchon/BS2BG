# Introduce the Workbench shell and platform modules

Status: resolved
Source: [GitHub #88](https://github.com/evildarkarchon/BS2BG/issues/88)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:06:58Z
Updated: 2026-08-29T07:14:26Z
Closed: 2026-08-29T07:14:26Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 07
Resolution: superseded; see imported comments for replacement tickets

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Make the Fluent-inspired Workbench the sole application shell, with one Project flow and the shared navigation, jobs, feedback, theming, accessibility, and platform behavior needed for subsequent feature cutovers.

## Acceptance criteria

- [ ] The Workbench is the sole entrypoint and owns one authoritative Project flow over one ProjectSession; placeholder Templates, Morphs, NPC Database, Output, and Settings Areas render coherent immutable frames.
- [ ] A JavaFX-independent JobCoordinator provides one application-wide job, truthful progress, cancellation races and safe commit phases, freshness evidence, retry linkage, observer isolation, and coordinated shutdown.
- [ ] The kernel owns typed navigation and semantic focus return, durable Activity and InfoBars, status projection, typed dialogs, tokenized platform effects, Output drawer geometry, and late-callback rejection.
- [ ] Standard JavaFX controls run over application-owned Fluent tokens for System, Light, Dark, and live High Contrast/reduced-motion behavior using public APIs only.
- [ ] The semantic icon gate selects either verified Ikonli or the predetermined bundled-vector adapter; only the selected implementation ships.
- [ ] Temporary adapters may reach unfinished legacy workflows, but there is one writable route per capability and feature modules neither bind to legacy observable state nor call legacy controllers.
- [ ] Delete the replaced legacy root shell, notification, confirmation, and equivalent platform routes in this slice.
- [ ] Publish and exercise an isolated-profile BS2BG Preview app-image covering navigation, focus, jobs, theme, High Contrast, cancellation, shutdown, and responsive shell behavior.
- [ ] All inherited evidence remains green, packaged exit evidence is retained, and the issue is a whole-checkpoint revert boundary.

## Blocked by

- [#87](07-prove-the-packaged-codec-cutover-and-remove-minimal-json.md)

## Comments

### evildarkarchon — 2026-08-29T07:14:25Z

Source: https://github.com/evildarkarchon/BS2BG/issues/88#issuecomment-5461003459
Updated: 2026-08-29T07:14:25Z

Superseded by the approved context-sized Workbench foundation slices [#98](14-launch-the-workbench-with-complete-project-lifecycle.md), [#99](15-establish-workbench-navigation-focus-and-responsive-layout.md), [#100](16-deliver-workbench-themes-and-accessible-platform-behavior.md), and [#101](17-run-application-work-through-the-centralized-job-coordinator.md).
