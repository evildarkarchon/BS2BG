## Why

Users already have `.jbs2bg` projects created by v1, so the port must preserve project files before higher-level workflows can be trusted. M1 makes load/save compatibility explicit and protects modders from silent data loss during the rewrite.

## What Changes

- Introduce the Core project model for slider presets, set sliders, custom morph targets, NPC assignments, and project state.
- Implement `.jbs2bg` load/save through `ProjectFileService` with Java-compatible JSON shape and ordering.
- Preserve v1 `isUUNP` semantics while adding the optional `Profile` field for named body/game profiles.
- Drop unresolved preset references on load to match Java behavior.
- Add round-trip tests for v1-saved fixtures and unchanged re-save output.

## Capabilities

### New Capabilities
- `project-roundtrip`: Defines the Core data model and `.jbs2bg` load/save compatibility contract.

### Modified Capabilities

## Impact

- Adds model and serialization services under `BS2BG.Core`.
- Adds fixture-driven round-trip tests in `BS2BG.Tests`.
- Establishes the compatibility rules future import, assignment, and export features rely on.
