# template-generation-flow Specification

## Purpose
TBD - created by archiving change m2-template-generation-flow. Update Purpose after archive.
## Requirements
### Requirement: BodySlide XML import reads Java-compatible data
The system SHALL import one or more BodySlide `SliderPresets` XML files by reading each `Preset` name and its `SetSlider` children while ignoring unknown attributes and children.

#### Scenario: Import sparse preset XML
- **WHEN** XML contains presets with only `big` or only `small` slider values
- **THEN** the imported preset keeps those sparse values and defers missing-default resolution to render time

### Requirement: Preset list supports v1 management actions
The system SHALL provide preset selection, rename, duplicate, remove, clear, profile selection, and duplicate-name validation for imported presets.

#### Scenario: Duplicate preset name conflict
- **WHEN** a user duplicates or renames a preset to a name already present
- **THEN** the action is rejected and the preset collection remains unchanged

### Requirement: Template preview updates live
The system SHALL update the selected preset preview whenever selection, profile, set slider values, or omit-redundant state changes.

#### Scenario: Toggle omit redundant sliders
- **WHEN** `Omit Redundant Sliders` changes
- **THEN** the selected preset preview is recalculated and generated template text is cleared

### Requirement: Templates can be generated and copied
The system SHALL generate one template line per preset using Java-compatible ordering and formatting and SHALL support copying generated text to the clipboard.

#### Scenario: Generate Flow A output
- **WHEN** presets have been imported and the user runs Generate Templates
- **THEN** the generated text contains one correctly formatted line for each preset

### Requirement: Runtime profile catalog supports project-scoped overlays
The system SHALL calculate previews and generated templates against the current runtime profile catalog, including explicit project-scoped embedded profile overlays.

#### Scenario: Use project copy for missing profile
- **WHEN** an embedded project profile is explicitly activated for a missing reference
- **THEN** template preview and generation use the project-scoped profile definition
- **AND** the embedded definition is not written to the local profile store by that action

### Requirement: Missing profile fallback remains visible
The system SHALL keep unresolved saved profile names on presets until the user explicitly remaps or adopts an installed profile.

#### Scenario: Generate with unresolved custom profile
- **WHEN** a selected preset references a profile absent from the current catalog
- **THEN** preview and generation continue with visible fallback calculation rules
- **AND** the saved preset `ProfileName` remains the unresolved value
- **AND** `Keep Unresolved for Now` does not hide the fallback information

### Requirement: Recovery remap updates template state transactionally
The system SHALL update template preview, missing-default rows, profile selector state, generated text, and undo state when profile recovery remaps presets to an installed profile.

#### Scenario: Undo recovery remap
- **WHEN** a recovery remap changes affected preset profile references
- **AND** the user runs undo
- **THEN** the previous unresolved profile names are restored
- **AND** visible fallback information and recovery diagnostics can reappear

