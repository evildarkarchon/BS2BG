# profile-extensibility Specification

## Purpose
Define custom profile trust domains, validation-gated local profile management, project-embedded profile sharing, and explicit missing-profile recovery behavior for BS2BG without changing bundled slider math or legacy project compatibility.

## Requirements

### Requirement: Bundled profiles are read-only
The system SHALL treat bundled Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE profiles as protected application data that cannot be edited, deleted, replaced, or exported as user-authored custom profile JSON from the profile manager.

#### Scenario: Inspect bundled profile
- **WHEN** a bundled profile row is selected
- **THEN** the row is labeled `Bundled — read-only`
- **AND** edit, save, delete, and standalone export actions are disabled
- **AND** copy-to-custom remains available as the authoring path

### Requirement: Local custom profiles are validation-gated user data
The system SHALL store editable custom profiles under user-local profile storage and SHALL include them in the runtime catalog only after strict Core validation succeeds.

#### Scenario: Import valid custom profile
- **WHEN** the user imports profile JSON with a unique internal display name and valid `Defaults`, `Multipliers`, and `Inverted` tables
- **THEN** the profile is saved to the local user profile store
- **AND** the runtime catalog refresh includes the profile as `LocalCustom`

#### Scenario: Reject duplicate display names
- **WHEN** an imported or saved profile display name matches an existing bundled, local custom, or active project profile ignoring case
- **THEN** validation rejects the profile before catalog inclusion
- **AND** no existing profile file is overwritten by the rejected candidate

### Requirement: Projects embed referenced custom profiles only
The system SHALL persist an optional `CustomProfiles` section in `.jbs2bg` projects containing only non-bundled custom profile definitions referenced by project presets.

#### Scenario: Save project with referenced custom profile
- **WHEN** a project preset references a local or embedded custom profile
- **THEN** saving the project writes that profile definition under `CustomProfiles`
- **AND** unrelated local custom profiles are not included
- **AND** legacy preset `Profile` and `isUUNP` fields remain present for compatibility

### Requirement: Embedded/local profile conflicts are explicit
The system SHALL never silently choose between different embedded and local custom profiles with the same display name.

#### Scenario: Profile conflict found
- **WHEN** opening a project whose embedded profile differs from a local custom profile with the same display name
- **THEN** the app prompts with `Profile conflict found`
- **AND** the available decisions include `Use Project Copy`, `Replace Local Profile`, `Rename Project Copy`, and `Keep Local Profile`
- **AND** all decisions are collected before local profile writes or active project replacement occur

### Requirement: Missing profile recovery is neutral and explicit
The system SHALL keep projects open when custom profile references are missing and SHALL expose neutral recovery actions without mutating project data automatically.

#### Scenario: Missing custom profile actions
- **WHEN** a project references a custom profile absent from the active runtime catalog
- **THEN** Diagnostics and Profiles surfaces show visible fallback information
- **AND** explicit actions include `Import Matching Profile`, `Remap to Installed Profile`, `Use Project Copy` when an embedded copy exists, and `Keep Unresolved for Now`
- **AND** `Keep Unresolved for Now` does not suppress future fallback or recovery diagnostics

#### Scenario: Import matching profile uses internal identity
- **WHEN** the user chooses `Import Matching Profile`
- **THEN** the imported JSON resolves the missing reference only when its internal `Name` matches the missing profile display name exactly ignoring case
- **AND** filenames and paths are never used as identity
- **AND** mismatched internal names leave the reference unresolved with the exact-match note visible

#### Scenario: Remap to installed profile is undoable
- **WHEN** the user chooses `Remap to Installed Profile`
- **THEN** affected preset `ProfileName` values change to the selected installed catalog profile
- **AND** the project becomes dirty
- **AND** undo restores the previous unresolved names and recovery diagnostics reappear

### Requirement: Selected custom and embedded profiles can be exported
The system SHALL allow standalone profile JSON export only for selected local custom or project-embedded profile rows.

#### Scenario: Export selected custom profile JSON
- **WHEN** a local custom or embedded project profile is selected
- **THEN** `Export Profile JSON` is enabled
- **AND** the written JSON is produced by `ProfileDefinitionService.ExportProfileJson`
- **AND** success status is `Profile JSON exported.`

#### Scenario: Export unavailable for bundled and missing rows
- **WHEN** a bundled profile or missing fallback row is selected
- **THEN** `Export Profile JSON` is disabled
