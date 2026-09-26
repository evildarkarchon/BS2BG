# Prove the packaged codec cutover and remove minimal-json

Status: resolved
Source: [GitHub #87](https://github.com/evildarkarchon/BS2BG/issues/87)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:06:55Z
Updated: 2026-08-29T09:58:09Z
Closed: 2026-08-29T09:58:09Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 06

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Finish the single-codec transition by proving all JSON workflows in a clean packaged application and removing the legacy codec and every temporary coexistence mechanism.

## Acceptance criteria

- [ ] The complete Project, Settings, and BoS corpus passes using only the owned Jackson-backed adapters.
- [ ] Clean-checkout, clean-app-image, dependency-convergence, runtime-closure, and packaged workflow gates prove the selected codec is present and functional.
- [ ] Remove temporary comparison adapters, routing switches, obsolete codec tests, and the `minimal-json` dependency and imports.
- [ ] No release or preview artifact contains two production JSON codecs or a dormant fallback implementation.
- [ ] Dependency, runtime, license, and corresponding-source manifests reflect the completed codec cutover.
- [ ] All inherited evidence remains green and packaged exit evidence covers Project open/save, Settings, and BoS workflows.
- [ ] The issue is independently revertible to the last verified dual-development-oracle checkpoint.

## Blocked by

- [#86](06-cut-project-reading-over-to-the-owned-json-adapter.md)

## Comments

No comments at migration.
