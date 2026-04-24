## Why

BodyGen output is only useful once users can assign presets to NPCs and custom targets. M3 ports the Morphs workflow with enough filtering and assignment support for the v1 Flow B and Flow C use cases.

## What Changes

- Implement custom morph target creation, removal, clearing, and preset assignment.
- Implement NPC text import with Java-compatible parsing, de-duplication, assignment, and display fields.
- Add the Morphs workspace with NPC table, custom target list, selected-target preset panel, count badge, and search-only basic filter.
- Generate `morphs.ini` lines for NPC and custom target assignments.
- Add Fill Empty, Clear Assignments, Add/Add All/Remove/Clear assignment flows.

## Capabilities

### New Capabilities
- `morph-assignment-flow`: Defines custom targets, NPC import/table behavior, assignment flows, basic filtering, and morph generation.

### Modified Capabilities

## Impact

- Adds Core morph target, NPC import, assignment, and morph writer behavior.
- Adds Morphs ViewModels and UI surfaces to the app shell.
- Adds tests for import parsing, de-dupe keys, filtering scope, and generated morph lines.
