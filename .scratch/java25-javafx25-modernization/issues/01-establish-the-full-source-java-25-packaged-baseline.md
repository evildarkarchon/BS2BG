# Establish the full-source Java 25 packaged baseline

Status: resolved
Source: [GitHub #81](https://github.com/evildarkarchon/BS2BG/issues/81)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:06:41Z
Updated: 2026-08-29T02:47:23Z
Closed: 2026-08-29T02:47:23Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: none

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Make the complete existing BS2BG application build and launch as a self-contained Windows app-image on the pinned Java 25 and JavaFX 25 toolchain, preserving the current feature baseline while removing build exclusions and unsupported JavaFX internals.

## Acceptance criteria

- [ ] Commit and use a pinned Maven Wrapper, Temurin 25 and JavaFX 25 inputs, pinned lifecycle plugins, and `--release 25`; preview and incubator APIs remain disabled.
- [ ] Compile every production source and resource, and verify representative FXML/controller linkage without source exclusions.
- [ ] Replace private JDK/JavaFX APIs, private Modena resources, and the vendored JavaFX 8 filter while preserving logical visible-set behavior.
- [ ] Keep the existing ProjectSession, immutable snapshot, and package-private Project contracts green under the Java 25 toolchain.
- [ ] Build a non-modular Windows x64 `jpackage` app-image and smoke-test launch and the existing primary workflows through its packaged launcher.
- [ ] Record the pinned toolchain and packaged exit evidence, and narrowly supersede only ADR-0001's Java 8 baseline clause.
- [ ] The issue is independently revertible to the preceding verified application state.

## Blocked by

- None (can start immediately).

## Comments

No comments at migration.
