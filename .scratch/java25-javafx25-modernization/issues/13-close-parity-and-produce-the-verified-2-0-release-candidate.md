# Close parity and produce the verified 2.0 release candidate

Status: ready-for-agent
Source: [GitHub #93](https://github.com/evildarkarchon/BS2BG/issues/93)
GitHub state at migration: open (no closure reason)
Created: 2026-08-28T12:07:09Z
Updated: 2026-08-29T07:14:34Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 28

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Close every remaining compatibility and feature-parity gap, prove the exact supported Windows release bytes, and publish the stable BS2BG 2.0 portable app-image ZIP.

## Acceptance criteria

- [ ] No legacy route, unused legacy FXML, temporary migration adapter, private JDK/JavaFX dependency, retired library, source exclusion, or dormant fallback remains.
- [ ] Every accepted Project lifecycle, Slider Preset, Custom Morph Target, NPC Morph Assignment, NPC Database, Settings, generation, copy, export, keyboard, focus, filtering, and recovery workflow has retained deterministic and packaged evidence.
- [ ] Finalize and verify the non-modular runtime module/service closure, pinned build inputs, dependency graph, application version, license, notice, corresponding-source, and bundled-runtime manifests.
- [ ] The exact candidate bytes pass the complete UI, accessibility, keyboard, theme, High Contrast, reduced-motion, 100/125/150% DPI, responsive, multi-monitor, cancellation, shutdown, compatibility, and packaging matrix.
- [ ] The exact candidate bytes pass release-blocking Windows 10 22H2 x64 and serviced Windows 11 x64 clean-machine, first-run, legacy-data, upgrade, profile-backup, rollback, checksum, and attestation checks.
- [ ] Publish immutable GitHub release assets using the authoritative 2.0 SemVer: the exact tested unsigned app-image ZIP, SHA256SUMS, attestation, notices/source manifest, and release notes.
- [ ] No untested rebuild or substituted byte may be published, and the preceding verified app-image remains the rollback checkpoint.
- [ ] Close this issue only after all inherited evidence is green and the stable release assets have been read back and verified.

## Blocked by

- [#112](28-remove-migration-scaffolding-and-prove-complete-workbench-parity.md)

## Comments

No comments at migration.
