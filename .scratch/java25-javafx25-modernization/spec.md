# Implement the Java 25 and JavaFX 25 modernization

Status: open
Source: [GitHub #80](https://github.com/evildarkarchon/BS2BG/issues/80)
GitHub state at migration: open (no closure reason)
Created: 2026-08-28T12:06:41Z
Updated: 2026-08-28T12:06:41Z
Labels: none
Assignees: none
Blocked by: none

## Source

- Planning map: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted implementation order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## Goal

Track the ordered, independently revertible implementation checkpoints that modernize BS2BG as a self-contained Windows desktop application on Java 25 LTS and JavaFX 25 LTS. Completion preserves Project compatibility, generated-output semantics, feature coverage, and the accepted ProjectSession and immutable Project boundaries while delivering the Fluent-inspired Workbench and verified portable 2.0 release.

## Shared constraints

- The child issues are implemented in their native blocking order; each checkpoint retains all earlier evidence.
- There is always one application entrypoint, one authoritative Project flow, and no capability with two writable production routes.
- ProjectSession remains synchronous, thread-safe, and JavaFX-independent; immutable snapshots and the package-private immutable Project aggregate remain the integrity seams.
- Every child finishes with a full-source Java 25 build, targeted deterministic tests, and a Windows x64 app-image exercised through its packaged launcher.
- Changed UI surfaces meet the accepted accessibility, keyboard, High Contrast, DPI, and responsive-layout criteria.
- Replaced routes and dependencies are deleted in their owning slice; rollback restores the preceding verified source and app-image checkpoint.
- Preview publication uses the isolated BS2BG Preview profile. Stable publication targets an unsigned portable app-image ZIP for Windows 10 22H2 x64 and serviced Windows 11 x64.

## Completion

This meta-issue is complete when every native sub-issue is closed and the exact verified 2.0 app-image ZIP bytes have been published.

## Local tracking

Use the [ticket index](README.md) for the GitHub-to-local mapping and dependency order. This spec remains open until all required checkpoints are resolved and the verified 2.0 release is published. The imported completion wording refers to the original GitHub workflow; progress is now recorded in these local files.

## Comments

No comments at migration.
