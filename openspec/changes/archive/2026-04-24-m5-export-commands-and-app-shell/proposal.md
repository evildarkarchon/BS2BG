## Why

Once core workflows are present, the app needs the complete command shell users expect from v1: project lifecycle, exports, keybinds, prompts, and Help/About parity. M5 closes the feature-by-feature checklist before optional UX upgrades.

## What Changes

- Implement File menu commands for New, Open, Save, Save As, Export BoS JSON, and Export BodyGen INIs.
- Add unsaved-change detection and confirmation prompts around destructive project lifecycle actions.
- Bind required key shortcuts and route command availability through ViewModels.
- Implement BodyGen INI and BoS JSON export to chosen folders with required line-ending and filename behavior.
- Add About dialog credits and complete the remaining PRD parity checklist.

## Capabilities

### New Capabilities
- `export-commands-app-shell`: Defines app-level commands, prompts, keybinds, exports, and final v1 parity closure.

### Modified Capabilities

## Impact

- Adds app shell command wiring and storage-provider integrations.
- Adds export services and integration tests around generated files.
- Adds UI tests for prompts, keybinds, About, and export paths.
