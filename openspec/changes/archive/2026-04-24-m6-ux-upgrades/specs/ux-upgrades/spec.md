## ADDED Requirements

### Requirement: Global search filters active work surfaces
The system SHALL focus global search with `Ctrl+F` and filter the currently visible presets, targets, or NPC rows by supported text fields.

#### Scenario: Search NPC editor ID
- **WHEN** the Morphs workspace is active and the user searches an NPC editor ID
- **THEN** the NPC table shows matching rows and hides non-matching rows

### Requirement: Drag and drop imports supported files
The system SHALL import dropped XML preset files, open dropped `.jbs2bg` files, and import dropped NPC text files on the relevant surfaces.

#### Scenario: Drop XML files on preset list
- **WHEN** one or more `.xml` files are dropped on the preset list
- **THEN** the app imports them through the same parser and validation path as the file picker

### Requirement: Command palette exposes app actions
The system SHALL show every menu command and context action in a command palette opened with `Ctrl+Shift+P`.

#### Scenario: Run command from palette
- **WHEN** the user selects Generate Templates from the command palette
- **THEN** the same command executes as if invoked from the normal UI

### Requirement: Multiselect assignment is supported
The system SHALL allow multiple NPCs to be selected and assigned or cleared together without changing single-selection behavior for existing flows.

#### Scenario: Assign preset to selected NPCs
- **WHEN** multiple NPCs are selected and a preset assignment is applied
- **THEN** each selected NPC receives that preset exactly once

### Requirement: Undo and redo cover user mutations
The system SHALL provide ViewModel-level undo and redo for add, remove, rename, and assignment mutations.

#### Scenario: Undo preset rename
- **WHEN** a user renames a preset and invokes Undo
- **THEN** the original preset name and affected references are restored

### Requirement: Column checklist filter extends basic filtering
The system SHALL replace the M3 search-only NPC filter with per-column checklist filtering and search within each column menu.

#### Scenario: Filter by race checklist
- **WHEN** the user selects one race value in the Race column filter
- **THEN** only NPC rows with that race remain visible

### Requirement: Theme and validation upgrades are user-facing
The system SHALL support dark, light, and system themes, inline custom-target validation, and actionable preset-count warnings.

#### Scenario: Preset count exceeds warning threshold
- **WHEN** the selected target reaches the PRD warning threshold
- **THEN** the app displays the warning state and offers the trim command
