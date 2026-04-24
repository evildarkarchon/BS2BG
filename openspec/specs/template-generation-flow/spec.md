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

