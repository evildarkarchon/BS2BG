## ADDED Requirements

### Requirement: Project model preserves v1 data
The system SHALL model slider presets, set sliders, custom morph targets, NPC assignments, profile selection, and dirty state without requiring UI dependencies.

#### Scenario: Load v1 project data into Core
- **WHEN** a v1 `.jbs2bg` file is loaded
- **THEN** all supported presets, set sliders, custom targets, NPC fields, and assignment lists are represented in Core models

### Requirement: Project files round-trip compatibly
The system SHALL load and save `.jbs2bg` JSON using the v1 root objects, property names, null handling, omitted missing-default sliders, and deterministic ordering.

#### Scenario: Save loaded fixture without changes
- **WHEN** a v1-saved fixture project is loaded and saved immediately
- **THEN** the saved JSON matches the expected compatible output for that fixture

### Requirement: Profile field is backward compatible
The system SHALL keep `isUUNP` behavior while accepting and emitting the optional `Profile` field for named profiles.

#### Scenario: Load project without Profile
- **WHEN** a preset has no `Profile` property and `isUUNP` is true
- **THEN** the preset uses the Skyrim UUNP profile and saving emits both `isUUNP` and `Profile`

### Requirement: Missing preset references are dropped
The system SHALL silently discard target and NPC assignment references that do not match a loaded slider preset.

#### Scenario: Load stale assignment
- **WHEN** a project file references a preset name absent from `SliderPresets`
- **THEN** the assignment is omitted without failing the load
